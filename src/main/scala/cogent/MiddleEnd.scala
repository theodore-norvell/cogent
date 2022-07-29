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

    enum ParentKind{ case AND; case OR; case NONE; }

    def processBlocks( blockList : Iterable[BlockUml] ) : List[StateChart] =
        val listOfOptions = for block <- blockList
            yield processBlock(block)
        listOfOptions.toList.flatten

    def processBlock( block : BlockUml ) : Option[StateChart] =
        val diagram = block.getDiagram()
        // It would be nice to be able to find the diagram name.
        logger.log( Info, s"Processing diagram")
        {
            given Logger = logger
            blockPrinter.printBlock( block )
        }
        diagram match
        case ( stateDiagram : StateDiagram ) => 
            constructStateChart( stateDiagram )
        case _ => logger.info( "Diagrams that are not state diagrams are ignored." )
            None

    def constructStateChart( stateDiagram : StateDiagram ) : Option[StateChart] =
        // First: recursively walk the tree of PlantUML entities (states and state-like things) in
        // `stateDiagram`.  For each entity we create a corresponding `cogent.Node`.  `entityToNodeMutMap`
        // records a mapping from IEntity objects to `Node` objects.
        val rootGroup : IGroup = stateDiagram.getRootGroup()
        val junk : mutable.Map[IEntity,Node] = new mutable.HashMap[IEntity,Node]()
        val entityToNodeMutMap = mutable.HashMap[IEntity, Node]()

        extractState( 0, "", ParentKind.NONE, -1, rootGroup, junk, entityToNodeMutMap )

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

        var stateChart = StateChart( rootState, nodeSet, edgeSet, parentMap )
        indexTheNodes( stateChart )
        setTheStartNodes( stateChart )
        setCNames( stateChart )
        stateChart = addMissingTriggers( stateChart )
        logger.log( Debug, stateChart.show )
        return Some( stateChart )
    end constructStateChart

    def extractState(   depth : Int,
                        parentName : String,
                        parentKind : ParentKind,
                        index : Int,
                        group : IGroup,
                        sink : mutable.Map[IEntity,Node],
                        entityToNodeMap : mutable.Map[IEntity,Node]
    ) : Unit =
        val name = group2Name( group, parentName, parentKind, index )

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

        val stereotype = try group.getStereotype() catch (e) => null
        if stereotype != null && !stereotype.toString.equals("<<submachine>>") then 
                logger.info( s"Composite node $name has an unknown stereotype ${stereotype.toString}. The stereotype will be ignored.")
        end if
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
                    + s"but it also has other children. This seems wrong.")
    end extractState

    def extractState(   depth : Int,
                        parentName : String,
                        parentKind : ParentKind,
                        index : Int,
                        leaf : ILeaf,
                        sink : mutable.Map[IEntity,Node],
                        entityToNodeMap : mutable.Map[IEntity,Node]
    ) : Unit = 
        val name = leaf2Name( leaf, parentName )
        val node : Node =
            leaf.getLeafType() match
                case LeafType.STATE =>
                    val stereotype = leaf.getStereotype()
                    if stereotype != null then 
                        if stereotype.toString.equals( "<<choice>>" ) then
                            logger.debug( s"Leaf node $name identified as a CHOICE pseudostate. ")
                            val stateInfo = StateInformation( name, depth, Stereotype.None )
                            Node.ChoicePseudoState( stateInfo )
                        else if stereotype.toString.equals( "<<entryPoint>>" ) then
                            val stateInfo = StateInformation( name, depth, Stereotype.None )
                            Node.EntryPointPseudoState( stateInfo )
                        else if stereotype.toString.equals( "<<exitPoint>>" ) then
                            val stateInfo = StateInformation( name, depth, Stereotype.None )
                            Node.ExitPointPseudoState( stateInfo )
                        else if stereotype.toString.equals( "<<submachine>>") then
                            val stateInfo = StateInformation( name, depth, Stereotype.Submachine )
                            logger.log(Debug, s"Leaf node $name identified as a BASIC submachine state. ")
                            Node.BasicState( stateInfo ) 
                        else if unsupportedStereotypes contains stereotype.toString then
                            reportError( s"Leaf node $name has an unsupported stereotype ${stereotype.toString}.")
                            logger.debug( s"Leaf node $name identified as a BASIC state. ")
                            val stateInfo = StateInformation( name, depth, Stereotype.None )
                            Node.BasicState( stateInfo )
                        else
                            logger.info( s"Leaf node $name has an unknown stereotype ${stereotype.toString}. It will be treated as a basic state.")
                            logger.debug( s"Leaf node $name identified as a BASIC state. ")
                            val stateInfo = StateInformation( name, depth, Stereotype.None )
                            Node.BasicState( stateInfo )
                        end if
                    else
                        logger.log(Debug, s"Leaf node $name identified as a BASIC state. ")
                        val stateInfo = StateInformation( name, depth, Stereotype.None )
                        Node.BasicState( stateInfo )
                    end if
                case LeafType.CIRCLE_START =>
                    logger.log(Debug, s"Leaf node $name identified as a START marker. ")
                    val stateInfo = StateInformation( name, depth, Stereotype.None )
                    Node.StartMarker( stateInfo )
                case LeafType.STATE_CHOICE => 
                    logger.log(Debug, s"Leaf node $name identified as a CHOICE pseudostate. ")
                    val stateInfo = StateInformation( name, depth, Stereotype.None )
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
        var childIndex = 0 
        for g <- relevantGroupChildren do
            extractState( depth+1, name, ParentKind.AND, childIndex, g, childMap, entityToNodeMap )
            childIndex += 1
        end for
        val children = childMap.values.toSeq
        val stereotype = try group.getStereotype() catch (e) => null
        val st = 
            if stereotype != null && stereotype.toString.equals( "<<submachine>>" ) then
                Stereotype.Submachine
            else Stereotype.None
        val stateInfo = StateInformation( name, depth, st )
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
        var childIndex = 0
        for g <- relevantGroupChildren do
            extractState( depth+1, name, ParentKind.OR, childIndex, g, childMap, entityToNodeMap )
            childIndex += 1
        for leaf <- relevantLeafChildren do 
            extractState( depth+1, name, ParentKind.OR, childIndex, leaf, childMap, entityToNodeMap )
            childIndex += 1
        val children = childMap.values.toSeq
        val stereotype = try group.getStereotype() catch (e) => null
        val st = 
            if stereotype != null && stereotype.toString.equals( "<<submachine>>" ) then
                Stereotype.Submachine
            else Stereotype.None
        val stateInfo = StateInformation( name, depth, st )
        val orState = Node.OrState( stateInfo, children )
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
                reportError( s"Found a leaf $name marked STATE_CONCURRENT. Don't know what to do with that.")
                false
            case LeafType.CIRCLE_END =>
                reportError( s"Found final state $name. Final state are not supported yet.")
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
            case _ =>
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

    def group2Name( group : IGroup, parentName : String, parentKind : ParentKind, index : Int ) : String =
        if parentKind==ParentKind.NONE then "root"
        else if parentKind==ParentKind.AND then s"${parentName}_region_${index}"
        else entity2Name( group )

    def leaf2Name( leaf : ILeaf, parentName : String ) : String =
        entity2Name(leaf)

    def extractEdgesFromLinks(  links : Seq[Link],
                                rootState : Node,
                                parentMap : Map[Node,Node],
                                entityToNodeMap : Map[IEntity, Node],
                                edgeSet : mutable.Set[Edge] ) : Unit =
        for link <- links do
            val source = if link.isInverted() then link.getEntity2() else link.getEntity1()
            val target = if link.isInverted() then link.getEntity1() else link.getEntity2()
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
        // Sort the nodes so we have OR states first, followed by other states
        // and then other nodes.
        val nodeList = stateChart.nodes.toSeq.sortBy( (n : Node) => n.rank )
        for n <- nodeList do
            logger.debug( s"Mapping ${n.getFullName} to $globalIndex")
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
            val startMarkers = children.filter( child => child.isStartMarker )
            var initialState : Node = null 
            startMarkers.size match 
                case 0 =>
                    if orState.childStates.size == 1 then
                        initialState = orState.childStates.head
                        logger.log( Info, s"Or state ${orState.getFullName} has no start marker." +
                                            s" It only has 1 child state, ${initialState.getFullName}. " + 
                                            "That state will be the start state for the OR state.")
                    else
                        logger.log( Info, s"Or state ${orState.getFullName} has no start markers.")
                    end if
                case 1 =>
                    val startMarker = startMarkers.head
                    val edges = stateChart.edges.filter( e => e.source == startMarker )
                    edges.size match
                        case 0 =>
                            logger.log( Info, s"Or state ${orState.getFullName} has no initial state.")
                        case 1 =>
                            var candidate : Node = edges.head.target
                            if ! candidate.isState then 
                                reportError( s"Or state ${orState.getFullName} has an initial vertex that is not a state.")
                            else if stateChart.parentMap( candidate ) != orState  then
                                reportError( s"Or state ${orState.getFullName} has an initial state ${candidate.getFullName} that is not its child.")
                            else
                                initialState = candidate
                            end if
                        case _ => reportError( s"Or state ${orState.getFullName} has more than one initial state.")
                case _ => reportError( s"Or state ${orState.getFullName} has more than one start marker.")
            // We change its local index to 0 by swapping its index with that of the state that is currently 0
            if initialState != null then
                val state0 = children.filter( child => child.getLocalIndex == 0 ).head
                assert( state0.isState)
                val startIndex = initialState.getLocalIndex
                state0.setLocalIndex( startIndex )
                initialState.setLocalIndex( 0 )
                orState.setInitialState( initialState )
            end if
        end for
    }



    def setCNames(  stateChart : StateChart ) : Unit =
        logger.log( Debug, "Ensure no duplicate state names")
        val cNames : mutable.Set[ String ] = new mutable.HashSet[ String ]()
        for node <- stateChart.nodes do
            val name = node.getFullName
            val len = name.length
            val shortName = (if len > 10 then name else name.substring(0, len))
            var cName = shortName
            var counter = 0
            while cNames contains cName do
                cName = shortName + counter
                counter += 1
            end while
            node.setCName( cName )
            cNames += name
        end for
    end setCNames

    def addMissingTriggers(  stateChart : StateChart ) : StateChart =
        logger.log( Debug, "AddingMissingTriggers")
        val newEdges = mutable.Set[Edge]()
        for edge <- stateChart.edges do
            logger.debug( s"Processing edge $edge")
            edge match 
                case Edge( source, target, triggerOpt, guardOpt, actions ) =>
                    logger.debug( s"triggerOpt.isEmpty is ${triggerOpt.isEmpty}")
                    logger.debug( s"source.isState is ${source.isState}")
                    if triggerOpt.isEmpty && source.isState then
                        val newEdge = Edge( source, target, Some(Trigger.AfterTrigger(0)), guardOpt, actions )
                        newEdges += newEdge
                        if source.isBasicState then
                            logger.info( s"Edge $edge exiting state ${source.getFullName} has no trigger. " +
                                            "The trigger will be assumed to be 'after(0s)'.")
                        else
                            reportError( s"Edge $edge exiting compound state ${source.getFullName} has no trigger. " +
                                            "This is not allowed because completion events are not yet supported." )
                        end if
                    else
                        newEdges += edge
                    end if
        end for
        StateChart( stateChart.root, stateChart.nodes, newEdges.toSet, stateChart.parentMap )
    end addMissingTriggers

    private val unsupportedStereotypes = Set(
        "<<fork>>", "<<join>>",
        "<<inputPin>>", "<<outputPin>>",
        "<<expansionInput>>", "<<expansionOutput>>" )
end MiddleEnd