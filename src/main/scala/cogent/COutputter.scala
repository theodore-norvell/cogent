package cogent

import java.io.PrintWriter

class COutputter( override val writer : PrintWriter ) extends Outputter( writer ) :

    def block( contents : => Unit ) : Unit = {
        put("{")
        endLine
        indent
        contents
        dedent
        endLine
        put("}")
        endLine
    }
    def switchComm( expr : String )( contents : => Unit ) : Unit = {
        put( s"switch( $expr )")
        block( contents )
    }
end COutputter
