package cogent

import java.io.PrintWriter
import scala.collection.mutable

class Outputter( val writer : PrintWriter ) :

    protected val lines = mutable.ArrayBuffer[String]()
    protected var currentLine = mutable.StringBuilder()
    protected var atStartOfLine = true
    protected var indentation = 0
    
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

    def indent : Unit = indentation += 1

    def dedent : Unit = {assert( indentation > 0 ) ; indentation -= 1 ; }
end Outputter
