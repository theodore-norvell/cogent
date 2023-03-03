package cogent

import java.io.File 
import java.io.IOException
import net.sourceforge.plantuml.SourceFileReader
import net.sourceforge.plantuml.BlockUml
import net.sourceforge.plantuml.core.Diagram
import scala.jdk.CollectionConverters._

import Logger.Level._


object Main :
    def main( args : Array[String] ) : Unit =
        val logger = new LogToStdError( Logger.Level.Info )
        val commit = gitCommit.gitCommit( )
        logger.log( Info, s"Cogent version $commit.")
        println( s"args.length is ${args.length}")
        for i <- 0 until args.length do
            println( s"args($i) is ${args(i)}")
        val chartName : String = if( args != null && args.length > 0 ) args(0) else "foo"
        val inFileName : String = if args != null && args.length > 1 then args(1) else chartName + ".puml"
        var outFileName : String = if args != null && args.length > 2 then args(2) else chartName + ".c"
        logger.log( Info, s"Chart name:   ${chartName}" )
        logger.log( Info, s"Source file:  ${inFileName}" )
        logger.log( Info, s"Target file:  ${outFileName}" )
        val f : File = new File( inFileName )
        if ! f.exists() then
            logger.log( Fatal, s"Input file ${f} does not exist.")
            return ()

        val sfr : SourceFileReader = 
            try
                new SourceFileReader( f )
            catch (e : Throwable) =>
                    logger.log( Fatal, s"Exception making SourceFileReader ${e.getMessage()} ${e}" )
                    return ()

        // Step 0. Parse
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
        
        // Step 1: Create a list of StateChart objects
        val blocks = blockList.asScala
        val middleEnd = MiddleEnd( logger ) 
        val stateChartList = middleEnd.processBlocks( blocks )
        if ! logger.hasFatality then
            if stateChartList.size == 0 then
                logger.log( Fatal, "No statecharts to process")
            else if stateChartList.size > 1 then
                logger.log( Fatal, "Sorry, only one statechart can be handled right now." )
            else
                // Step 2: Check that the StateChart is well formed.
                val stateChart = stateChartList.head
                val checker = Checker( logger )
                checker.check( stateChart )
                if ! logger.hasFatality then
                    // Step 3: Convert to a C file
                    logger.log( Info, "Ready for code generation" )
                    val outFile = new File( outFileName )
                    import java.io.PrintWriter
                    val cout = COutputter( new PrintWriter( outFile ) )
                    val backend = Backend( logger, cout )
                    backend.generateCCode( stateChart, chartName ) 
    end main
end Main