package cogent

import scala.util.{Try,Success,Failure}
import scala.collection.immutable.IntMapEntryIterator

class MiddleEnd(val logger : Logger) :

    import net.sourceforge.plantuml.BlockUml
    import net.sourceforge.plantuml.core.Diagram
    import net.sourceforge.plantuml.statediagram.StateDiagram 
    import net.sourceforge.plantuml.cucadiagram.IEntity 
    import net.sourceforge.plantuml.cucadiagram.IGroup
    import net.sourceforge.plantuml.cucadiagram.ILeaf
    import net.sourceforge.plantuml.cucadiagram.GroupType
    import net.sourceforge.plantuml.cucadiagram.LeafType
    import net.sourceforge.plantuml.cucadiagram.Link
    import net.sourceforge.plantuml.cucadiagram.Display
    import scala.jdk.CollectionConverters._
    import scala.collection.mutable

    import Logger.Level._


    def processBlocks( blockList : Iterable[BlockUml] ) : Unit =
        for block <- blockList do
            processBlock(block)

    def processBlock( block : BlockUml ) : Unit =
        val diagram = block.getDiagram()

        diagram match
        case ( stateDiagram : StateDiagram ) => 
            constructStateChart( stateDiagram )
        case _ => reportWarning( "Diagram is not a state diagram" )

    def constructStateChart( stateDiagram : StateDiagram ) : StateChart =
        // First: recursively walk the tree of PlantUML entities (states and state-like things) in
        // `stateDiagram`.  For each entity we create a corresponding `cogent.Node`.  `entityToNodeMutMap`
        // records a mapping from IEntity objects to `Node` objects.
        val rootGroup : IGroup = stateDiagram.getRootGroup()
        val junk : mutable.Map[IEntity,Node] = new mutable.HashMap[IEntity,Node]()
        val entityToNodeMutMap = mutable.HashMap[IEntity, Node]()

        extractState( 0,  0, "", rootGroup, junk, entityToNodeMutMap )

        // Second, we find a Higraph node that represents the root state.
        // This will always be an OR State.  This is a tree that includes all
        // states in it.  We also create a mapping from PlantUML entities (IEntity)
        // to cogent.Node objects.
        val rootState : Node = entityToNodeMutMap.apply( rootGroup )
        logger.log( Debug, s"The root of the tree is\n${rootState.show}" )
        assert( rootState.isInstanceOf[Node.OrState] )
        val entityToNodeMap = entityToNodeMutMap.toMap
        val nodeSet = entityToNodeMap.values.toSet
        
        // Third we compute a map from Nodes to their parents. The
        // root won't be mapped, so this is truly a partial function.
        val parentMutMap = new mutable.HashMap[Node,Node]
        computeParentMap( rootState, parentMutMap ) 
        val parentMap = parentMutMap.toMap

        // Fourth, we turn PlantUML edges into cogent.Edge objects.
        val links : Seq[Link] = stateDiagram.getLinks().asScala.toSeq
        val edgeMutSet = mutable.Set[Edge]()
        extractEdgesFromLinks( links, rootState, parentMap, entityToNodeMap, edgeMutSet )

        val edgeSet = edgeMutSet.toSet

        val stateChart = StateChart( rootState, nodeSet, edgeSet, parentMap )
        logger.log( Debug, stateChart.show )
        return stateChart
    end constructStateChart

    def extractState(   depth : Int,
                        index : Int,
                        parent_name : String,
                        group : IGroup,
                        sink : mutable.Map[IEntity,Node],
                        entityToNodeMap : mutable.Map[IEntity,Node]
    ) : Unit =
        val name = (if depth == 0 then "root" else group.getCode().getName() + "_" + parent_name)

        // logger.log( Debug, s"Processing group $name")
        val leafChildren = group.getLeafsDirect().asScala.toSeq
        val groupChildren = group.getChildren().asScala.toSeq
        val relevantLeafChildren = filterLeaves( leafChildren )
        val relevantGroupChildren = filterGroups( groupChildren )
        val concurrentStates = relevantGroupChildren
            .filter( (childGroup) =>
                        childGroup.getGroupType() == GroupType.CONCURRENT_STATE )
        // logger.log( Debug, s"""... It has ${relevantLeafChildren.size} leaves
        //             |... and ${relevantGroupChildren.size} groups 
        //             |... of which ${concurrentStates.size} are concurrent.""".stripMargin)

        // No children that are states or similar.
        if( relevantLeafChildren.size == 0 && relevantGroupChildren.size == 0 ) then
            reportError( s"State $name is a group but has no children that are states" )

        // AND states have at least on child and all its children are CONCURRENT_STATEs
        else if(   relevantLeafChildren.size == 0
                && concurrentStates.size == relevantGroupChildren.size  ) then
            // logger.log( Debug, s"State $name identified as an AND state" )
            makeANDState( depth, index, group, name, sink, relevantGroupChildren, entityToNodeMap )

        // OR states contain no CONCURRENT_STATEs but have at least one child.
        else if( concurrentStates.size == 0 ) then
            // logger.log( Debug, s"State $name identified as an OR state" )
            makeORState( depth, index, group, name, sink, relevantLeafChildren, relevantGroupChildren, entityToNodeMap )
        
        // Otherwise there are some children that are concurrent states and some that aren't.
        else 
            reportError( s"Node $name has ${concurrentStates.size} regions "
                    + s"... but it also has other children. This seems wrong.")
    end extractState

    def extractState(   depth : Int,
                        index : Int,
                        parent_name : String,
                        leaf : ILeaf,
                        sink : mutable.Map[IEntity,Node],
                        entityToNodeMap : mutable.Map[IEntity,Node]
    ) : Unit = 
        val name = leaf.getCode().getName() + "_" + parent_name
        val stateInfo = StateInformation( name, depth, index )
        val node : Node =
            leaf.getLeafType() match
                case LeafType.STATE => Node.BasicState( stateInfo )
                case LeafType.CIRCLE_START => Node.StartMarker( stateInfo )
                case LeafType.STATE_CHOICE => Node.BranchPseudoState( stateInfo )
                case _ => assert( false, "States should be filtered.")
        addState( leaf, node, sink, entityToNodeMap )
    end extractState

    def makeANDState( depth : Int,
                    index : Int,
                    group : IGroup,
                    name : String,
                    sink : mutable.Map[IEntity,Node],
                    relevantGroupChildren : Seq[IGroup],
                    entityToNodeMap : mutable.Map[IEntity,Node]
    ) : Unit =
        val childMap = mutable.HashMap[IEntity,Node]()
        var i = -1
        for g <- relevantGroupChildren do
            i += 1
            extractState( depth+1, i, name, g, childMap, entityToNodeMap )
        val children = childMap.values.toSeq
        val stateInfo = StateInformation( name, depth, index )
        val andState = Node.AndState( stateInfo, children )
        addState( group, andState, sink, entityToNodeMap)
    end makeANDState

    def makeORState( depth : Int,
                    index : Int,
                    group : IGroup,
                    name : String,
                    sink : mutable.Map[IEntity,Node],
                    relevantLeafChildren : Seq[ILeaf],
                    relevantGroupChildren : Seq[IGroup],
                    entityToNodeMap : mutable.Map[IEntity,Node]
    ) : Unit = 
        val childMap = mutable.HashMap[IEntity,Node]()
        var i = -1 
        for g <- relevantGroupChildren do
            i += 1
            extractState( depth+1, i, name, g, childMap, entityToNodeMap )
        for l <- relevantLeafChildren do 
            i += 1
            extractState( depth+1, i, name, l, childMap, entityToNodeMap )
        val children = childMap.values.toSeq
        val info =  StateInformation( name, depth, index )
        val orState = Node.OrState( info, children )
        addState(group, orState, sink, entityToNodeMap ) 
    end makeORState

    def addState(   entity : IEntity, 
                    node : Node,
                    sink : mutable.Map[IEntity, Node],
                    entityToNodeMap : mutable.Map[IEntity, Node]
    ) : Unit =
        assert( ! (sink contains entity) )
        sink.addOne( entity, node )
        assert( ! (entityToNodeMap contains entity) )
        entityToNodeMap.addOne( entity, node )
    end addState

    def reportError( message : String ) : Unit =
        logger.log( Fatal, message )
    

    def reportWarning( message : String ) : Unit =
        logger.log( Warning, message )

    def filterLeaves( leaves : Seq[ILeaf] ) : Seq[ILeaf] =
        leaves.filter( (leaf) =>
            leaf.getLeafType() match
            case LeafType.STATE =>
                true
            case LeafType.STATE_CHOICE =>
                true
            case LeafType.DEEP_HISTORY =>
                reportError( "History pseudo-states are not supported yet.")
                false 
            case LeafType.STATE_FORK_JOIN =>
                reportError( "Fork and join pseudo-states are not supported yet.")
                false
            case LeafType.STATE_CONCURRENT =>
                reportError( "Found a leaf marked STATE_CONCURRENT. Don't know what to do with that.")
                false
            case LeafType.CIRCLE_END =>
                reportError( "Final state are not supported yet.")
                false
            case LeafType.CIRCLE_START =>
                true
            case LeafType.NOTE =>
                false 
            case LeafType.DESCRIPTION =>
                false 
            case _ =>
                reportError( s"A leaf node of type ${leaf.getLeafType().toString()} was found and ignored.")
                false 
        )

    def filterGroups( groups : Seq[IGroup] ) : Seq[IGroup] =
        groups.filter( (group) =>
            group.getGroupType() match
            case GroupType.STATE => true
            case GroupType.CONCURRENT_STATE => true
            case _ =>
                reportError( s"A group node of type ${group.getGroupType().toString()} was found and ignored.")
                false 
        )

    def computeParentMap( node : Node, parentMap : mutable.Map[Node, Node] ) : Unit =
        node match
            case Node.BasicState( si ) =>
            case Node.BranchPseudoState( si ) => 
            case Node.StartMarker( si ) =>
            case Node.OrState( si, children ) =>
                for child <- children do
                    assert( !(parentMap contains child) )
                    parentMap.addOne( child, node )
                    computeParentMap( child, parentMap )
                    
            case Node.AndState( si, children ) =>
                for child <- children do
                    assert( !(parentMap contains child) )
                    parentMap.addOne( child, node )
                    computeParentMap( child, parentMap )
    end computeParentMap

    def display2String( display : Display ) : String = 
        if display == null then ""
        else if display.size() == 0 then ""
        else display.iterator().asScala.toSeq
             .map( charSeq => charSeq.toString + "\n")
             .foldRight("")( (a,b) => a+b )

    def extractEdgesFromLinks(  links : Seq[Link],
                                rootState : Node,
                                parentMap : Map[Node,Node],
                                entityToNodeMap : Map[IEntity, Node],
                                edgeSet : mutable.Set[Edge] ) : Unit =
        for link <- links do
            val source = link.getEntity1()
            val target = link.getEntity2()
            if !(entityToNodeMap contains source) || !(entityToNodeMap contains target) then
                reportWarning(s"Link from ${source.getCode().getName()} to ${source.getCode().getName()} ignored.")
            else 
                val sourceNode = entityToNodeMap.apply( source )
                val targetNode = entityToNodeMap.apply( target )
                val label = link.getLabel()
                val labelAsString = display2String( label ) 
                val result : parsers.ParseResult[(Option[Trigger], Option[Guard], Seq[Action] )] =  parsers.parseEdgeLabel(labelAsString)
                logger.log( Debug, s"Parser input <<${labelAsString}>>")
                logger.log( Debug, s"Parser result ${result}.")
                val edge = result match
                    case parsers.Success( (triggerOpt, guardOpt, actions), _ ) =>
                        Edge(sourceNode, targetNode, triggerOpt, guardOpt, actions ) 
                    case parsers.Failure( message, _) =>
                        reportError( message  )
                        Edge(sourceNode, targetNode, None, None, List.empty ) 
                    case parsers.Error( message, _) =>
                        reportError( message  )
                        Edge(sourceNode, targetNode, None, None, List.empty )  
                edgeSet.add( edge )
            end if
        end for
    end extractEdgesFromLinks
end MiddleEnd