package cogent

object RunWithPresetArgs :
    def main( args : Array[String] ) : Unit =
        val presetArgs = Array[String]( "", "supermachine")
        cogent.Main.main( presetArgs )