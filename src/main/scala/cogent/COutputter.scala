package cogent

import java.io.PrintWriter

class COutputter( override val writer : PrintWriter ) extends Outputter( writer ) :

    def block( contents : => Unit ) : Unit = block( contents, true )
    
    def blockNoNewLine ( contents : => Unit ) : Unit = block( contents, false )

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

    def switchComm( exhaustive : Boolean, expr : String )( cases : => Unit ) : Unit = {
        put( s"switch( $expr ) ")
        block{
            cases
            if exhaustive then
                put("default : { assertUnreachable() ; }")
            else
                put("default : { }")
            end if
        }
    }

    def caseComm( expr : String )( contents : => Unit ) : Unit = {
        put( s"case $expr : ")
        block( contents, false ) 
        put( " break ;" )
        endLine
    }

    def comment( text : String ): Unit = {
        put( s"/* $text */" )
    }
    
    def endLineComment( text : String ): Unit = {
        put( s"/* $text */" )
        endLine
    }

    def ifComm( expr : String  )( contents : => Unit ) : Unit = {
        put( s"if( $expr  ) " )
        block( contents, false )
    }

    def ifComm( expr : => Unit  )( contents : => Unit ) : Unit = {
        put( "if( " ) ; expr ; put( s" )" )
        block( contents, false )
    }
end COutputter
