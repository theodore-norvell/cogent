package cogent

import scala.collection.mutable ;
import java.awt.Taskbar.State

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
    
    def isEntryPseudostate : Boolean = 
        this match 
            case BasicState( _ ) => false
            case OrState( _, _ ) => false
            case AndState( _, _ ) => false
            case StartMarker( _ ) => false
            case ChoicePseudoState( _ ) => false
            case EntryPointPseudoState( _ ) => true
            case ExitPointPseudoState( _ ) => false
    end isEntryPseudostate
    
    def isExitPseudostate : Boolean = 
        this match 
            case BasicState( _ ) => false
            case OrState( _, _ ) => false
            case AndState( _, _ ) => false
            case StartMarker( _ ) => false
            case ChoicePseudoState( _ ) => false
            case EntryPointPseudoState( _ ) => false
            case ExitPointPseudoState( _ ) => true
    end isExitPseudostate
    
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
    
    def computeParentMap( parentMap : mutable.Map[Node,Node] ) : Unit = 
        def dealWithChild( child : Node ) : Unit = {
            parentMap(child) = this
            child.computeParentMap( parentMap )
        }
        this match 
            case BasicState( _ ) => ()
            case x @ OrState( _, _ ) => x.children.map( dealWithChild( _ ) )
            case x @ AndState( _, _ ) => x.children.map( dealWithChild( _ ) )
            case StartMarker( _ ) => ()
            case ChoicePseudoState( _ ) => ()
            case EntryPointPseudoState( _ ) => ()
            case ExitPointPseudoState( _ ) => ()
        ()
    end computeParentMap
    
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
    
    def childNodes : Seq[Node] = 
        this match 
            case BasicState( _ ) => Seq[Node]()
            case x @ OrState( _, _ ) => x.children
            case x @ AndState( _, _ ) => x.children
            case StartMarker( _ ) => Seq[Node]()
            case ChoicePseudoState( _ ) => Seq[Node]()
            case EntryPointPseudoState( _ ) => Seq[Node]() 
            case ExitPointPseudoState( _ ) => Seq[Node]()
    end childNodes
    
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

    def hasInitialState : Boolean = stateInfo.hasInitialState

    def getInitialState : Node = stateInfo.getInitialState

    def getStereotype : Stereotype = stateInfo.stereotype

    def setInitialState( initState : Node ) : Unit =
        assert( ! hasInitialState )
        stateInfo.setInitialState( initState )
end Node

enum Stereotype {
    case Submachine 
    case EntryPoint
    case ExitPoint
    case Choice
    case None
}

class StateInformation( val fullName : String, val depth : Int, val stereotype : Stereotype ) :
    //var entryLabel : Option[String] = None
    //var exitLabel : Option[String] = None
    //var invLabel : Option[String] = None
    private var localIndex : Option[Int] = None
    private var globalIndex : Option[Int] = None
    private var cName : Option[String] = None
    private var initialState : Option[Node] = None


    def copy( newFullName : String, newDepth : Int, newStereotype : Stereotype ) = {
        val result = StateInformation( newFullName, newDepth, newStereotype ) 
        //entryLabel.map( x => result.setEntryLabel( x ) )
        //exitLabel.map( x => result.setExitLabel( x ) )
        //invLabel.map( x => result.setInvLabel( x ) )
        localIndex.map( x => result.setLocalIndex( x ) )
        globalIndex.map( x => result.setGlobalIndex( x ) )
        cName.map( x => result.setCName( x ) )
        initialState.map( x => result.setInitialState( x ) )
        result
    }

    def copy( ) : StateInformation =
        copy( fullName, depth, stereotype )

    def withFullName( newName : String ) : StateInformation =
        copy( newName, depth, stereotype ) 

    def withStereotype( newStereotype : Stereotype ) : StateInformation =
        copy( fullName, depth, newStereotype ) 

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

    def hasInitialState = initialState.nonEmpty

    def getInitialState = initialState.head

    def setInitialState( state : Node ) : Unit = { initialState = Some(state) }
end StateInformation