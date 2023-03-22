package cogent

import scala.collection.mutable
import scala.util.control.NonLocalReturns._

class Combiner( val logger : Logger ) :

    type NameToDefinitionMap = mutable.Map[String, StateChart]
    type UseToDefinitionMap = mutable.Map[(StateChart,Node), String]

    def combine( stateChartList : List[StateChart] )
    : Option[StateChart] = returning {
        val primaryStateMachine = stateChartList.head
        val len = stateChartList.length
        val submachineDefinitions = stateChartList.slice(1, len)
        val defMap = createDefinitionMap( submachineDefinitions )
        if logger.hasFatality  then
            None
        else
            val useToDefMap = makeUseToDefinitionMap( stateChartList, defMap )
            if logger.hasFatality then
                None
            else
                val orderedSubMachineDefinitions = topologicalSort( submachineDefinitions, useToDefMap, defMap )
                if logger.hasFatality then
                    None
                else
                    // Process from the bottom of the DAG up to the root.
                    val statecharts = orderedSubMachineDefinitions.reverse
                    for sc <- statecharts do
                        val scExpandedOpt = expand( sc, useToDefMap, defMap, false )
                        // Update the map 
                        if scExpandedOpt.nonEmpty then
                            for name <- defMap.keySet do
                                if defMap(name) == sc then
                                    defMap(name) = scExpandedOpt.head
                        else
                            throwReturn[Option[StateChart]]( None )
                    val expandedOpt = expand(primaryStateMachine, useToDefMap, defMap, true )
                    expandedOpt
    }

    private def createDefinitionMap( submachineDefinitions : List[StateChart] )
    : NameToDefinitionMap = {
        var map = mutable.Map[String,StateChart]() 
        for statechart <- submachineDefinitions do
            val root = statechart.root
            if root.asOrState.head.children.length != 1 then
                logger.fatal( s"${statechart.description}. Submachine definitions should contain exactly"
                            + " one state under the root." )
            else
                val topNode = root.asOrState.head.children.head
                val name = topNode.getFullName
                if map.keySet.contains( name ) then
                    logger.fatal(s"${statechart.description}. $name is already defined by ${map(name).description}." )
                else
                    logger.debug(s"$name --> ${statechart.description}")
                    map(name) = statechart
        map }

    private def makeUseToDefinitionMap( stateChartList : List[StateChart],
                                defMap : NameToDefinitionMap ) 
    : UseToDefinitionMap = {
        var useToDef : UseToDefinitionMap = mutable.Map[(StateChart,Node), String]()
        for statechart <- stateChartList do
            for node <- statechart.nodes do
                val name = node.getFullName
                if defMap.keySet.contains( name ) then
                    val definition = defMap.get( name ).head
                    val root = definition.root.asOrState.head
                    assert( root.children.length == 1 )
                    val topNode = root.children.head
                    // If the source node is the top node of the definition, then this
                    // is not a macro call.
                    if node != topNode then
                        if node.getStereotype != Stereotype.Submachine then
                            logger.warning( s"Node $name in ${statechart.description} appears to be a reference to a submachine, but does not have a submachine stereotype.")
                        useToDef((statechart,node)) = name 
                        logger.debug(s"(${statechart.description},${node.getFullName}) --> ${definition.description}")
        useToDef }
    
    private def topologicalSort(
                    list : List[StateChart],
                    useToDefMap : UseToDefinitionMap,
                    defMap : NameToDefinitionMap)
    : List[StateChart] = {
        val white : mutable.Set[StateChart] = mutable.Set.from(list)
        val grey : mutable.Set[StateChart] = mutable.Set[StateChart]()
        val black : mutable.Set[StateChart] = mutable.Set[StateChart]()
        var s : mutable.Stack[StateChart] = mutable.Stack[StateChart]()

        def dfs( u : StateChart, us : List[StateChart] ) : Unit = {
            white.remove(u) ; grey.add( u )
            val successors =
                for (   (sc0,node0) <- useToDefMap.keySet ;
                        if sc0 == u ;
                        name = useToDefMap.apply((sc0,node0)) ;
                        sc1 = defMap(name)
                    ) yield sc1 
            val us1 = u :: us
            for v <- successors do
                if white.contains(v) then
                    dfs(v, us1)
                else if us1.contains( v ) then
                    logger.debug( s"us1 is ${us1.map( (sc : StateChart) => sc.description )}")
                    logger.debug( s"v is ${v.description}")
                    val loc = us1.indexOf(v) 
                    val cycle : List[StateChart] = (v :: us1.take( loc+1 )).reverse
                    logger.debug( s"Cycle length is ${cycle.length}")
                    logger.debug( s"Cycle is ${cycle.map( (sc : StateChart) => sc.description )}")
                    var a = cycle.head 
                    logger.fatal( s"${v.description}. Cycle of submachines found.")
                    for b <- cycle.tail do
                        logger.fatal( s"    ${a.description} uses ${b.description}.")
                        a = b
                end if

            end for
            s.push( u )
            grey.remove(u) ; black.add(u)
        }

        while white.nonEmpty do
            val u = white.head
            dfs( u, List[StateChart]() )
        s.toList
    }

    // Expand one statechart by substituting definitions for references to
    // state machines.  Note, this process is not recursive; it only expands one
    // level of submachines. Therefore each submachine that is used should already
    // have been expanded.
    private def expand(
                    sc : StateChart,
                    useToDefMap : UseToDefinitionMap,
                    defMap : NameToDefinitionMap,
                    isMain : Boolean )
    : Option[StateChart] = {
        val root = sc.root.asOrState.head
        val edgesToAdd = mutable.Set[Edge]()
        val newNodes = mutable.Set[Node]()
        val nodeMap = mutable.Map[Node,Node]()
        val excludedNodes = mutable.Set[Node]()
        if ! isMain then
            val topNode = sc.root.childNodes.head
            excludedNodes.add( topNode )
            for child <- topNode.childNodes do
                child match {
                    case _ : Node.EntryPointPseudoState =>
                        excludedNodes.add( child )
                    case _ : Node.ExitPointPseudoState =>
                        excludedNodes.add( child )
                    case _ => () }
        val newTree = copyNode( root, sc, excludedNodes.toSet, useToDefMap, defMap, edgesToAdd, newNodes, nodeMap )
        
        // Some of the errors that could have happened
        // during copyNode will mean that some nodes are not
        // included in the nodeMap.  This will cause problems in
        // the copying of edges and so we bail out here.
        if ! logger.hasFatality then
            // edgesToAdd should already contain any edges derived from submachine expansion. However,
            // it will not have edges derived from the edges of this statechart.
            for edge <- sc.edges do
                val src = nodeMap( edge.source )
                val trg = nodeMap( edge.target )
                val triggerOpt = edge.triggerOpt
                val guardOpt = edge.guardOpt
                val actions = edge.actions
                val newEdge = Edge( src, trg, triggerOpt, guardOpt, actions )
                edgesToAdd.add( newEdge ) 
            val parentMap = mutable.Map[Node, Node]()
            newTree.computeParentMap( parentMap ) 
            val result = StateChart(
                sc.name,
                sc.location,
                newTree,
                newNodes.toSet,
                edgesToAdd.toSet,
                parentMap.toMap,
                sc.isFirst )
            Some(result)
        else
            None
    }

    private def copyNode(
                    node : Node,
                    sc : StateChart,
                    excludedNodes : Set[Node],
                    useToDefMap : UseToDefinitionMap,
                    defMap : NameToDefinitionMap,
                    edgesToAdd : mutable.Set[Edge],
                    newNodes : mutable.Set[Node],
                    nodeMap : mutable.Map[Node,Node] )
    : Node = {
        if !excludedNodes.contains( node ) && useToDefMap.keySet.contains((sc,node)) then
            if node.getStereotype != Stereotype.Submachine then
                logger.warning( s"${sc.description}. State ${node.getFullName} appears to be a submachine reference, but does not have the <<submachine>> stereotype.")
            val definition = defMap( useToDefMap((sc,node)) )
            logger.info( s"${sc.description}. Node ${node.getFullName} will be expanded using ${definition.description}")
            replace( node, sc, definition, edgesToAdd, newNodes, nodeMap )
            // replace should have added all nodes copied from the definition to
            // the newNodes set and the nodeMap map also new edges in terms of new
            // nodes should have been added to edgesToAdd
            // Finally the node map should have added entries for node and for
            // its entry and exit pseudostates.
        else
            if !excludedNodes.contains( node ) && node.getStereotype == Stereotype.Submachine then
                logger.fatal( s"${sc.description}. State ${node.getFullName} has the <<submachine>> stereotype, but no definition for that submachine was found.")
            end if
            // Here we don't make copies of the nodes (unless they have children), since the original statechart
            // will be thrown away.
            val newNode = node match {
                    case Node.BasicState( si ) =>
                        node
                    case Node.ChoicePseudoState( si ) =>
                        node
                    case Node.StartMarker( si ) =>
                        node
                    case Node.OrState( si, children ) =>
                        def temp( node : Node ) = copyNode( node, sc, excludedNodes, useToDefMap, defMap, edgesToAdd, newNodes, nodeMap ) ;
                        val newChildren = children.map( temp )
                        Node.OrState( si, newChildren )
                    case Node.AndState( si, children ) =>
                        def temp( node : Node ) = copyNode( node, sc, excludedNodes, useToDefMap, defMap, edgesToAdd, newNodes, nodeMap ) ;
                        val newChildren = children.map( temp )
                        Node.AndState( si, newChildren )
                    case Node.EntryPointPseudoState( si ) =>
                        if excludedNodes.contains( node ) then
                            node
                        else
                            Node.ChoicePseudoState( si )
                    case Node.ExitPointPseudoState( si ) =>
                        if excludedNodes.contains( node ) then
                            node
                        else
                            Node.ChoicePseudoState( si )
            }
            newNodes.add( newNode ) 
            nodeMap(node) = newNode
            newNode
    }

    private def replace(
                    node : Node,
                    sc : StateChart,
                    definition : StateChart,
                    edgesToAdd : mutable.Set[Edge], // These must go between new nodes.
                    newNodes : mutable.Set[Node],
                    upperNodeMap : mutable.Map[Node,Node]  )
    : Node = {
        val root = definition.root.asOrState.head
        val topNode = root.children.head
        val entryExitMap = mutable.Map[Node, Node]()
        computeEntryExitMap( node, topNode, entryExitMap, sc, definition )
        val suffix = "__" + node.getFullName
        val lowerNodeMap = mutable.Map[Node,Node]()
        val depth = node.getDepth
        val newTopNode = copySubmachineNodes( topNode, suffix, depth, sc, definition, newNodes, lowerNodeMap )

        // Now all the nodes from the definition have been copied and are in newNodes.
        // In addition the lowerNodeMap maps from nodes in the definition to new nodes.
        // Now we compute the edges that come from the definition
        for edge <- definition.edges do
            val src = lowerNodeMap( edge.source )
            val trg = lowerNodeMap( edge.target )
            val triggerOpt = edge.triggerOpt
            val guardOpt = edge.guardOpt.map( x => modifyGuard( x, suffix ) )
            val actions = edge.actions
            val newEdge = Edge( src, trg, triggerOpt, guardOpt, actions )
            edgesToAdd.add( newEdge ) 
        
        // At this point each entry and exit in the definition should have been mapped to a 
        // new node.  The entry and exit nodes of the use should be mapped to the same nodes.
        for (trgNode, srcNode) <- entryExitMap do
            upperNodeMap( srcNode ) = lowerNodeMap( trgNode )
        upperNodeMap( node ) = newTopNode
        newTopNode
    }

    def modifyGuard( guard : Guard, suffix : String  ) : Guard = {
        // TODO
        guard
    }

    def computeEntryExitMap(
                    srcNode : Node, 
                    targetNode : Node,
                    entryExitMap : mutable.Map[Node,Node],
                    sc : StateChart,
                    definition : StateChart) : Unit = {
        for srcNodeChild <- srcNode.childNodes do
            var mapped = false
            if srcNodeChild.isEntryPseudostate || srcNodeChild.isExitPseudostate then
                for trgNodeChild <- targetNode.childNodes do
                    if srcNodeChild.getFullName == trgNodeChild.getFullName then
                        if srcNodeChild.isEntryPseudostate then
                            if trgNodeChild.isEntryPseudostate then
                                entryExitMap(trgNodeChild) = srcNodeChild
                                mapped = true
                            else
                                logger.fatal( s"${sc.description}. Node ${srcNodeChild.getFullName} is an entry pseudostate, but the corresponding node in ${definition.description} is not.")
                                logger.debug( s"    Src child is ${srcNodeChild.show} .")
                                logger.debug( s"    Trg child is ${trgNodeChild.show} .")
                        else if srcNodeChild.isExitPseudostate then
                            if trgNodeChild.isExitPseudostate then
                                entryExitMap(trgNodeChild) = srcNodeChild
                                mapped = true
                            else
                                logger.fatal( s"${sc.description}. Node ${srcNodeChild.getFullName} is an exit pseudostate, but the corresponding node in ${definition.description} is not.")
                        end if
                    end if
                end for
                if ! mapped then
                    logger.fatal( s"${sc.description}. Pseudostate ${srcNodeChild.getFullName} is a child of a submachine reference but there is no corresponding pseudo state in the definition at ${definition.description}." )
                end if
            else
                logger.fatal( s"${sc.description}. Node ${srcNodeChild.getFullName} is a child of a submachine reference but is not an entry nor an exit state." )
            end if
        end for

        // Check that all entries and exits used in the definition are used.
        for trgNodeChild <- targetNode.childNodes do
            if trgNodeChild.isEntryPseudostate || trgNodeChild.isExitPseudostate then
                if ! entryExitMap.keySet.contains( trgNodeChild ) then 
                    logger.fatal(   s"${sc.description}. Pseudostate ${trgNodeChild.getFullName} in" +
                                    s" ${definition.description} is a child of a submachine definition" +
                                    s"node but there is no corresponding pseudo state when the submachine is used in ${sc.description}")
        end for
    }

    private def copySubmachineNodes( 
                    node : Node,
                    suffix : String,
                    depth : Int,
                    sc : StateChart,
                    definition : StateChart,
                    newNodes : mutable.Set[Node],
                    nodeMap : mutable.Map[Node,Node]  )
    : Node = {
        val topNode = definition.root.asOrState.head.children.head
        val stateInfo = node.stateInfo 
        val newName = stateInfo.fullName ++ suffix
        val newStateInfo =
            (if node == topNode then
                // We keep the old name, but we need to clear the <<submachine>> stereotype
                stateInfo.copy( node.getFullName, depth, Stereotype.None )
            else
                // Otherwise we change the name by appending a suffix
                stateInfo.copy( newName, depth, node.getStereotype ) )
        val newNode = node match
                case Node.BasicState( si ) =>
                    Node.BasicState( newStateInfo )
                case Node.ChoicePseudoState( si ) =>
                    Node.ChoicePseudoState( newStateInfo )
                case Node.StartMarker( si ) =>
                    Node.StartMarker( newStateInfo )
                case Node.OrState( si, children ) =>
                    def recurse( child : Node ) =
                        copySubmachineNodes( child, suffix, depth+1, sc, definition, newNodes, nodeMap ) ;
                    val newChildren = children.map( recurse )
                    Node.OrState( newStateInfo, newChildren )
                case Node.AndState( si, children ) =>
                    def recurse( child : Node ) =
                        copySubmachineNodes( child, suffix, depth+1, sc, definition, newNodes, nodeMap ) ;
                    val newChildren = children.map( recurse )
                    Node.AndState( newStateInfo, newChildren )
                case Node.EntryPointPseudoState( si ) =>
                    if definition.parentOf(node) != topNode then
                        logger.fatal(s"${sc.description}. Internal error. Unexpected entry pseudo state ${node.getFullName} found in expanded submachine definition. Submachine is ${definition.description}.")
                    Node.ChoicePseudoState( newStateInfo )
                case Node.ExitPointPseudoState( si ) =>
                    if definition.parentOf(node) != topNode then
                        logger.fatal(s"${sc.description}. Internal error. Unexpected exit pseudo state ${node.getFullName} found in expanded submachine definition. Submachine is ${definition.description}.")
                    Node.ChoicePseudoState( newStateInfo )

        newNodes.add( newNode )
        nodeMap(node) = newNode
        newNode
    }