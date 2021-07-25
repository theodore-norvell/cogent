package cogent

import java.io.File 
import java.io.IOException
import net.sourceforge.plantuml.SourceFileReader
import net.sourceforge.plantuml.BlockUml
import net.sourceforge.plantuml.core.Diagram
import scala.jdk.CollectionConverters._

import Logger.Level._

@main def main : Unit =
    val logger = new LogToStdOut( Logger.Level.Debug )
    val f : File = new File("foo.puml")
    if ! f.exists() then
        logger.log( Fatal, s"File ${f} does not exist.")
        return ()

    val sfr : SourceFileReader = 
        try
            new SourceFileReader( f )
        catch (e : Throwable) =>
                logger.log( Fatal, s"Exception making SourceFileReader ${e.getMessage()} ${e}" )
                return ()
    var blockList : java.util.List[BlockUml] =
        try
            sfr.getBlocks()
        catch 
            case (e : IOException) =>
                logger.log( Fatal, s"IOException getting blocklist ${e.getMessage()} ${e}" )
                return ()
            case (e : Throwable) =>
                logger.log( Fatal, s"Exception getting blocklist ${e.getMessage()} ${e}" )
                return ()
    
    val blocks = blockList.asScala
    val middleEnd = MiddleEnd( logger ) 
    val stateChartList = middleEnd.processBlocks( blocks )
    if ! logger.hasFatality then
        if stateChartList.size == 0 then
            logger.log( Fatal, "No statecharts to process")
        else if stateChartList.size > 1 then
            logger.log( Fatal, "Sorry, only one statechart can be handled right now." )
        else
            val stateChart = stateChartList.head
            val checker = Checker( logger )
            checker.check( stateChart )
            if ! logger.hasFatality then
                logger.log( Info, "Ready for code generation" )
                import java.io.PrintWriter
                val cout = COutputter( new PrintWriter( Console.out) )
                val backend = Backend( logger, cout )
                backend.generateCCode( stateChart ) 
end main