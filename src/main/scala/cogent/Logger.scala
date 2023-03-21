package cogent

import cogent.Logger.Level

object Logger :
    enum Level :
        case Never ;
        case Fatal;
        case Warning;
        case Info;
        case Debug

trait Logger :
    import Logger.Level._ 

    def setLogLevel( max : Level ) : Unit

    def hasFatality : Boolean

    def log( level : Logger.Level,  message : String ) : Unit 

    def fatal( message : String ) : Unit = log( Fatal, message )

    def warning( message : String ) : Unit = log( Warning, message )

    def info( message : String ) : Unit = log( Info, message )

    def debug( message : String ) : Unit = log( Debug, message )