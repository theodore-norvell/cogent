package cogent

import org.scalatest.flatspec.AnyFlatSpec

class TestChecker extends AnyFlatSpec :

    "the checker" should "correct duplicate state names" in {
        val logger = new LoggerForTesting
        val otherNode = new Node.BasicState( StateInformation( "fred", 1, Stereotype.None ))
        val children = Seq( otherNode ) 
        val rootNode = new Node.OrState( new StateInformation( "fred", 0, Stereotype.None  ), children )
        val parentMap : Map[Node,Node] = Map( (otherNode -> rootNode ) )
        val nodes : Set[Node] = Set(rootNode, otherNode)
        val edges : Set[Edge] = Set()
        val stateChart = new StateChart( "*main*", "line 32", rootNode, nodes, edges, parentMap, true )
        val middle = MiddleEnd(logger)
        middle.setCNames( stateChart )
        assert( otherNode.getCName == "fred" && rootNode.getCName == "fred_0"
                || rootNode.getCName == "fred" && otherNode.getCName == "fred_0" )
        val checker = new Checker( logger )
        checker.check( stateChart )
        assert( logger.fatalCount == 0 ) ;
    }

    it should "correct bad C names" in {
        val logger = new LoggerForTesting
        val v =  new Node.BasicState( StateInformation( "if", 1, Stereotype.None ))
        val w = new Node.BasicState( StateInformation( "", 1, Stereotype.None ))
        val x = new Node.BasicState( StateInformation( "break", 1, Stereotype.None ))
        val y = new Node.BasicState( StateInformation( "ok", 1, Stereotype.None ))
        val z = new Node.BasicState( StateInformation( "$*#\u1234", 1, Stereotype.None ))
        val children = Seq[Node]( v, w, x, y, z )
        val rootNode = new Node.OrState( new StateInformation( "if", 0, Stereotype.None ), children )
        val parentMap : Map[Node,Node] = Map(children map {n=> (n->rootNode)} : _*)
        val nodes : Set[Node] = Set(rootNode) union children.toSet
        val edges : Set[Edge] = Set()
        val stateChart = new StateChart( "*main*", "line 23", rootNode, nodes, edges, parentMap, true )
        val middle = MiddleEnd(logger)
        middle.setCNames( stateChart )
        assert( w.getCName == "S")
        assert( x.getCName == "break_0")
        assert( y.getCName == "ok" )
        assert( z.getCName == "_u36__u42__u35__u4660_" )
        assert( rootNode.getCName == "if_0" && v.getCName == "if_1"
                || v.getCName == "if_0" && rootNode.getCName == "if_1" )

        val checker = new Checker( logger )
        checker.check( stateChart )
        assert( logger.fatalCount == 0 ) ;
    }
end TestChecker
