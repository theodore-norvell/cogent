package cogent

enum Node derives CanEqual :
    val stateInfo : StateInformation 

    case BasicState( override val stateInfo : StateInformation )

    case OrState( override val stateInfo : StateInformation, children : Seq[Node] ) 
        var optStartingIndex : Option[Int] = None

    case AndState( override val stateInfo : StateInformation, children : Seq[Node] ) 

    case StartMarker( override val stateInfo : StateInformation )
    
    case ChoicePseudoState( override val stateInfo : StateInformation )

    def isState : Boolean = 
        this match 
            case BasicState( _ ) => true
            case OrState( _, _ ) => true
            case AndState( _, _ ) => true
            case StartMarker( _ ) => false
            case ChoicePseudoState( _ ) => false
    end isState
    
    def isStartNode : Boolean = 
        this match 
            case BasicState( _ ) => false
            case OrState( _, _ ) => false
            case AndState( _, _ ) => false
            case StartMarker( _ ) => true
            case ChoicePseudoState( _ ) => false
    end isStartNode
    
    def asOrState : Option[OrState] = 
        this match 
            case BasicState( _ ) => None
            case x @ OrState( _, _ ) => Some( x )
            case AndState( _, _ ) => None
            case StartMarker( _ ) => None
            case ChoicePseudoState( _ ) => None
    end asOrState
    
    def childStates : Seq[Node] = 
        this match 
            case BasicState( _ ) => Seq[Node]()
            case x @ OrState( _, _ ) => x.children.filter( _.isState )
            case x @ AndState( _, _ ) => x.children.filter( _.isState )
            case StartMarker( _ ) => Seq[Node]()
            case ChoicePseudoState( _ ) => Seq[Node]()
    end childStates
    
    def rank : Int = 
        this match 
            case BasicState( _ ) => 0
            case OrState( _, _ ) => 1
            case AndState( _, _ ) => 2
            case StartMarker( _ ) => 3
            case ChoicePseudoState( _ ) => 4
    end rank

    def show : String =

        def show( n : Node, indentLevel : Int ) : String =
            val indent1 = "|   "*(indentLevel) + "+--"
            val indent  = "|   "*(indentLevel) + "|  "
            n match 
                case BasicState( si ) => 
                    s"${indent1}Basic State\n${indent}${si.toString}\n"

                case ChoicePseudoState( si ) =>
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
        case ChoicePseudoState(si) => si.name
        case StartMarker( si ) => si.name
        case OrState( si, children ) => si.name
        case AndState( si, children ) => si.name
    end getName

    def getLocalIndex = stateInfo.getLocalIndex

    def setLocalIndex( i : Int ) : Unit =
        stateInfo.setLocalIndex(i)

    def getGlobalIndex = stateInfo.getGlobalIndex
    
    def setGlobalIndex( gi : Int ) : Unit = 
        stateInfo.setGlobalIndex(gi)

end Node


class StateInformation( val name : String, val depth : Int ) :
    var entryLabel : Option[String] = None
    var exitLabel : Option[String] = None
    var invLabel : Option[String] = None
    var localIndex : Option[Int] = None
    var globalIndex : Option[Int] = None

    override def toString : String =
        s"name: $name depth: $depth local index: $localIndex global $globalIndex"

    def getLocalIndex = localIndex.head

    def setLocalIndex( i : Int ) : Unit = {localIndex = Some(i) ;}

    def getGlobalIndex = globalIndex.head

    def setGlobalIndex( gi : Int ) : Unit = {globalIndex = Some(gi) ;}
end StateInformation