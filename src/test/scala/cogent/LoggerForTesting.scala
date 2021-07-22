package cogent

class LoggerForTesting
    extends Logger :
    import Logger.Level

    var fatalCount = 0
    var warnCount = 0

    def hasFatality = fatalCount > 0

    def log( level : Logger.Level,  message : String ) : Unit =
        level match
            case Level.Fatal => fatalCount += 1
            case Level.Warning => warnCount += 1
            case _ => ()