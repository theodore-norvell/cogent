package cogent

import java.io.PrintWriter

class COutputter( override val writer : PrintWriter ) extends Outputter( writer ) :

    def block( contents : => Unit ) : Unit = block( contents, true )

    def block( contents : => Unit, endLastLine : Boolean ) : Unit = {
        put("{")
        endLine
        indent
        contents
        dedent
        endLine
        put("}")
        if endLastLine then endLine
        end if
    }
    def switchComm( expr : String )( contents : => Unit ) : Unit = {
        put( s"switch( $expr )")
        block( contents )
    }
    def caseComm( expr : String )( contents : => Unit ) : Unit = {
        put( s"case $expr  : ")
        block( contents, false ) 
        put( " break ;" )
        endLine
    }

    def comment( text : String ): Unit = {
        put( s"/* $text */" )
        endLine
    }
end COutputter
