package cogent

import scala.quoted.*
import sys.process._

object gitCommit {

    inline def gitCommit() = ${ gitCommitImpl() }

    def gitCommitImpl()(using q: Quotes) : Expr[String]= {
        try {
            val dateStr = java.time.ZonedDateTime.now.toString() 
            var commit = "git rev-parse HEAD".!!
            commit = commit.take(Math.max(commit.size, 8))
            commit = ("commit " +commit+ " compiled at " +dateStr)
            println( commit )
            Expr(commit)
        } catch {
            case exn: Throwable =>
                println("commit is not available")
                '{"commit is not available"}
        }
    }
  
}
