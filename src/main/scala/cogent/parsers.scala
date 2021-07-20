package cogent 

import scala.util.parsing.combinator.RegexParsers

object parsers extends RegexParsers:

    def parseEdgeLabel( in : String )
    : ParseResult[ (Option[Trigger], Option[Guard], Seq[Action]) ] =
        parseAll( edgeLabelParser, in)
    end parseEdgeLabel

    def edgeLabelParser : Parser[(Option[Trigger], Option[Guard], Seq[Action] )] =
        (trigger ~ optGuard ~ actions) ^^ {case (a~b~c) => (a,b,c)}
    
    def trigger : Parser[ Option[Trigger]] =
        opt( keyword("after") ~> commit( "(" ~> duration <~ ")" ) ^^ (t => Trigger.AfterTrigger(t) )
           | ident ^^ ( name => Trigger.NamedTrigger( name ) ) )
    
    def duration : Parser[Double] =
        number ~ units ^^ {
            case (n~ "s") => n*1000 
            case (n ~ "ms") => n
        }
    
    def number : Parser[Double] =
        regex( """[0-9]+(\.[0-9]*)?""".r )
        .flatMap( str =>    try
                                val d = str.toDouble
                                success(d)
                            catch( e => 
                                err( s"Number format error on $str.") )
                )
    
    def units : Parser[String] =
      ( keyword("ms")
      | keyword("s")
      | err( "Unit of time must be 's' or 'ms'") )

    def optGuard : Parser[ Option[Guard] ] =
        opt( literal("[") ~> possiblyElseGuard <~ literal("]") )

    def possiblyElseGuard : Parser[ Guard ] =
        ( keyword("else") ^^ ( _ => Guard.ElseGuard() )
        | guard0 )

    def guard0 : Parser[Guard] =
        guard1.flatMap( moreGuard0 )

    def moreGuard0( gl : Guard ) : Parser[Guard] =
        ( (literal("==>") | keyword("implies") )
          ~> commit(guard0) ^^ (gr => Guard.ImpliesGuard( gl, gr))
        | success( gl ) )

    def guard1 : Parser[Guard] =
        guard2.flatMap( moreGuard1 ) 
    
    def moreGuard1( gl : Guard ) : Parser[Guard] = 
        (   (keyword("or") | literal("||"))
            ~> commit( guard1 ) ^^ ( gr => Guard.OrGuard(gl, gr) )
        |
            success( gl )
        )

    def guard2 : Parser[Guard] =
        primitiveGuard.flatMap( moreGuard2 ) 
    
    def moreGuard2( gl  : Guard ) : Parser[Guard] = 
        (   (keyword("and") | literal("&&"))
            ~> commit( guard2 ) ^^ ( gr => Guard.AndGuard(gl, gr) )
        |
            success( gl )
        )

    def primitiveGuard : Parser[ Guard ] =
        (
            keyword("not") ~> primitiveGuard ^^ (g => Guard.NotGuard(g))
        |   literal("!") ~> primitiveGuard ^^ (g => Guard.NotGuard(g))
        |   keyword("in") ~> ident  ^^ ( name => Guard.InGuard( name ) )
        |   ident ^^ ( name => Guard.NamedGuard( name ) )
        |   literal("{") ~> commit(cCode <~ literal("}")) ^^ (str => Guard.RawGuard(str))
        |   literal("(") ~> guard0 <~ literal(")") 
        |   err( "Expected guard" ) 
        )

    def actions : Parser[ Seq[Action] ] = 
        ( "/" ~> commit(rep1sep( action, opt(";") ))
        | success( Seq.empty[Action] ) )
    
    def action : Parser[ Action ] =
        (   ident ^^ (name => Action.NamedAction(name) )
        | literal("{") ~> commit(cCode <~ literal("}")) ^^ (str => Action.RawAction(str))
        | failure("expected action")
        )

    def ident : Parser[ String ] =
        regex("""[a-zA-Z_]\w*""".r)

    def keyword( kw : String) : Parser[ String ] =
        regex("""[a-zA-Z_]\w*""".r).filter( str => str.equals(kw))
    
    def cCode : Parser[String] =
        regex( """[^}]*""".r )
    
end parsers
