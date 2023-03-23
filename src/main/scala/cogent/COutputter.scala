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

    def elseStart : Unit = {
        put( "else " )
    }
    def ifComm( expr : => Unit  )( contents : => Unit ) : Unit = {
        put( "if( " ) ; expr ; put( s" )" )
        block( contents, false )
    }

    def stringify( str : String ) : String = {
        val sb = new StringBuilder()
        sb.append( "\"" )
        for c <- str do
            c match
                case '\n' => sb.append( "\\n" )
                case '\t' => sb.append( "\\t" )
                case '\r' => sb.append( "\\r" )
                case '\\' => sb.append( "\\\\" )
                case '"' => sb.append( "\\\"" )
                case _ =>
                    val codePoint = c.toInt
                    if 32 <= codePoint && codePoint < 127 then
                        sb.append( c )
                    else
                        // Perhaps not perfect but good enough
                        sb.append( "\\x" )
                        sb.append( codePoint.toHexString )
                    end if
            end match
        end for
        sb.append( "\"" )
        sb.toString
    }
end COutputter
