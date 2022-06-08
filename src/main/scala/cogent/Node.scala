package cogent

enum Node derives CanEqual :
    val stateInfo : StateInformation 

    case BasicState( override val stateInfo : StateInformation )

    case OrState( override val stateInfo : StateInformation, children : Seq[Node] )

    case AndState( override val stateInfo : StateInformation, children : Seq[Node] ) 

    case StartMarker( override val stateInfo : StateInformation )
    
    case ChoicePseudoState( override val stateInfo : StateInformation )

    case EntryPointPseudoState( override val stateInfo : StateInformation )

    case ExitPointPseudoState( override val stateInfo : StateInformation )

    def isState : Boolean = 
        this match 
            case BasicState( _ ) => true
            case OrState( _, _ ) => true
            case AndState( _, _ ) => true
            case StartMarker( _ ) => false
            case ChoicePseudoState( _ ) => false
            case EntryPointPseudoState( _ ) => false
            case ExitPointPseudoState( _ ) => false
    end isState
    
    def isStartMarker : Boolean = 
        this match 
            case BasicState( _ ) => false
            case OrState( _, _ ) => false
            case AndState( _, _ ) => false
            case StartMarker( _ ) => true
            case ChoicePseudoState( _ ) => false
            case EntryPointPseudoState( _ ) => false
            case ExitPointPseudoState( _ ) => false
    end isStartMarker
    
    def isChoicePseudostate : Boolean = 
        this match 
            case BasicState( _ ) => false
            case OrState( _, _ ) => false
            case AndState( _, _ ) => false
            case StartMarker( _ ) => false
            case ChoicePseudoState( _ ) => true
            case EntryPointPseudoState( _ ) => false
            case ExitPointPseudoState( _ ) => false
    end isChoicePseudostate
    
    def isBasicState : Boolean = 
        this match 
            case BasicState( _ ) => true
            case OrState( _, _ ) => false
            case AndState( _, _ ) => false
            case StartMarker( _ ) => false
            case ChoicePseudoState( _ ) => false
            case EntryPointPseudoState( _ ) => false
            case ExitPointPseudoState( _ ) => false
    end isBasicState
    
    def isOrState : Boolean = ! asOrState.isEmpty
    
    def asOrState : Option[OrState] = 
        this match 
            case BasicState( _ ) => None
            case x @ OrState( _, _ ) => Some( x )
            case AndState( _, _ ) => None
            case StartMarker( _ ) => None
            case ChoicePseudoState( _ ) => None
            case EntryPointPseudoState( _ ) => None
            case ExitPointPseudoState( _ ) => None
    end asOrState
    
    def childStates : Seq[Node] = 
        this match 
            case BasicState( _ ) => Seq[Node]()
            case x @ OrState( _, _ ) => x.children.filter( _.isState )
            case x @ AndState( _, _ ) => x.children.filter( _.isState )
            case StartMarker( _ ) => Seq[Node]()
            case ChoicePseudoState( _ ) => Seq[Node]()
            case EntryPointPseudoState( _ ) => Seq[Node]() 
            case ExitPointPseudoState( _ ) => Seq[Node]()
    end childStates
    
    // Rank is used for sorting both for global indexes and for local indexes.
    def rank : Int = 
        this match 
            case OrState( _, _ ) => 0
            case BasicState( _ ) => 1
            case AndState( _, _ ) => 1
            case StartMarker( _ ) => 3
            case ChoicePseudoState( _ ) => 2
            case EntryPointPseudoState( _ ) => 2
            case ExitPointPseudoState( _ ) => 2
    end rank

    def show : String =

        def show( n : Node, indentLevel : Int ) : String =
            val indent1 = "|   "*(indentLevel) + "+--"
            val indent  = "|   "*(indentLevel) + "|  "
            n match 
                case BasicState( si ) => 
                    s"${indent1}Basic State\n${indent}${si.toString}\n"

                case ChoicePseudoState( si ) =>
                    s"${indent1}Choice Pseudo-state\n${indent}${si.toString}\n"

                case StartMarker( si ) =>
                    s"${indent1}Start Pseudo-state\n${indent}${si.toString}\n"

                case OrState( si, children ) =>
                    val childrenString = children.map( child => show(child, indentLevel+1) )
                                                .fold("")( (x,y) => x+y )
                    ( s"${indent1}OR State\n${indent}${si.toString}\n"
                    + childrenString )

                case AndState( si, children ) =>
                    val childrenString = children.map( child => show(child, indentLevel+1) )
                                                .fold("")( (x,y) => x+y )
                    ( s"${indent1}AND State\n${indent}${si.toString}\n"
                    + childrenString)

                case EntryPointPseudoState( si ) => 
                    s"${indent1}Entry Point\n${indent}${si.toString}\n"

                case ExitPointPseudoState( si ) => 
                    s"${indent1}Exit Point\n${indent}${si.toString}\n"

        end show
        show( this, 0 ) 
    end show

    def getFullName : String = stateInfo.fullName

    def getCName = stateInfo.getCName
    
    def setCName( name : String ) : Unit = 
        stateInfo.setCName(name)

    def getLocalIndex = stateInfo.getLocalIndex

    def setLocalIndex( i : Int ) : Unit =
        stateInfo.setLocalIndex(i)

    def getGlobalIndex = stateInfo.getGlobalIndex
    
    def setGlobalIndex( gi : Int ) : Unit = 
        stateInfo.setGlobalIndex(gi)

    def getDepth : Int = stateInfo.depth

    def getInitialState : Option[Node] = stateInfo.initialState

    def setInitialState( initState : Node ) : Unit =
        assert( stateInfo.initialState.isEmpty )
        stateInfo.initialState = Some(initState)

end Node

enum Stereotype {
    case Submachine 
    case None
}

class StateInformation( val fullName : String, val depth : Int, val stereotype : Stereotype ) :
    var entryLabel : Option[String] = None
    var exitLabel : Option[String] = None
    var invLabel : Option[String] = None
    var localIndex : Option[Int] = None
    var globalIndex : Option[Int] = None
    var cName : Option[String] = None
    var initialState : Option[Node] = None


    override def toString : String =
        val initialStateName =  if initialState.isEmpty then ""
                                else s" initialState: ${initialState.head.getFullName}"
        s"name: $fullName depth: $depth stereotype: $stereotype local index: $localIndex global index: $globalIndex $initialStateName"

    def getCName = cName.head

    def setCName( name : String ) : Unit = {cName = Some(name) ;}

    def getLocalIndex = localIndex.head

    def setLocalIndex( i : Int ) : Unit = {localIndex = Some(i) ;}

    def getGlobalIndex = globalIndex.head

    def setGlobalIndex( gi : Int ) : Unit = {globalIndex = Some(gi) ;}
end StateInformation