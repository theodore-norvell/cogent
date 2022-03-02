package cogent

class LogToStdError( var maxLevel : Logger.Level ) 
    extends Logger :
    import Logger.Level

    var fatalCount = 0

    val WARNCOLOR = "\u001b[93m"
    val FATALCOLOR = "\u001b[91m"
    val ENDCOLOR = "\u001b[0m"

    def hasFatality = fatalCount > 0

    def log( level : Logger.Level,  message : String ) : Unit =
        var color = ""
        var endColor = ""
        level match 
            case Level.Fatal => {color = FATALCOLOR ; endColor = ENDCOLOR ; }
            case Level.Warning => {color = WARNCOLOR ; endColor = ENDCOLOR ; }
            case _ => {}
        if level.ordinal <= maxLevel.ordinal then
            System.err.println( s"$color[$level] $message$endColor") 
        if level == Logger.Level.Fatal then
            fatalCount += 1