package cogent

import org.scalatest.flatspec.AnyFlatSpec

class TestChecker extends AnyFlatSpec :

    "the checker" should "flag duplicate state names" in {
        val logger = new LoggerForTesting
        val otherNode = new Node.BasicState( StateInformation( "fred", 1, 0))
        val children = Seq( otherNode ) 
        val rootNode = new Node.OrState( new StateInformation( "fred", 0, 0), children )
        val parentMap : Map[Node,Node] = Map( (otherNode -> rootNode ) )
        val nodes : Set[Node] = Set(rootNode, otherNode)
        val edges : Set[Edge] = Set()
        val stateChart = new StateChart( rootNode, nodes, edges, parentMap )
        val checker = new Checker( logger )
        checker.check( stateChart )
        assert( logger.fatalCount == 1 ) ;
    }

    it should "flag bad C names" in {
        val logger = new LoggerForTesting
        val otherNode = new Node.BasicState( StateInformation( "fred!", 1, 0))
        val children = Seq( otherNode ) 
        val rootNode = new Node.OrState( new StateInformation( "1fred", 0, 0), children )
        val parentMap : Map[Node,Node] = Map( (otherNode -> rootNode ) )
        val nodes : Set[Node] = Set(rootNode, otherNode)
        val edges : Set[Edge] = Set()
        val stateChart = new StateChart( rootNode, nodes, edges, parentMap )
        val checker = new Checker( logger )
        checker.check( stateChart )
        assert( logger.fatalCount == 2 ) ;
    }
end TestChecker
