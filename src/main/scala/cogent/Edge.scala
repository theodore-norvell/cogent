package cogent

class Edge(
    val source : Node,
    val target : Node,
    val eventNameOpt : Option[String],
    val guardOpt : Option[String],
    val actions : Seq[String]
) :
    override def toString : String =
        s"${source.getName} ---- "
        + eventNameOpt.getOrElse("-")
        + guardOpt.map( (x) => s"[$x]").getOrElse("--")
        + actions.fold("")( (x,y) => s"$x; $y")
        + s"--->${target.getName}"
end Edge