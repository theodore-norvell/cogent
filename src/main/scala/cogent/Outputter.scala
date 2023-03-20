package cogent

import java.io.PrintWriter
import scala.collection.mutable

class Outputter( val writer : PrintWriter ) :

    protected val lines = mutable.ArrayBuffer[String]()
    protected var currentLine = mutable.StringBuilder()
    protected var atStartOfLine = true
    protected var indentation = 0
    
    def putLine( str : String ) : Unit = {
        put( str )
        endLine
    }

    def put( str :  String ) : Unit = {
        if atStartOfLine then
            currentLine.append("    "*indentation)
            atStartOfLine = false
        end if
        currentLine.append( str )
    }

    def endLine : Unit = {
        if ! atStartOfLine then
            lines += currentLine.toString
            writer.println( currentLine.toString )
            writer.flush()
            currentLine = mutable.StringBuilder()
            atStartOfLine = true
        end if
    }

    def blankLine : Unit = {
        endLine
        put("")
        endLine
    }

    def indent : Unit = indentation += 1

    def dedent : Unit = {assert( indentation > 0 ) ; indentation -= 1 ; }

    def indented( contents : => Unit) : Unit = {
        indent
        contents
        dedent
    }
end Outputter
