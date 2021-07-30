package cogent 

class Checker( val logger : Logger ) :

    import scala.collection.mutable
    import Logger.Level._

    def check( stateChart : StateChart ) : Unit =
        childrenOfANDsAreORs( stateChart )
        checkValidCNamesForStatesAndChoices( stateChart )
        checkNoLabelsOnStartEdges( stateChart )
        checkNoEdgesToStart( stateChart )
        checkAllEdgesReachable( stateChart )
        checkAllEdgesOutOfStatesHaveTriggers( stateChart )
        checkOnlyEdgesOutOfStatesHaveTriggers( stateChart )
        // Other things to check:
        // (a) Define a bundle to be a set of all transitions out
        //     of a state that have the same trigger or a set of
        //     all transitions out of a branch node.
        //     (i) Each bundle with size greater than one should have
        //         a guard on every edge.
        //     (ii) At most one edge in each bundle is labelled with "else".
    end check

    def childrenOfANDsAreORs(  stateChart : StateChart ) : Unit = {
        for n <- stateChart.nodes do
            n match
                case Node.AndState( _, children ) =>
                    for child <- children do
                        if ! child.isOrState then
                            logger.fatal( s"Vertex ${child.getFullName} is child of AND state ${n.getFullName}, but is not an OR state.")
                case _ => {}
    }

    def checkValidCNamesForStatesAndChoices(  stateChart : StateChart ) : Unit = {
        logger.log( Debug, "Checking for valid C names for states")
        val regEx = """[a-zA-Z_][a-zA-Z_0-9]*""".r
        for node <- stateChart.nodes do
            if node.isState || node.isChoicePseudostate then 
                val name = node.getCName 
                if ! regEx.matches(name) then
                    logger.log( Fatal, s"State or choice named $name is not a valid C identifier.")
                end if
            end if
        end for
        // TODO check for C keywords.  And do the same for actions and guards.
    }

    def checkNoLabelsOnStartEdges(  stateChart : StateChart ) : Unit = {
        val edgesFromStart = stateChart.edges.filter( e => e.source.isStartMarker )
        for e <- edgesFromStart do
            e match {
                case Edge(_, _, None, None, List()) => ()
                case _ => logger.log( Fatal, s"Edge $e from a start pseudo-state has a label." )
            }
    }

    def checkNoEdgesToStart(  stateChart : StateChart ) : Unit = {
        val edgesToStart = stateChart.edges.filter( e => e.target.isStartMarker )
        for e <- edgesToStart do
            logger.log( Fatal, s"Edge $e goes to a start pseudo-state")
    }

    def checkAllEdgesReachable(  stateChart : StateChart ) : Unit = {
        logger.log( Debug, "Checking for unreachable edges") 
        // A path is sequence of one of more edges that
        // starts at a state and ends at a state.
        val edges = edgesOnPaths( stateChart )
        val edgesFromStart = stateChart.edges.filter( e => e.source.isStartMarker )
        val edgesNotOnPaths = stateChart.edges -- edges -- edgesFromStart
        for e <- edgesNotOnPaths do
            logger.log( Warning, s"Edge $e is not reachable from any state")
    }

    def edgesOnPaths( stateChart : StateChart ) : Set[Edge] = {
        // Here we collect all edges on all paths that start at a state.
        // At the same time we check paths that start at a state, then reach a 
        // nonstate node and then cycle back to that nonstate node.
        val setOfSetOfEdges =
            for node <- stateChart.nodes
                if node.isState
                yield edgesOnPaths( node, stateChart, Set[Node]() )
        setOfSetOfEdges.flatten
    }

    def  edgesOnPaths( node : Node, stateChart : StateChart, visited : Set[Node] ) : Set[Edge] = {
        if visited contains node then
            if ! node.isState then
                logger.log( Fatal, s"There is a cycle involving nonstate node $node")
            end if
            Set[Edge]()
        else 
            val edges = mutable.Set[Edge]()
            for e <- stateChart.edges do
                if e.source == node then
                    edges += e
                    if ! e.target.isState then
                        // The edge ends on a pseudo-state. Keep going
                        val es = edgesOnPaths( e.target, stateChart, visited + node)
                        edges ++= es
            edges.toSet
    }

    def checkAllEdgesOutOfStatesHaveTriggers(  stateChart : StateChart ) : Unit = {
        val edgesFromStates = stateChart.edges.filter( e => e.source.isState )
        for e <- edgesFromStates do
            if e.triggerOpt.isEmpty then
                logger.fatal( "Edge $e has no trigger. Completion events are not supported." )
    }

    def checkOnlyEdgesOutOfStatesHaveTriggers(  stateChart : StateChart ) : Unit = {
        val edgesFromNonStates = stateChart.edges.filter( e => ! e.source.isState )
        for e <- edgesFromNonStates do
            if ! e.triggerOpt.isEmpty then
                logger.fatal( "Edge $e has a trigger. Only edges out of states may have triggers." )
    }


end Checker