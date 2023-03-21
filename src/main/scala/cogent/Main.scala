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
        val logger : Logger = new LogToStdError( Info )
        val commit = gitCommit.gitCommit( )
        logger.log( Info, s"Cogent version $commit.")
        var argCounter = 0
        if args.length == 0 then
            printHelp( logger )
            return ()
        while argCounter < args.length
            && args(argCounter).startsWith("-")
        do
            if args(argCounter) == "--fatal" then
                logger.setLogLevel( Fatal )
            else if args(argCounter) == "--warning" then
                logger.setLogLevel( Warning )
            else if args(argCounter) == "--info" then
                logger.setLogLevel( Info )
            else if args(argCounter) == "--debug" then
                logger.setLogLevel( Debug )
            else if args(argCounter) == "--help" then
                printHelp(logger)
                return ()
            else
                logger.log( Fatal, s"Unrecognized option ${args(argCounter)}" )
                printHelp(logger)
                return ()
            end if
            argCounter += 1
        end while
        logger.log( Debug, s"args.length is ${args.length}")
        for i <- 0 until args.length do
            logger.log( Info, s"args($i) is ${args(i)}")
        val chartName : String = if( args != null && args.length > argCounter ) then args(argCounter) else "foo"
        val inFileName : String = if args != null && args.length > (argCounter+1) then args(argCounter+1) else chartName + ".puml"
        var outFileName : String = if args != null && args.length > (argCounter+2) then args(argCounter+2) else chartName + ".c"
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
        logger.info( "Parsing with PlantUML" ) 
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
        logger.info( "Parsing successful. Extracting statecharts." ) 
        val middleEnd = MiddleEnd( logger ) 
        val stateChartList = middleEnd.processBlocks( blocks, chartName )
        if ! logger.hasFatality then
            if stateChartList.size == 0 then
                logger.log( Fatal, "No statecharts to process")
            else
                // Step 2 Combine all the statecharts to make one big
                // statechart.
                logger.info( "Extraction successful. Expanding submachine references." ) 
                val combiner = Combiner( logger )
                val optStateChart = combiner.combine( stateChartList )
                if ! logger.hasFatality then
                    assert( ! optStateChart.isEmpty ) 

                    logger.info( "Expansion complete. Preparing for code generation.")

                    val stateChart = middleEnd.prepareForBackEnd( optStateChart.head )

                    logger.debug( "The prepared statechart is" )
                    logger.debug( stateChart.show )

                    // Step 3: Check that the StateChart is well formed.
                    logger.info( "Preparation complete. Checking for errors.")
                    val checker = Checker( logger )
                    checker.check( stateChart )
                    if ! logger.hasFatality then
                        // Step 4: Convert to a C file
                        logger.log( Info, "Checking complete. Code generation begins." )
                        val outFile = new File( outFileName )
                        import java.io.PrintWriter
                        val cout = COutputter( new PrintWriter( outFile ) )
                        val backend = Backend( logger, cout )
                        backend.generateCCode( stateChart, chartName ) 
                        logger.log( Info, "Code generation complete." )
    end main

    private def printHelp( logger : Logger ) : Unit = 
        logger.setLogLevel( Info )
        logger.info( "Usage: scala cogent.jar [options] chartName [inputFile [outputFile]]" )
        logger.info( "or   : java -cp cogent.jar [options] chartName [inputFile [outputFile]]" )
        logger.info( "    inputFile defaults to chartName.puml" )
        logger.info( "    outputFile defaults to chartName.c" )
        logger.info( "Options:" )
        logger.info( "    --fatal   - only fatal errors are reported" )
        logger.info( "    --warning - fatal and warning errors are reported" )
        logger.info( "    --info    - fatal, warning and info messages are reported. This is the default." )
        logger.info( "    --debug   - debug messages are also reported" )
        logger.info( "    --help    - print this message and exit")
        logger.info( "To generate png files use:")
        logger.info( "    java -cp cogent.jar net.sourceforge.plantuml.Run *.puml" )
    end printHelp
end Main // Object 