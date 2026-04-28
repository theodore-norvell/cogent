package cogent

case class Edge(
    val source : Node,
    val target : Node,
    val triggerOpt : Option[Trigger],
    val guardOpt : Option[Guard],
    val actions : Seq[Action]
) :
    override def toString : String =
        s"${source.getFullName} --"
        + triggerOpt.getOrElse("--")
        + guardOpt.map( (x) => x.toString()).getOrElse("--")
        + actions.fold("")( (x,y) => s"$x;$y")
        + s"->${target.getFullName}"
end Edge


enum Trigger :
    case AfterTrigger( durationInMilliseconds : Double )
    case NamedTrigger( name : String )

    override def toString() : String =
        this match
            case AfterTrigger( durationInMilliseconds ) => s"after($durationInMilliseconds ms)"
            case NamedTrigger( name ) => name

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

    def toString(p : Int) : String =
        this match {
            case ElseGuard() => "else"
            case OKGuard( ) => "OK"
            case InGuard( name : String ) => s"in $name"
            case NamedGuard( name : String ) => name
            case RawGuard( rawCCode : String ) => s"{$rawCCode}"
            case NotGuard( operand : Guard ) => s"not ${operand.toString(4)}"
            case AndGuard( left : Guard, right : Guard ) =>
                val s = s"${left.toString(3)} and ${right.toString(3)}"
                if p <= 3 then s else s"($s)"
            case OrGuard( left: Guard, right : Guard ) =>
                val s = s"${left.toString(2)} or ${right.toString(2)}"
                if p <= 2 then s else s"($s)"
            case ImpliesGuard( left : Guard, right : Guard ) =>
                val s = s"${left.toString(2)} ==> ${right.toString(2)}"
                if p <= 1 then s else s"($s)"
        }

    override def toString() = "[" + this.toString(0) +"]"
end Guard

enum Action :
    case NamedAction( name : String )
    case RawAction( rawCCode : String )

    override def toString() =
        this match 
            case NamedAction(name) => name
            case RawAction(rawCCode) => s"{$rawCCode}"
end Action