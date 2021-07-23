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


    def processBlocks( blockList : Iterable[BlockUml] ) : List[StateChart] =
        val listOfOptions = for block <- blockList
            yield processBlock(block)
        listOfOptions.toList.flatten

    def processBlock( block : BlockUml ) : Option[StateChart] =
        val diagram = block.getDiagram()
        {
            given Logger = logger
            blockPrinter.printBlock( block )
        }

        diagram match
        case ( stateDiagram : StateDiagram ) => 
            constructStateChart( stateDiagram )
        case _ => reportWarning( "Diagram is not a state diagram" )
            None

    def constructStateChart( stateDiagram : StateDiagram ) : Option[StateChart] =
        // First: recursively walk the tree of PlantUML entities (states and state-like things) in
        // `stateDiagram`.  For each entity we create a corresponding `cogent.Node`.  `entityToNodeMutMap`
        // records a mapping from IEntity objects to `Node` objects.
        val rootGroup : IGroup = stateDiagram.getRootGroup()
        val junk : mutable.Map[IEntity,Node] = new mutable.HashMap[IEntity,Node]()
        val entityToNodeMutMap = mutable.HashMap[IEntity, Node]()

        extractState( 0, "", rootGroup, junk, entityToNodeMutMap )

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
        indexTheNodes( stateChart )
        setTheStartNodes( stateChart )
        logger.log( Debug, stateChart.show )
        return Some( stateChart )
    end constructStateChart

    def extractState(   depth : Int,
                        parent_name : String,
                        group : IGroup,
                        sink : mutable.Map[IEntity,Node],
                        entityToNodeMap : mutable.Map[IEntity,Node]
    ) : Unit =
        val name = group2Name( group, depth, parent_name )

        logger.log( Debug, s"Processing group $name")
        val leafChildren = group.getLeafsDirect().asScala.toSeq
        val groupChildren = group.getChildren().asScala.toSeq
        val relevantLeafChildren = filterLeaves( leafChildren )
        val relevantGroupChildren = filterGroups( groupChildren )
        val concurrentStates = relevantGroupChildren
            .filter( (childGroup) =>
                        childGroup.getGroupType() == GroupType.CONCURRENT_STATE )
        logger.log( Debug, s"""... It has ${relevantLeafChildren.size} leaves
                    |... and ${relevantGroupChildren.size} groups 
                    |... of which ${concurrentStates.size} are concurrent.""".stripMargin)

        // No children that are states or similar.
        if( relevantLeafChildren.size == 0 && relevantGroupChildren.size == 0 ) then
            reportError( s"State $name is a group but has no children that are states" )

        // AND states have at least on child and all its children are CONCURRENT_STATEs
        else if(   relevantLeafChildren.size == 0
                && concurrentStates.size == relevantGroupChildren.size  ) then
            logger.log( Debug, s"State $name identified as an AND state" )
            makeANDState( depth, group, name, sink, relevantGroupChildren, entityToNodeMap )

        // OR states contain no CONCURRENT_STATEs but have at least one child.
        else if( concurrentStates.size == 0 ) then
            logger.log( Debug, s"State $name identified as an OR state" )
            makeORState( depth, group, name, sink, relevantLeafChildren, relevantGroupChildren, entityToNodeMap )
        
        // Otherwise there are some children that are concurrent states and some that aren't.
        else 
            reportError( s"Node $name has ${concurrentStates.size} regions "
                    + s"... but it also has other children. This seems wrong.")
    end extractState

    def extractState(   depth : Int,
                        parent_name : String,
                        leaf : ILeaf,
                        sink : mutable.Map[IEntity,Node],
                        entityToNodeMap : mutable.Map[IEntity,Node]
    ) : Unit = 
        val name = leaf2Name( leaf, parent_name )
        val stateInfo = StateInformation( name, depth )
        val node : Node =
            leaf.getLeafType() match
                case LeafType.STATE =>
                    val stereotype = leaf.getStereotype()
                    if stereotype != null then 
                        if stereotype.toString.equals( "<<choice>>" ) then
                            logger.debug( s"Leaf node $name identified as a CHOICE pseudostate. ")
                            Node.ChoicePseudoState( stateInfo )
                        else
                            reportWarning( s"Leaf node $name has an unknown stereotype ${stereotype.toString}. It will be treated as a basic state.")
                            logger.debug( s"Leaf node $name identified as a BASIC state. ")
                            Node.BasicState( stateInfo )
                        end if
                    else
                        logger.log(Debug, s"Leaf node $name identified as a BASIC state. ")
                        Node.BasicState( stateInfo )
                    end if
                case LeafType.CIRCLE_START =>
                    logger.log(Debug, s"Leaf node $name identified as a START marker. ")
                    Node.StartMarker( stateInfo )
                case LeafType.STATE_CHOICE => 
                    logger.log(Debug, s"Leaf node $name identified as a CHOICE pseudostate. ")
                    Node.ChoicePseudoState( stateInfo )
                case _ => assert( false, "States should be filtered.")
        addState( leaf, node, sink, entityToNodeMap )
    end extractState

    def makeANDState( depth : Int,
                    group : IGroup,
                    name : String,
                    sink : mutable.Map[IEntity,Node],
                    relevantGroupChildren : Seq[IGroup],
                    entityToNodeMap : mutable.Map[IEntity,Node]
    ) : Unit =
        val childMap = mutable.HashMap[IEntity,Node]()
        for g <- relevantGroupChildren do
            extractState( depth+1, name, g, childMap, entityToNodeMap )
        val children = childMap.values.toSeq
        val stateInfo = StateInformation( name, depth )
        val andState = Node.AndState( stateInfo, children )
        addState( group, andState, sink, entityToNodeMap)
    end makeANDState

    def makeORState( depth : Int,
                    group : IGroup,
                    name : String,
                    sink : mutable.Map[IEntity,Node],
                    relevantLeafChildren : Seq[ILeaf],
                    relevantGroupChildren : Seq[IGroup],
                    entityToNodeMap : mutable.Map[IEntity,Node]
    ) : Unit = 
        val childMap = mutable.HashMap[IEntity,Node]()
        for g <- relevantGroupChildren do
            extractState( depth+1, name, g, childMap, entityToNodeMap )
        for l <- relevantLeafChildren do 
            extractState( depth+1, name, l, childMap, entityToNodeMap )
        val children = childMap.values.toSeq
        val info =  StateInformation( name, depth )
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
            val name = leaf.getCode().getName()
            leaf.getLeafType() match
            case LeafType.STATE =>
                true
            case LeafType.STATE_CHOICE =>
                true
            case LeafType.DEEP_HISTORY =>
                reportError( s"Found deep history $name. History pseudo-states are not supported yet.")
                false 
            case LeafType.STATE_FORK_JOIN =>
                reportError( s"Found deep fork or join $name. Fork and join pseudo-states are not supported yet.")
                false
            case LeafType.STATE_CONCURRENT =>
                reportError( "Found a leaf $name marked STATE_CONCURRENT. Don't know what to do with that.")
                false
            case LeafType.CIRCLE_END =>
                reportError( "Found final state $name. Final state are not supported yet.")
                false
            case LeafType.CIRCLE_START =>
                true
            case LeafType.NOTE =>
                reportWarning( s"Note $name ignored." )
                false 
            case LeafType.DESCRIPTION =>
                reportWarning( s"Description $name ignored." )
                false 
            case _ =>
                reportError( s"A leaf node $name of type ${leaf.getLeafType().toString()} was found and ignored.")
                false 
        )

    def filterGroups( groups : Seq[IGroup] ) : Seq[IGroup] =
        groups.filter( (group) =>
            val name = group.getCode().getName()
            group.getGroupType() match
            case GroupType.STATE => true
            case GroupType.CONCURRENT_STATE => true
            case _ =>
                reportWarning( s"A group node named $name of type ${group.getGroupType().toString()} was found and ignored.")
                false 
        )

    def computeParentMap( node : Node, parentMap : mutable.Map[Node, Node] ) : Unit =
        node match
            case Node.BasicState( si ) =>
            case Node.ChoicePseudoState( si ) => 
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

    def entity2Name( entity : IEntity ) : String =
        val code = entity.getCode()
        if code == null then "unknown"
        else code.getName()

    def shorten( str : String ) : String =
        if str.length() > 31 then str.substring( 0, 31 ) else str

    def group2Name( group : IGroup, depth : Int, parentName : String ) : String =
        val longName =
            if depth == 0 then "root"
            else entity2Name( group ) + "_" + parentName
        shorten( longName )

    def leaf2Name( leaf : ILeaf, parentName : String ) : String =
        val longName = entity2Name(leaf) + "_" + parentName
        shorten( longName )

    def extractEdgesFromLinks(  links : Seq[Link],
                                rootState : Node,
                                parentMap : Map[Node,Node],
                                entityToNodeMap : Map[IEntity, Node],
                                edgeSet : mutable.Set[Edge] ) : Unit =
        for link <- links do
            val source = link.getEntity1()
            val target = link.getEntity2()
            if !(entityToNodeMap contains source) || !(entityToNodeMap contains target) then
                reportWarning(s"Link from ${source.getCode().getName()} to ${target.getCode().getName()} ignored.")
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

    def indexTheNodes( stateChart : StateChart ) : Unit = 
        var globalIndex = 0
        val nodeList = stateChart.nodes.toSeq.sortBy( (n : Node) => n.rank )
        for n <- nodeList do
            n.setGlobalIndex( globalIndex )
            globalIndex += 1
        end for
        indexTheNodes( stateChart.root, -1 )
        
        def indexTheNodes( node : Node, localIndex : Int ) : Unit =
            node.setLocalIndex( localIndex ) 
            node match
                case Node.AndState( _, children ) => indexNodeList( children )
                case Node.OrState( _, children ) => indexNodeList( children )
                case _ => ()
        end indexTheNodes
        def indexNodeList( nodeList : Seq[Node] ) : Unit =
            val sorted = nodeList.sortBy( _.rank )
            var i = 0
            for node <- sorted do
                indexTheNodes( node, i)
                i += 1
            end for
        end indexNodeList
    end indexTheNodes

    def setTheStartNodes( stateChart : StateChart ) : Unit = {
        val orStates = stateChart.nodes.map( node => node.asOrState ).flatten
        for orState <- orStates do
            val children = orState.children
            val startNodes = children.filter( child => child.isStartNode )
            startNodes.size match 
                case 0 =>
                    if orState.childStates.size == 1 then
                        val startNode = orState.childStates.head
                        orState.optStartingIndex = Some( startNode.getLocalIndex )
                        reportWarning( s"Or state ${orState.getName} has no start marker. It only has 1 child state, ${startNode.getName}. That state will be the start state for the OR state.")
                    else
                        reportError( s"Or state ${orState.getName} has no start markers.")
                    end if
                case 1 =>
                    val startNode = startNodes.head
                    val edges = stateChart.edges.filter( e => e.source == startNode )
                    edges.size match
                        case 0 => reportError( s"Or state ${orState.getName} has no initial state.")
                        case 1 =>
                            val startNode = edges.head.target
                            if ! startNode.isState then 
                                reportError( s"Or state ${orState.getName} has an initial state that is not a state.")
                            end if
                            if stateChart.parentMap( startNode ) == orState  then
                                orState.optStartingIndex = Some( startNode.getLocalIndex )
                            else reportError( s"Or state ${orState.getName} has an initial state ${startNode.getName} that is not its child.")
                            end if
                        case _ => reportError( s"Or state ${orState.getName} has more than one initial state.")
                case _ => reportError( s"Or state ${orState.getName} has more than one start marker.")
    }
end MiddleEnd