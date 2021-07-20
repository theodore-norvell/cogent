package cogent

enum Node derives CanEqual:
    case BasicState( stateInfo : StateInformation )
    case StartMarker( stateInfo : StateInformation )
    case BranchPseudoState( stateInfo : StateInformation )
    case OrState( stateInfo : StateInformation, children : Seq[Node] ) 
        var optStartingIndex : Option[Int] = None 
    case AndState( stateInfo : StateInformation, children : Seq[Node] )

    def show : String =

        def show( n : Node, indentLevel : Int ) : String =
            val indent1 = "|   "*(indentLevel) + "+--"
            val indent  = "|   "*(indentLevel) + "|  "
            n match 
                case BasicState( si ) => 
                    s"${indent1}Basic State\n${indent}${si.toString}\n"
                case BranchPseudoState( si ) =>
                    s"${indent1}Branch Pseudo-state\n${indent}${si.toString}\n"
                case StartMarker( si ) =>
                    s"${indent1}Start Pseudo-state\n${indent}${si.toString}\n"
                case OrState( si, children ) =>
                    val childrenString = children.map( child => show(child, indentLevel+1) )
                                                .fold("")( (x,y) => x+y )
                    val nameOfStartState = optStartingIndex match
                        case None => "not yet set"
                        case Some(i) => i.toString
                    ( s"${indent1}OR State\n${indent}${si.toString}\n${indent}first child is $nameOfStartState\n"
                    + childrenString )
                case AndState( si, children ) =>
                    val childrenString = children.map( child => show(child, indentLevel+1) )
                                                .fold("")( (x,y) => x+y )
                    ( s"${indent1}AND State\n${indent}${si.toString}\n"
                    + childrenString)
        end show
        show( this, 0 ) 
    end show

    def getName : String =
        this match
        case BasicState(si) => si.name 
        case BranchPseudoState(si) => si.name
        case StartMarker( si ) => si.name
        case OrState( si, children ) => si.name
        case AndState( si, children ) => si.name
    end getName
end Node


class StateInformation( val name : String, depth : Int, index : Int ) :
    var entryLabel : Option[String] = None
    var exitLabel : Option[String] = None
    var invLabel : Option[String] = None

    override def toString : String =
        s"name: $name depth: $depth index: $index"
end StateInformation