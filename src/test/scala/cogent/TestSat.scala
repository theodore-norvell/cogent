package cogent

import org.scalatest.flatspec.AnyFlatSpec

class TestSat extends AnyFlatSpec :

    "the sat checker" should "find simple formulas satisfiable" in {
        val logger = new LoggerForTesting
        val g0 = new Guard.OKGuard( )
        assert( Satisfaction.is_satisfiable(logger, g0) )
        val g1 = new Guard.InGuard( "s" )
        assert( Satisfaction.is_satisfiable(logger, g1) )
        val g2 = new Guard.NamedGuard( "x" )
        assert( Satisfaction.is_satisfiable(logger, g2) )
        val g3 = new Guard.RawGuard( "{}" )
        assert( Satisfaction.is_satisfiable(logger, g3) )
    }

    it should "find (not x) satisfiable" in {
        val logger = new LoggerForTesting
        val g0 = new Guard.NamedGuard( "x" )
        val g1 = Guard.NotGuard( g0 )
        assert( Satisfaction.is_satisfiable(logger, g1) )
    }

    it should "find (x and y) satisfiable" in {
        val logger = new LoggerForTesting
        val g0 = new Guard.NamedGuard( "x" )
        val g1 = new Guard.NamedGuard( "y" )
        val g2 = Guard.AndGuard( g0, g1 )
        assert( Satisfaction.is_satisfiable(logger, g2) )
    }

    it should "find (x and x) satisfiable" in {
        val logger = new LoggerForTesting
        val g0 = new Guard.NamedGuard( "x" )
        val g1 = new Guard.NamedGuard( "x" )
        val g2 = Guard.AndGuard( g0, g1 )
        assert( Satisfaction.is_satisfiable(logger, g2) )
    }

    it should "find (x and not x) unsatisfiable" in {
        val logger = new LoggerForTesting
        val g3 = Guard.AndGuard( 
                    Guard.NamedGuard( "a" ),
                    Guard.NotGuard( Guard.NamedGuard( "a" ) ) )
        assert( ! Satisfaction.is_satisfiable(logger, g3) )
    }

    it should "find that it is possible for none of 'a or b' and 'b or c' 'a or c' to be true" in {
        val logger = new LoggerForTesting

        val g0 = Guard.OrGuard(
                    Guard.NamedGuard( "a" ),
                    Guard.NamedGuard( "b" ) )
        val g1 = Guard.OrGuard(
                    Guard.NamedGuard( "b" ),
                    Guard.NamedGuard( "c" ) )
        val g2 = Guard.OrGuard(
                    Guard.NamedGuard( "a" ),
                    Guard.NamedGuard( "c" ) )
        val seq = List(g0, g1, g2)
        assert( Satisfaction.possibly_none( logger, seq ) )
    }

    it should "not find that it is possible for none of 'a or b' and 'b or c' 'not a and not b and not c' to be true" in {
        val logger = new LoggerForTesting

        val g0 = Guard.OrGuard(
                    Guard.NamedGuard( "a" ),
                    Guard.NamedGuard( "b" ) )
        val g1 = Guard.OrGuard(
                    Guard.NamedGuard( "b" ),
                    Guard.NamedGuard( "c" ) )
        val g2 = Guard.AndGuard(
                    Guard.NotGuard(Guard.NamedGuard( "a" )),
                    Guard.AndGuard(
                        Guard.NotGuard(Guard.NamedGuard( "b" )),
                        Guard.NotGuard(Guard.NamedGuard( "c" ))) )
        val seq = List(g0, g1, g2)
        assert( ! Satisfaction.possibly_none( logger, seq ) )
    }

    it should "not find that at least one of 'a and not b', 'b and not a' 'not a and not b' is true" in {
        val logger = new LoggerForTesting

        val g0 = Guard.AndGuard( 
                    Guard.NamedGuard( "a" ),
                    Guard.NotGuard( Guard.NamedGuard( "b" ) ) )

        val g1 = Guard.AndGuard( 
                    Guard.NamedGuard( "b" ),
                    Guard.NotGuard( Guard.NamedGuard( "a" ) ) )
        val g2 = Guard.AndGuard( 
                    Guard.NotGuard( Guard.NamedGuard( "a" ) ),
                    Guard.NotGuard( Guard.NamedGuard( "b" ) ) )
        val seq = List(g0, g1, g2)
        assert( ! Satisfaction.definitely_at_least_one( logger, seq ) )
    }

    it should "find that at least one of 'a and not b', 'b and not a' 'not a or not b' is true" in {
        val logger = new LoggerForTesting

        val g0 = Guard.AndGuard( 
                    Guard.NamedGuard( "a" ),
                    Guard.NotGuard( Guard.NamedGuard( "b" ) ) )

        val g1 = Guard.AndGuard( 
                    Guard.NamedGuard( "b" ),
                    Guard.NotGuard( Guard.NamedGuard( "a" ) ) )

        val g2 = Guard.OrGuard( 
                    Guard.NotGuard( Guard.NamedGuard( "a" ) ),
                    Guard.NotGuard( Guard.NamedGuard( "b" ) ) )
        val seq = List(g0, g1, g2)
        assert( ! Satisfaction.definitely_at_least_one( logger, seq ) )
    }

    it should "find correct entailments between 'a and not b' 'b and not a' 'not a or not b'" in {
        val logger = new LoggerForTesting

        val g0 = Guard.AndGuard( 
                    Guard.NamedGuard( "a" ),
                    Guard.NotGuard( Guard.NamedGuard( "b" ) ) )

        val g1 = Guard.AndGuard( 
                    Guard.NamedGuard( "b" ),
                    Guard.NotGuard( Guard.NamedGuard( "a" ) ) )
        val g2 = Guard.OrGuard( 
                    Guard.NotGuard( Guard.NamedGuard( "a" ) ),
                    Guard.NotGuard( Guard.NamedGuard( "b" ) ) )
        assert( Satisfaction.entails( logger, g0, g0 ) )
        assert( Satisfaction.entails( logger, g1, g1 ) )
        assert( Satisfaction.entails( logger, g2, g2 ) )
        assert( ! Satisfaction.entails( logger, g0, g1 ) )
        assert( ! Satisfaction.entails( logger, g1, g0 ) )
        assert( Satisfaction.entails( logger, g0, g2 ) )
        assert( ! Satisfaction.entails( logger, g2, g0 ) )
        assert( Satisfaction.entails( logger, g1, g2 ) )
        assert( ! Satisfaction.entails( logger, g2, g1 ) )
    }

    it should "sort 'a and not b' 'b and not a' 'not a or not b'" in {
        val logger = new LoggerForTesting

        val si0 = StateInformation("x", 0, Stereotype.None )
        val si1 = StateInformation("y", 0, Stereotype.None )
        val n0 = Node.BasicState( si0 )
        val n1 = Node.BasicState( si1 )

        val g0 = Guard.AndGuard( 
                    Guard.NamedGuard( "a" ),
                    Guard.NotGuard( Guard.NamedGuard( "b" ) ) )
        val g1 = Guard.AndGuard( 
                    Guard.NamedGuard( "b" ),
                    Guard.NotGuard( Guard.NamedGuard( "a" ) ) )
        val g2 = Guard.OrGuard( 
                    Guard.NotGuard( Guard.NamedGuard( "a" ) ),
                    Guard.NotGuard( Guard.NamedGuard( "b" ) ) )

        val e0 = Edge(n0, n1, None, Some(g0), List())
        val e1 = Edge(n0, n1, None, Some(g1), List())
        val e2 = Edge(n0, n1, None, Some(g2), List())

        val seq0 = List(e0, e1, e2)
        var a : Array[Edge] = Satisfaction.sort_by_entailment( logger, seq0 ) 
        assert( a(0) == e0 )
        assert( a(1) == e1 )
        assert( a(2) == e2)

        val seq1 = List(e0, e2, e1)
        a = Satisfaction.sort_by_entailment( logger, seq1 ) 
        assert( a(0) == e0 )
        assert( a(1) == e1 )
        assert( a(2) == e2)

        val seq2 = List(e2, e0, e1)
        a = Satisfaction.sort_by_entailment( logger, seq1 ) 
        assert( a(0) == e0 )
        assert( a(1) == e1 )
        assert( a(2) == e2)
    }

    it should "find (a => b) => (not b => not a) is a tautology" in {
        val logger = new LoggerForTesting
        val g = Guard.ImpliesGuard(
                    Guard.ImpliesGuard(
                        Guard.NamedGuard( "a" ),
                        Guard.NamedGuard( "b" )),
                    Guard.ImpliesGuard(
                        Guard.NotGuard(Guard.NamedGuard( "b" )),
                        Guard.NotGuard(Guard.NamedGuard( "a" ))))
        assert( Satisfaction.is_tautology(logger, g) )
    }

end TestSat
