package cogent

object Logger :
    enum Level :
        case Fatal;
        case Warning;
        case Info;
        case Debug

class Logger( 
        var maxLevel : Logger.Level ) :

    var fatalCount = 0

    def log( level : Logger.Level,  message : String ) : Unit =
        if level.ordinal >= maxLevel.ordinal then
            println( s"[$level] $message")
        if level == Logger.Level.Fatal then
            fatalCount += 1