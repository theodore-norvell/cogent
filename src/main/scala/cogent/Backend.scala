package cogent
class Backend( val logger : Logger, val out : COutputter ) :

    private val isInArrayName = "isIn_a"
    private val currentChildArrayName = "currentChild_a"
    private val handledArrayName = "handled_a"
    private val eventPointerName = "event_p"
    private val statusVarName = "status"
    private val okStatusConstant = "OK_STATUS"

    def generateCCode( stateChart : StateChart ) : Unit = {
        val root = stateChart.root

        generateDefines( stateChart )
        
        out.blankLine
        out.put( "// This array maps the global index of each OR state to the local index of its currently active state" )
        out.endLine
        out.put( s"typedef unsigned char localIndex_t ;" )
        out.endLine
        out.put( s"static localIndex_t ${currentChildArrayName}[ OR_STATE_COUNT ] ;" )
        out.endLine
        out.put( s"static bool_t $isInArrayName[ STATE_COUNT ] ;" )
        out.blankLine
        
        out.put( s"bool_t dispatchEvent( event_t *${eventPointerName} ) " )
       
        out.block{
            out.put( s"bool_t ${handledArrayName}[ STATE_COUNT ] = {false};" )
            out.endLine
            generateCodeForState( root, stateChart ) 
            val rootStateName = stateChart.root.getCName
            out.put( s"return ${handledArrayName}[ ${globalMacro(root)} ];" )
        }

        // TODO Initialize the activeState_cg_v
    }

    def generateDefines( stateChart : StateChart ) : Unit = {
        val stateList = stateChart.nodes.filter( _.isState ).toSeq.sortBy( _.getGlobalIndex )
        val orStateList = stateList.filter( _.isOrState )
        val stateCount = stateList.size
        val orStateCount = orStateList.size
        out.put( s"#define STATE_COUNT $stateCount")
        out.endLine
        out.put( s"#define OR_STATE_COUNT $orStateCount" )
        out.blankLine
        out.comment( "Each state has a unique global index (G_INDEX)" )
        out.endLine
        out.comment( "Except the root, each state has a local index (L_INDEX) that is unique among its siblings." )
        out.endLine
        out.comment( "Initial states have a local index of 0." )
        out.endLine
        var index = 0
        for state <- stateList do
            logger.debug( s"State ${state.getFullName} has global index of ${state.getGlobalIndex}.")
            assert( state.getGlobalIndex == index )
            assert( state.isOrState == (index < orStateCount) )

            out.put( s"#define ${globalMacro(state)} $index")
            out.endLine
            val localIndex = state.getLocalIndex
            if localIndex >= 0 then 
                out.put( s"#define ${localMacro(state)} $localIndex")
            end if
            out.blankLine
            index += 1
        end for
        val choiceList = stateChart.nodes.filter( _.isChoicePseudostate ).toSeq.sortBy( _.getGlobalIndex )
        for choice <- choiceList do
            val localIndex = choice.getLocalIndex
            out.put( s"#define ${localMacro(choice)} $localIndex")
            out.blankLine
            index += 1
        end for
        
    }


    def generateCodeForState( state : Node, stateChart : StateChart ) : Unit = {
        state match 
            case x @ Node.BasicState( _ ) =>
                generateCodeForBasicState( x, stateChart )
            case x @ Node.OrState( _, _ ) =>
                generateCodeForOrState( x, stateChart )
            case x @ Node.AndState( _, _ ) =>
                generateCodeForAndState( x, stateChart )
            case _ => assert( false ) 
    }

    def generateCodeForBasicState( state : Node.BasicState, stateChart : StateChart ) : Unit = {
        out.comment( s"Code for basic state '${state.getCName}'")
        out.blockNoNewLine{
        
            if needCodeForEvents( state, stateChart ) then
                generateEventCodeForState( state, stateChart )
            else
                out.comment( s"State ${state.getCName} has no outgoing transitions." )
                out.endLine
            end if

        }
        out.comment( s"End of basic state '${state.getCName}'")
        out.endLine
    }

    def generateCodeForOrState( state : Node.OrState, stateChart : StateChart ) : Unit = {
        
        out.comment( s"Code for OR state '${state.getCName}'")
        out.blockNoNewLine{

            val globalIndexMacro = globalMacro(state) 

            if state.childStates.size == 0 then
                // No children.  Not possible. All Or nodes should have a start state
                // and this requirement should already have been checked.
                assert( false ) ;
            else if state.childStates.size == 1 then
                // An Or with one child does not need a switch command
                val child = state.childStates.head
                generateCodeForState( child, stateChart )
                out.put( s"${handledArrayName}[ $globalIndexMacro ] = ${handledArrayName}[ ${globalMacro(child)} ] ;")
                out.endLine
            else /* state.childStates.size > 1 */
                // Generate a switch command.
                out.switchComm(true, s"${currentChildArrayName}[ $globalIndexMacro]"  ) {
                    for child <- state.childStates do
                        out.caseComm( localMacro(child)  ) {
                            generateCodeForState( child, stateChart )
                            out.put( s"${handledArrayName}[ $globalIndexMacro ] = ${handledArrayName}[ ${globalMacro(child)} ] ;")
                            out.endLine
                        }
                        out.endLine
                    end for
                }
            end if
            if needCodeForEvents( state, stateChart ) then
                out.ifComm( s"! ${handledArrayName}[ ${globalMacro(state)} ]" ){
                    generateEventCodeForState( state, stateChart )
                }
                out.endLine
            else
                out.comment( s"State ${state.getCName} has no outgoing transitions." )
                out.endLine
            end if
        }
        out.comment( s"End of OR state '${state.getCName}'")
        out.endLine
    }

    def generateCodeForAndState( state : Node.AndState, stateChart : StateChart ) : Unit = {
        out.comment( s"Code for AND state '${state.getCName}'")
        out.blockNoNewLine {
            val globalIndexMacro = globalMacro(state) 
            var first = true
            for child <- state.childStates do
                generateCodeForState( child, stateChart )
                val str = if first then "" else s" ${handledArrayName}[ $globalIndexMacro ] ||"
                out.put( s"${handledArrayName}[ $globalIndexMacro ] =$str ${handledArrayName}[ ${globalMacro(child)} ] ;")
                out.endLine
                first = false 
            end for
            
            
            if needCodeForEvents( state, stateChart ) then
                out.ifComm( s"! ${handledArrayName}[ ${globalMacro(state)} ]" ){
                    generateEventCodeForState( state, stateChart )
                }
            else
                out.comment( s"State ${state.getCName} has no outgoing transitions." )
                out.endLine
            end if
        }
        out.comment( s"End of AND state '${state.getCName}'")
        out.endLine
    }

    def generateEventCodeForState( state : Node, stateChart : StateChart ) : Unit = {
        out.comment( s"Event handling code for state ${state.getCName}")
        out.endLine
        // First collect all the edges out of this state.
        val edges = stateChart.edges.filter( e => e.source == state )
        // Now all named triggers
        val namedTriggers = edges.map(e => e.triggerOpt.flatMap( _.asNamedTrigger )).flatten
        if namedTriggers.size > 0 then
            out.switchComm( false, s"eventClassOf(${eventPointerName})" ) {
                for Trigger.NamedTrigger( name ) <- namedTriggers do
                    generateCaseForEvent( name, state, stateChart )
                end for
                out.comment( "TODO code for tick events if any" )
                out.endLine
            }
        end if
    }

    def generateCaseForEvent( name : String, state : Node, stateChart : StateChart ) : Unit = {
        // Gather all edges that exit the state and have NamedTrigger( name )
        // as the trigger
        val edges = stateChart.edges.filter(
                        e =>   e.source == state
                            && e.triggerOpt.map( t => t match {
                                case Trigger.NamedTrigger(n) => n==name
                                case _ => false
                            }).getOrElse( false ) ) ;
        
        out.caseComm( name ){
            generateIfsForEdges( name, state, edges, stateChart ) ;
        }
    }

    def generateIfsForEdges( name : String, node : Node, edges : Set[Edge], stateChart : StateChart) : Unit = {
        assert( node.isState || node.isChoicePseudostate )
        if( node.isState )
            out.put( s"status_t ${statusVarName} = ${okStatusConstant} ;") ; out.endLine
        val elseGuardedEdges = edges.filter( e => e.guardOpt.map( g => g match{
                                                    case Guard.ElseGuard() => true
                                                    case _ => false } ).getOrElse( false ) ) ;
        val nonElseGuardedEdges = (edges -- elseGuardedEdges).toSeq ;
        val unguardedEdges = nonElseGuardedEdges.filter( e => e.guardOpt.isEmpty )
        if unguardedEdges.size + elseGuardedEdges.size > 1 then 
            // Case: There too many (more than 1) else-guarded edges, too many unguarded edges, or too many of both.
            logger.fatal( s"More than one transition out of ${node.getFullName} with trigger $name has no guard or an else guard." )
        else if unguardedEdges.size == 1 then
            // Case: There is one guarded edge ...
            if nonElseGuardedEdges.size > 1 then
                // ... but there are also other edges
                logger.fatal( s"Vertex ${node.getFullName} with trigger $name has both unguarded and guarded transitions" )
            else
                // ... and it's the only edge
                assert( edges.size == 1 )
                val edge = unguardedEdges.head
                out.put( s"${handledArrayName}[${globalMacro(node)}] = true ; " ) 
                out.endLine
                generateTransition( edge, stateChart )
        else 
            // Case there are no unguarded edges and at most 1 else-guarded edges
            assert( unguardedEdges.size == 0 )
            assert( elseGuardedEdges.size < 2 )
            // TODO check guards for
            //  Overlap -- there exists a state where 2 or more guards could both be true
            //  Underlap -- there exists a state where all guards are false and there is no else. (This would be info for states and warning for branch nodes)
            //  Pointless else -- in all states at least one guard is true, but there is an else.

            // For each edge that is not guarded by an else, output "if(...) {...} else "
            for edge <- nonElseGuardedEdges do
                val guard = edge.guardOpt.head
                out.ifComm{ generateGuardExpression( guard, stateChart, node ) }{
                    if node.isState then
                        out.put( s"${handledArrayName}[${globalMacro(node)}] = true ; " ) 
                        out.endLine
                    end if
                    generateTransition( edge, stateChart )
                }
                out.put( " else " )
            end for
            if elseGuardedEdges.size == 1 then
                out.block{ generateTransition( elseGuardedEdges.head, stateChart ) }
            else if node.isState then
                out.block{
                    out.comment( "No transition." ) ; out.endLine
                }
            else
                out.block{
                    logger.warning( s"Vertex ${node.getFullName} has no else guarded transition. If none of the guards are true, the code will crash." )
                    out.put( "assertUnreachable() ;" )
                    out.endLine
                 }
            end if
        end if
    }

    def generateTransition( edge : Edge, stateChart : StateChart ) : Unit = {
        val source = edge.source
        val target = edge.target
        assert( source.isState || source.isChoicePseudostate )
        assert( target.isState || target.isChoicePseudostate )
        val actions = edge.actions
        out.comment( s"Transition from ${source.getCName} to ${target.getCName}." ) ; out.endLine


        val leastCommonOr = stateChart.leastCommonOrOf( source, target )

        if( source.isState ) {
            // Exit the source. The -1 means exit all active children as well.
            out.put( s"${exitFunctionName(source)}( -1 ) ;" ) ; out.endLine
        } else {
            assert( source.isChoicePseudostate )
            // If the source is a choice node, then we don't need to exit it.
        }
        // Now exit all ancestors until we reach the leastCommonOr ancestor.
        var child = source
        var p = stateChart.parentOf( source )
        while( p != leastCommonOr )
            // Exit the ancestor. The parameter means don't also exit this child.
            out.put( s"${exitFunctionName(p)}( ${localMacro(child)} ) ;" ) ; out.endLine
            child = p
            p = stateChart.parentOf( p )
        // Generate code for the actions
        actions.foreach( generateActionCode( _ ) )
        // Now we need to enter the states down to and including the parent.
        // But first we find the path
        //println( s"lcoa is ${leastCommonOr.getCName} target is ${target.getCName}") 
        p = stateChart.parentOf( target )
        var path = List( target )
        //println( s"p is ${p.getCName}; path is ${path.foldLeft("::Nil")( (a,b)=> a + "::" + b.getCName)}")
        while p != leastCommonOr do
            path = p :: path
            p = stateChart.parentOf( p )
            //println( s"p is ${p.getCName}; path is ${path.foldLeft("::Nil")( (a,b)=> a + "::" + b.getCName)}")
        end while
        while path.tail != Nil do
            p = path.head
            child = path.tail.head
            // Enter an ancestor of the target.
            // The parameter here means don't also enter this child.
            out.put( s"${enterFunctionName(p)}( ${localMacro(child)} ) ;" ) ; out.endLine
            path = path.tail
        if target.isState then
            // Enter the target.
            // The parameter of -1 means enter the child(ren) also.
            out.put( s"${enterFunctionName(target)}( -1 ) ;" ) ; out.endLine
        else
            assert( target.isChoicePseudostate )
            // For choice pseudostate's there is no enter function.
            // But we do need to keep going, so we generate
            // code for the exiting edges.  The graph should already have
            // been checked for loops that do not go through a state
            // and so this recursive call should terminate.
            val edges = stateChart.edges.filter( e => e.source == target )
            generateIfsForEdges( "none", target, edges, stateChart )
    }

    def generateGuardExpression( guard : Guard, stateChart : StateChart, sourceNode : Node ) : Unit = {
        out.endLine
        out.indent
        gge( guard )
        out.dedent
        out.endLine

        def gge( guard : Guard ) : Unit = {
            guard match
                case Guard.ElseGuard() => assert(false, "Else where else should not be")
                case Guard.OKGuard() =>
                    if sourceNode.isState then
                        logger.warning( s"Vertex ${sourceNode.getFullName} has an OK guard, but this will always be true at the start of a transition." ) ;
                        out.put( "true" )
                    else
                        out.put( s"OK( $statusVarName )" )
                case Guard.InGuard( name : String ) => 
                    // TODO. Bug! What if the name was changed!
                    out.put( s"${isInArrayName}[ ${globalMacro(name, stateChart)} ]" )
                case Guard.NamedGuard( name : String ) =>
                    out.put( s"$name( ${eventPointerName}, ${statusVarName} )" ) 
                case Guard.RawGuard( rawCCode : String ) =>
                    out.put (s"( $rawCCode )")
                case Guard.NotGuard( operand : Guard ) =>
                    out.put( "! ") ; gge( operand )
                case Guard.AndGuard( left : Guard, right : Guard ) =>
                    out.endLine
                    out.put("(" ) 
                    out.endLine
                    out.indent
                    gge( left )
                    out.dedent
                    out.endLine
                    out.put( "&&" )
                    out.endLine
                    out.indent
                    gge( right )
                    out.dedent
                    out.endLine
                    out.put( ")" )
                    out.endLine
                case Guard.OrGuard( left: Guard, right : Guard ) =>
                    out.endLine
                    out.put("(" ) 
                    out.endLine
                    out.indent
                    gge( left )
                    out.dedent
                    out.endLine
                    out.put( "||" )
                    out.endLine
                    out.indent
                    gge( right )
                    out.dedent
                    out.endLine
                    out.put( ")" )
                    out.endLine
                case Guard.ImpliesGuard( left : Guard, right : Guard ) =>
                    out.endLine
                    out.put("( !" ) 
                    out.endLine
                    out.indent
                    gge( left )
                    out.dedent
                    out.endLine
                    out.put( "||" )
                    out.endLine
                    out.indent
                    gge( right )
                    out.dedent
                    out.endLine
                    out.put( ")" )
                    out.endLine

        }
    }

    def generateActionCode( action : Action ) : Unit = {
        out.comment( s"Code for action $action." ) ; out.endLine
        action match 
            case Action.NamedAction( name : String ) =>
                out.put( s"${statusVarName} = ${name}( ${eventPointerName}, $statusVarName ) ;" )
                out.endLine
            case Action.RawAction( rawCCode : String ) =>
                out.put( s"{ ${rawCCode} ; }" )
                out.endLine
    }

    def needCodeForEvents( state : Node, stateChart : StateChart ) : Boolean = { 
        val edges = stateChart.edges.filter( e => e.source == state )
        return !(edges.isEmpty)
    }

    def globalMacro( node : Node ) : String = {
        assert( node.isState )
        ("G_INDEX_" + node.getCName )
    }

    def globalMacro( name : String, stateChart : StateChart ) : String = {
        assert( stateChart.nodes.exists( n => n.getCName == name && n.isState ) )
        ("G_INDEX_" + name )
    }

    def localMacro( node : Node ) : String = {
        ("L_INDEX_" + node.getCName )
    }

    def exitFunctionName( node : Node ) : String = {
        assert( node.isState )
        ("exit_" + node.getCName )
    }

    def enterFunctionName( node : Node ) : String = {
        assert( node.isState )
        ("enter_" + node.getCName )
    }
end Backend