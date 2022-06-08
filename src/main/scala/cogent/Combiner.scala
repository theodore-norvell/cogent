package cogent

import scala.collection.mutable ;

class Combiner( val logger : Logger ) :
    def combine( stateChartList : List[StateChart] )
    : Option[StateChart] =
        val primaryStateMachine = stateChartList.head
        val len = stateChartList.length
        val submachineDefinitions = stateChartList.slice(1, len)
        val map = createDefinitionMap( submachineDefinitions )
        if logger.hasFatality  then
            None
        else
            expandDefinition( primaryStateMachine, map )
        
    def createDefinitionMap( submachineDefinitions : List[StateChart] )
    : Map[String,(StateChart, Node)] = {
        var map : Map[String,(StateChart, Node)] = Map[String,(StateChart, Node)]() 
        for stateChart <- submachineDefinitions do
            val root = stateChart.root
            if root.asOrState.head.children.length != 1 then
                logger.fatal( "Submachine definitions should contain one state under the root." )
            else
                val definition = root.asOrState.head.children.head
                val name = definition.getFullName
                map = map.updated(name, (stateChart, definition))
        map }

        def expandDefinition(
                stateChart : StateChart,
                submachineDefinitions : Map[String,(StateChart, Node)] )
        : Option[StateChart] = {
            Some(stateChart) }

        def processNode (
            currentChart : StateChart,
            currentParent : Node,
            currentNode : Node,
            submachineDefinitions : Map[String,(StateChart, Node)],
            nodes : mutable.Set[Node],
            edges : mutable.Set[Edge],
            parentMap : mutable.Map[Node, Node],
            definitionsUsed : mutable.Set[String]
        ) : Option[Node] = {
            if currentNode.isState && currentNode.getStereotype == Stereotype.Submachine then
                checkNodeIsSuitable( currentChart, currentNode, submachineDefinitions, definitionsUsed ) 
                if ! logger.hasFatality then
                    val name = currentNode.getFullName
                    val (subChart, replacementNode) = submachineDefinitions( name )
                    processNode( subChart, currentParent, submachineDefinitions, nodes, edges, parentMap, definitionsUsed )
                    // TODO
                    // Add all edges that are to this node

            else {
                None // TODO
            }
        }