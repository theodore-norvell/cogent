package cogent 

import scala.util.parsing.combinator.RegexParsers

object parsers extends RegexParsers:

    def parseEdgeLabel( in : String )
    : ParseResult[ (Option[Trigger], Option[Guard], Seq[Action]) ] =
        parseAll( edgeLabelParser, in)
    end parseEdgeLabel

    private def edgeLabelParser : Parser[(Option[Trigger], Option[Guard], Seq[Action] )] =
        (trigger ~ optGuard ~ actions) ^^ {case (a~b~c) => (a,b,c)}
    
    private def trigger : Parser[ Option[Trigger]] =
        opt( keyword("after") ~>! "(" ~> duration <~ ")" ^^ (t => Trigger.AfterTrigger(t) )
           | triggerIdent ^^ ( name => Trigger.NamedTrigger( name ) ) )
    
    private def duration : Parser[Double] =
        number ~ units ^^ {
            case (n ~ "s") => n*1000 
            case (n ~ "ms") => n
        }
    
    private def number : Parser[Double] =
        regex( """[0-9]+(\.[0-9]*)?""".r )
        .flatMap( str =>    try
                                val d = str.toDouble
                                success(d)
                            catch( e => 
                                err( s"Number format error on $str.") )
                )
    
    private def units : Parser[String] =
      ( keyword("ms")
      | keyword("s")
      | err( "Unit of time must be 's' or 'ms'") )

    private def optGuard : Parser[ Option[Guard] ] =
        opt( literal("[") ~> possiblyElseGuard <~ literal("]") )

    private def possiblyElseGuard : Parser[ Guard ] =
        ( keyword("else") ^^ ( _ => Guard.ElseGuard() )
        | guard0 )

    private def guard0 : Parser[Guard] =
        guard1.flatMap( moreGuard0 )

    private def moreGuard0( gl : Guard ) : Parser[Guard] =
        ( (literal("==>") | keyword("implies") ) ~>! guard0 ^^ (gr => Guard.ImpliesGuard( gl, gr))
        | success( gl )
        )

    private def guard1 : Parser[Guard] =
        guard2.flatMap( moreGuard1 ) 
    
    private def moreGuard1( gl : Guard ) : Parser[Guard] = 
        (   (keyword("or") | literal("||")) ~>! guard1 ^^ ( gr => Guard.OrGuard(gl, gr) )
        |   success( gl )
        )

    private def guard2 : Parser[Guard] =
        primitiveGuard.flatMap( moreGuard2 ) 
    
    private def moreGuard2( gl  : Guard ) : Parser[Guard] = 
        (   (keyword("and") | literal("&&")) ~>! guard2 ^^ ( gr => Guard.AndGuard(gl, gr) )
        |   success( gl )
        )

    private def primitiveGuard : Parser[ Guard ] =
        (
            keyword("not") ~>! primitiveGuard ^^ (g => Guard.NotGuard(g))
        |   literal("!") ~>! primitiveGuard ^^ (g => Guard.NotGuard(g))
        |   keyword("OK") ^^ ( name => Guard.OKGuard( ) )
        |   keyword("in") ~>! stateIdent  ^^ ( name => Guard.InGuard( name ) )
        |   guardIdent ^^ ( name => Guard.NamedGuard( name ) )
        |   literal("{") ~>! cCode <~ literal("}") ^^ (str => Guard.RawGuard(str))
        |   literal("(") ~>! guard0 <~ literal(")") 
        |   err( "Expected guard" ) 
        )

    private def actions : Parser[ Seq[Action] ] = 
        ( "/" ~>! rep1sep( action, opt(";") )
        | success( Seq.empty[Action] )
        )
    
    private def action : Parser[ Action ] =
        (   actionIdent ^^ (name => Action.NamedAction(name) )
        | literal("{") ~>! cCode <~ literal("}") ^^ (str => Action.RawAction(str))
        | failure("expected action") // Needs to be failure rather than err because action is used in rep1sep
        )

    private def triggerIdent : Parser[ String ] =
        ident("?") ^^ sub("?", "RECV")
    
    private def guardIdent : Parser[ String ] =
        ident("?") ^^ sub("?", "query") 
    
    private def actionIdent : Parser[ String ] =
        ident("?!") ^^ (sub("?", "recv") compose sub("!", "send"))
    
    private def stateIdent : Parser[ String ] =
        ident("")

    private def ident(others:String) : Parser[ String ] =
        regex( ("[a-zA-Z_"+others+"][a-zA-Z_0-9"+others+"]*").r )

    private def keyword( kw : String) : Parser[ String ] =
        regex("""[a-zA-Z_]\w*""".r).filter( str => str.equals(kw))
    
    private def cCode : Parser[String] =
        regex( """[^}]*""".r )
    
    private def sub( pat : String, repl : String) : String => String = 
        (target : String) => {
            if target == pat then
                repl
            else
                var result = target 
                // Replace at start 
                result = (s"^\\Q${pat}\\E[_]?").r.replaceFirstIn( result, repl+"_" )
                // Replace at end
                result = (s"[_]?\\Q${pat}\\E"+"$").r.replaceFirstIn( result, "_"+repl )
                // Replace everywhere else
                result = (s"[_]?\\Q${pat}\\E[_]?").r.replaceAllIn( result, "_"+repl+"_" )
                result
        }

    
end parsers
