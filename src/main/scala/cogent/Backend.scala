package cogent
class Backend( val logger : Logger, val out : COutputter ) :

    private val boolType = "bool_t"
    private val trueConst = "true"
    private val falseConst = "false"
    private val isInArrayName = "isIn_a"
    private val currentChildArrayName = "currentChild_a"
    private val handledArrayName = "handled_a"
    private val timeEnteredArrayName = "timeEntered_a"
    private val eventPointerName = "event_p"
    private val statusType = "status_t"
    private val statusVarName = "status"
    private val okStatusConstant = "OK_STATUS"
    private val okMacro = "OK"
    private val localIndexType = "LOCAL_INDEX_T"
    private val eventType = "event_t"
    private val eventClassOf = "eventClassOf"
    private val timeType = "TIME_T"
    private val toDuration = "TO_DURATION"
    private val isAfter = "IS_AFTER"
    private val now = "now"

    def generateCCode( stateChart : StateChart, chartName : String ) : Unit = {
        val root = stateChart.root

        generateMacroDeclarations()

        generateDefines( stateChart )

        generateEnterAndExitDecls( stateChart )
        
        out.blankLine
        out.putLine( "// This array maps the global index of each OR state to the local index of its currently active state" )
        out.putLine( s"static $localIndexType ${currentChildArrayName}[ OR_STATE_COUNT ] ;" )
        out.putLine( "// This array maps keeps track of which states are active" ) 
        out.putLine( s"static ${boolType} $isInArrayName[ STATE_COUNT ] ;" )
        out.putLine( "// This array maps keeps track the time at which each active state was entered" ) 
        out.putLine( s"static $timeType $timeEnteredArrayName[ STATE_COUNT ] ;" )
        out.blankLine
        
        out.put( s"void initStateMachine_${chartName}( $timeType $now) " )
        out.block {
            out.putLine( s"${enterFunctionName(stateChart.root)}( -1, $now ) ;" )
        }

        out.blankLine 
        out.put( s"${boolType} dispatchEvent_${chartName}( ${eventType} *${eventPointerName}, $timeType $now ) " )
        out.block{
            out.put( s"${boolType} ${handledArrayName}[ STATE_COUNT ] = {${falseConst}};" )
            out.endLine
            generateCodeForState( root, stateChart ) 
            val rootStateName = stateChart.root.getCName
            out.put( s"return ${handledArrayName}[ ${globalMacro(root)} ];" )
        }

        generateEnterAndExitDefs( stateChart )
        
    }

    def generateMacroDeclarations() : Unit = {

        def declMacro( name : String, params : String, definition : String ) : Unit =
            out.putLine( s"#ifndef $name" )
            out.putLine( s"#define $name$params $definition" )
            out.putLine( "#endif")
            out.blankLine

        declMacro( localIndexType, "", "int" )
        declMacro( timeType, "", "unsigned int")
        declMacro( isAfter, "(d, t0, t1)", "((TIME_T)(d) <= (TIME_T)((t1)-(t0)))")
        declMacro( toDuration, "(x)", "x##u")
    }

    def generateEnterAndExitDecls( stateChart : StateChart ) : Unit = {
        val states = stateChart.nodes.filter( _.isState ).toSeq.sortBy( _.getGlobalIndex )
        for state <- states do
            out.put( s"static void ${enterFunctionName(state)} ( $localIndexType, $timeType ) ; "  )
            out.put( s"static void ${exitFunctionName(state)} ( $localIndexType ) ;" )
            out.endLine
    } 

    def generateEnterAndExitDefs( stateChart : StateChart ) : Unit = {
        val states = stateChart.nodes.filter( _.isState ).toSeq.sortBy( _.getGlobalIndex )
        for state <- states do
            out.blankLine
            // Generate the enter routine for the the state.
            out.put( s"static void ${enterFunctionName(state)} ( $localIndexType childIndex, $timeType $now ) "  )
            out.block{
                out.endLine
                out.putLine( s"$isInArrayName[ ${globalMacro(state)} ] = ${trueConst} ;" ) 
                out.putLine( s"$timeEnteredArrayName[ ${globalMacro(state)} ] = $now ;" )
                if( state != stateChart.root )
                    val parent = stateChart.parentOf( state ) 
                    if( parent.isOrState )
                        val parentGIndexOpt = parent.stateInfo.globalIndex
                        assert(! parentGIndexOpt.isEmpty )
                        out.putLine( s"$currentChildArrayName[ ${globalMacro(parent)} ] = ${localMacro(state)} ;" ) 

                // Entry actions go here.

                state match 
                    case x @ Node.BasicState( _ ) =>
                        // There should be nothing more to do
                    case x @ Node.OrState( _, _ ) =>
                        // When an Or state is entered. If the transition is to an descendent,
                        // There will be an enter call for it soon, so there is no need to
                        // do anything special. Otherwise, we need to  enter the default child.
                        // The default child should be first in the list of children
                        val defaultChild = startChild(x)
                        out.ifComm( " childIndex == -1 "){ out.put( s"${enterFunctionName( defaultChild )}( -1, $now ) ;") }
                        out.endLine
                    case x @ Node.AndState( _, _ ) =>
                        // When an AND state is entered, all of its children will also be entered.
                        // There will be another call to enter for the child, if any that is also
                        // being entered so we don't need to enter than child.
                        for child <- x.children.filter( _.isState ) do
                            out.ifComm( s"childIndex != ${localMacro(child)} ") {
                                    out.put( s"${enterFunctionName(child)}( -1, $now ) ; ")
                            }
                            out.endLine ;

                    case _ => assert( false )
            }
            out.endLine

            // Generate the exit routine for the the state.
            out.put( s"static void ${exitFunctionName(state)} ( $localIndexType childIndex )" )
            out.endLine
            out.block{
                out.endLine

                state match 
                    case x @ Node.BasicState( _ ) =>
                        // There should be nothing more to do
                    case x @ Node.OrState( _, _ ) =>
                        // When an Or state is exited: If the transition is from an descendent,
                        // it will already have been exited.
                        // But if the transition is from this node or any node above it
                        // then we must exit the current child.
                        out.ifComm( "childIndex == -1") {
                            out.putLine(s"$localIndexType current = currentChild_a[ ${globalMacro(x)} ] ;" )
                            val defaultChild = startChild( x ) 
                            out.switchComm(true, "current") {
                                for child <- x.children.filter( _.isState ) do
                                    out.caseComm( localMacro(child)) {
                                            out.put( s"${exitFunctionName(child)}( -1 ) ; ")
                                    }
                                    out.endLine
                            }
                        }
                        out.endLine
                    case x @ Node.AndState( _, _ ) =>
                        // When an AND state is exited, all of its children will should first
                        // be exited. If the transition's source is a strict descendant, then
                        // the region that that source is in should already have been exited.
                        // So here we exit all the others
                        for child <- x.children.filter( _.isState ) do
                            out.ifComm( s"childIndex != ${localMacro(state)} ") {
                                out.put( s"${exitFunctionName(state)}( -1 ) ; ")
                            }
                            out.endLine ;

                    case _ => assert( false )  

                // Exit actions go here

                out.put( s"$isInArrayName[ ${globalMacro(state)} ] = ${falseConst} ;" )
                out.endLine
            }
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
        val afterTriggers = edges.map(e => e.triggerOpt.flatMap( _.asAfterTrigger )).flatten
        //println( s"State is ${state.getFullName} edges is $edges")
        //println( s"namedTriggers is $namedTriggers")
        //println( s"afterTriggers is $afterTriggers")
        if namedTriggers.size > 0 || afterTriggers.size > 0 then
            out.switchComm( false, s"${eventClassOf}(${eventPointerName})" ) {
                for Trigger.NamedTrigger( name ) <- namedTriggers do
                    generateCaseForEvent( name, state, stateChart )
                end for

                if afterTriggers.size > 0 then 
                    val durationList = afterTriggers.toSeq.map( tr => tr.asAfterTrigger.get.durationInMilliseconds )
                    out.caseComm( "TICK" ) {
                        generateIfsForDurationList( durationList, state, stateChart )
                    }
                end if
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
            generateIfsForEdges( Some(name), state, edges, stateChart ) ;
        }
    }

    def generateIfsForDurationList( durationList: Seq[Double], state : Node, stateChart : StateChart ) : Unit = {
        val sortedDurationList = durationList.sorted
        for durationInMilliseconds <- sortedDurationList do
            // Gather all edges that exit the state and have t
            // as the trigger
            generateIfForDuration( durationInMilliseconds, state, stateChart )
    }

    def generateIfForDuration( durationInMilliseconds : Double, state : Node, stateChart : StateChart ) : Unit = {
        // Collect all edges with this duration.
        val edges = stateChart.edges.filter(
                        e =>   e.source == state
                            && e.triggerOpt.map( t => t match {
                                case Trigger.AfterTrigger(d) =>
                                    d == durationInMilliseconds
                                case _ =>
                                    false
                                }).getOrElse( false ) ) ;
        val intDuration : Int = durationInMilliseconds.asInstanceOf[Int]
        
        if intDuration.asInstanceOf[Double] != durationInMilliseconds then
            logger.warning( s"Duration $durationInMilliseconds ms rounded to $intDuration ms" )

        out.comment( s"Code for after( $durationInMilliseconds ms )" )
        out.endLine
        out.ifComm { 
            out.put(s"   ! ${handledArrayName}[${globalMacro(state)}]")
            if( intDuration > 0 )
                out.endLine
                out.put(s"    && $isAfter( ${toDuration}(${intDuration}), $timeEnteredArrayName[ ${globalMacro(state)} ], $now )")
        }{
            val triggerNameForMessages = Some(s"after $intDuration ms")
            generateIfsForEdges( triggerNameForMessages, state, edges, stateChart ) ;
        }
        out.endLine
    }

    def generateIfsForEdges( triggerDescriptionOpt : Option[String], node : Node, edges : Set[Edge], stateChart : StateChart) : Unit = {
        val locationForMessages = ( s"Vertex ${node.getFullName}" + (if( triggerDescriptionOpt.isEmpty ) "" else s" on trigger '${triggerDescriptionOpt.get}'" ))
        assert( node.isState || node.isChoicePseudostate )
        if( node.isState )
            out.put( s"${statusType} ${statusVarName} = ${okStatusConstant} ;") ; out.endLine
        val elseGuardedEdges = edges.filter( e => e.guardOpt.map( g => g match{
                                                    case Guard.ElseGuard() => true
                                                    case _ => false } ).getOrElse( false ) ) ;
        val nonElseGuardedEdges = (edges -- elseGuardedEdges)
        val unguardedEdges = nonElseGuardedEdges.filter( e => e.guardOpt.isEmpty )
        val conditionalEdges = (nonElseGuardedEdges -- unguardedEdges)
        assert(elseGuardedEdges.size + unguardedEdges.size + conditionalEdges.size == edges.size)
        if unguardedEdges.size > 1 then 
            // Case: There too many (more than 1) else-guarded edges, too many unguarded edges, or too many of both.
            logger.fatal( s"$locationForMessages has more than one transition with no guard." )
        else if unguardedEdges.size == 1 then
            // Case: There is one unguarded edge ...
            if edges.size > 1 then
                // ... but there are also other edges
                logger.fatal( s"$locationForMessages has both unguarded and guarded transitions." )
            else
                // ... and it's the only edge
                assert( edges.size == 1 )
                val edge = unguardedEdges.head
                if node.isState then
                    out.put( s"${handledArrayName}[${globalMacro(node)}] = ${trueConst} ; " ) 
                    out.endLine
                end if
                generateTransition( edge, stateChart )
        else if elseGuardedEdges.size > 1 then
            logger.fatal( s"$locationForMessages multiple transitions guarded by 'else'." )
        else
            // Case there are no unguarded edges and at most 1 else-guarded edges
            assert( unguardedEdges.size == 0 )
            assert( elseGuardedEdges.size < 2 )
            if conditionalEdges.size == 0 then
                logger.warning( s"$locationForMessages has only a transition guarded by 'else'; this guard is not needed.")
            // TODO check guards for
            //  Overlap -- there exists a state where 2 or more guards could both be true
            //  Underlap -- there exists a state where all guards are false and there is no else. (This would be info for states and warning for branch nodes)
            //  Pointless else -- in all states at least one guard is true, but there is an else.

            // For each edge that is not guarded by an else, output "if(...) {...} else "
            for edge <- conditionalEdges do
                val guard = edge.guardOpt.head
                out.ifComm{ generateGuardExpression( guard, stateChart, node ) }{
                    if node.isState then
                        out.put( s"${handledArrayName}[${globalMacro(node)}] = ${trueConst} ; " ) 
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
                    logger.warning( s"$locationForMessages has no else guarded transition. If none of the guards are true, the code will crash." )
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
            out.put( s"${enterFunctionName(p)}( ${localMacro(child)}, $now ) ;" ) ; out.endLine
            path = path.tail
        if target.isState then
            // Enter the target.
            // The parameter of -1 means enter the child(ren) also.
            out.put( s"${enterFunctionName(target)}( -1, $now ) ;" ) ; out.endLine
        else
            assert( target.isChoicePseudostate )
            // For choice pseudostate's there is no enter function.
            // But we do need to keep going, so we generate
            // code for the exiting edges.  The graph should already have
            // been checked for loops that do not go through a state
            // and so this recursive call should terminate.
            val edges = stateChart.edges.filter( e => e.source == target )
            generateIfsForEdges( None, target, edges, stateChart )
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
                        out.put( "${trueConst}" )
                    else
                        out.put( s"${okMacro}( $statusVarName )" )
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

    def startChild( state : Node.OrState ) : Node = {
        val opt = state.children.find( _.getLocalIndex == 0)
        assert( !opt.isEmpty)
        opt.get
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