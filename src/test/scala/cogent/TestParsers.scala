package cogent

import org.scalatest.flatspec.AnyFlatSpec

class TestParsers extends AnyFlatSpec {
    protected def result2Str[T]( result : cogent.parsers.ParseResult[T]) : String =
        result match 
            case cogent.parsers.Success( x, _ ) => x.toString
            case cogent.parsers.Failure( e, _ ) => result.toString
            case cogent.parsers.Error( e, _ ) => result.toString
    
    "the  label parser" should "parse an empty string" in {
        val in = "" ;
        val expected = "(None,None,List())"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result ) 
        assert( out == expected ) 
    }

    it should "parse string with only a simple trigger" in {
        val in = " AN_EVENT_CLASS_99 "
        val expected = "(Some(AN_EVENT_CLASS_99),None,List())"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result ) 
        assert( out == expected ) 
    }

    it should "parse a string with only duration trigger in ms" in {
        val in = "after( 100 ms)" ;
        val expected = "(Some(after(100.0 ms)),None,List())"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result ) 
        assert( out == expected ) 
    }

    it should "parse a string with only duration trigger in seconds" in {
        val in = "after( 99.12345s )" ;
        val expected = "(Some(after(99123.45000000001 ms)),None,List())"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result ) 
        assert( out == expected ) 
    }

    it should "not accept an empty guard" in {
        val in = "[]" 
        val expected = "[1.2] error: Expected guard\n\n[]\n ^"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result )
        assert( out == expected )
    }

    it should "parse a string with only a named guard" in {
        val in = "[xyz ]" ;
        val expected = "(None,Some([xyz]),List())"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result ) 
        assert( out == expected ) 
    }

    it should "parse a string with only a raw guard" in {
        val in = "[{a + b > 17*p[i]}]" ;
        val expected = "(None,Some([{a + b > 17*p[i]}]),List())"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result ) 
        assert( out == expected ) 
    }

    it should "parse a string with only an in guard" in {
        val in = "[in bob]" ;
        val expected = "(None,Some([in bob]),List())"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result ) 
        assert( out == expected ) 
    }

    it should "parse a string with an else guard" in {
        val in = "[else]" ;
        val expected = "(None,Some([else]),List())"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result ) 
        assert( out == expected )
    }

    it should "parse a string with a negated (!) guard" in {
        val in = "[! foo]" ;
        val expected = "(None,Some([not foo]),List())"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result )
        assert( out == expected )
    }

    it should "parse a string with a negated (not) guard" in {
        val in = "[not foo]" ;
        val expected = "(None,Some([not foo]),List())"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result )
        assert( out == expected )
    }

    it should "parse a string with anded (and) guard" in {
        val in = "[not foo and not {a < b}]" ;
        val expected = "(None,Some([not foo and not {a < b}]),List())"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result )
        assert( out == expected )
    }

    it should "parse a string with anded (&&) guard" in {
        val in = "[! foo && ! {a < b}]" ;
        val expected = "(None,Some([not foo and not {a < b}]),List())"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result )
        assert( out == expected )
    }

    it should "parse a string with or-ed (or) guard" in {
        val in = "[foo and bar or baz and boo]" ;
        val expected = "(None,Some([foo and bar or baz and boo]),List())"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result )
        assert( out == expected )
    }

    it should "parse a string with or-ed (||) guard" in {
        val in = "[foo && bar || baz && boo]" ;
        val expected = "(None,Some([foo and bar or baz and boo]),List())"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result )
        assert( out == expected )
    }

    it should "parse a string with an implication (==>) guard" in {
        val in = "[foo or bar ==> baz or boo]" ;
        val expected = "(None,Some([foo or bar ==> baz or boo]),List())"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result )
        assert( out == expected )
    }

    it should "parse a string with an implication (implies) guard" in {
        val in = "[foo or bar implies baz or boo]" ;
        val expected = "(None,Some([foo or bar ==> baz or boo]),List())"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result )
        assert( out == expected )
    }

    it should "right associate or" in {
        val in = "[foo or bar and baz or boo]" ;
        val expected = "(None,Some([foo or bar and baz or boo]),List())"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result )
        assert( out == expected )
    }

    it should "right associate implies" in {
        val in = "[foo implies bar or baz implies boo]" ;
        val expected = "(None,Some([foo ==> (bar or baz ==> boo)]),List())"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result )
        assert( out == expected )
    }

    it should "right associate and" in {
        val in = "[foo and (bar or baz) and boo]" ;
        val expected = "(None,Some([foo and (bar or baz) and boo]),List())"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result )
        assert( out == expected )
    }

    it should "not allow else as an operand" in {
        val in = "[not else]" ;
        val expected = "[1.6] error: Expected guard\n\n[not else]\n     ^"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result )
        assert( out == expected )
    }

    it should "not accept an empty sequence of actions" in {
        val in = "/" 
        val expected = "[1.2] error: expected action\n\n/\n ^"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result )
        assert( out == expected )
    }

    it should "accept a named action" in {
        val in = "/ go " 
        val expected = "(None,None,List(go))"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result )
        assert( out == expected )
    }

    it should "accept a raw action" in {
        val in = "/ {foo() ; } " 
        val expected = "(None,None,List({foo() ; }))"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result )
        assert( out == expected )
    }

    it should "accept a sequence of three actions no semicolons" in {
        val in = "/ {foo() ; } bar baz" 
        val expected = "(None,None,List({foo() ; }, bar, baz))"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result )
        assert( out == expected )
    }

    it should "accept a sequence of three actions with semicolons" in {
        val in = "/ {foo() ; };bar;baz" 
        val expected = "(None,None,List({foo() ; }, bar, baz))"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result )
        assert( out == expected )
    }

    it should "not accept extra semicolons 0" in {
        val in = "/ {foo() ; } bar baz;"
        val expected = "[1.22] failure: expected action\n\n/ {foo() ; } bar baz;\n                     ^"
        val result = parsers.parseEdgeLabel( in )
        val out = result2Str( result )
        assert( out == expected )
    }
}