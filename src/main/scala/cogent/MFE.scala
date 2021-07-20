import scala.util.parsing.combinator.RegexParsers

object p extends RegexParsers:
    def meth() : Int = {
        val result : ParseResult[Int]
                = parseAll( err("XYZ"), "")
        result match {
            case Success( x, _ ) =>  1
            case _ => 2
        }
    }
end p

def MFE() : Unit = {
    println( p.meth() ) 

    sealed abstract class A { val y : Int } ;
    case class B( val x : Int, override val y : Int ) extends A
    case class C( override val y : Int ) extends A 

    val x : A = B( 12, 34 ) ;
    x match {
        case B( x, _ ) => println( x )
        case C( y ) => println( "no" )
    }
}


