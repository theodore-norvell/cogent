package cogent

case class Edge(
    val source : Node,
    val target : Node,
    val triggerOpt : Option[Trigger],
    val guardOpt : Option[Guard],
    val actions : Seq[Action]
) :
    override def toString : String =
        s"${source.getFullName} ----"
        + triggerOpt.getOrElse("--")
        + guardOpt.map( (x) => s"[$x]").getOrElse("--")
        + actions.fold("")( (x,y) => s"$x--$y")
        + s"--->${target.getFullName}"
end Edge


enum Trigger :
    case AfterTrigger( durationInMilliseconds : Double )
    case NamedTrigger( name : String )

    def asAfterTrigger : Option[AfterTrigger] =
        this match
            case x@AfterTrigger( _ ) => Some(x)
            case NamedTrigger( _ ) => None

    def asNamedTrigger : Option[NamedTrigger] =
        this match
            case AfterTrigger( _ ) => None
            case x@NamedTrigger( _ ) => Some(x)
end Trigger

enum Guard :
    case ElseGuard() // ElseGuards must not be operands of other guards.
    case OKGuard( )
    case InGuard( name : String )
    case NamedGuard( name : String )
    case RawGuard( rawCCode : String )
    case NotGuard( operand : Guard )
    case AndGuard( left : Guard, right : Guard )
    case OrGuard( left: Guard, right : Guard )
    case ImpliesGuard( left : Guard, right : Guard )
end Guard

enum Action :
    case NamedAction( name : String )
    case RawAction( rawCCode : String )
end Action