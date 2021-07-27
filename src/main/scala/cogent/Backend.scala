package cogent
class Backend( val logger : Logger, val out : COutputter ) :

    def generateCCode( stateChart : StateChart ) : Unit = {
        val root = stateChart.root

        generateDefines( stateChart )
        
        out.blankLine
        out.put( "// This array maps the global index of each OR state to the local index of its currently active state" )
        out.endLine
        out.put( "static localIndex_t activeChild_a[ OR_STATE_COUNT ] ;" )
        out.endLine
        out.put( "static bool_t isIn_a[ STATE_COUNT ] ;" )
        out.blankLine
        
        out.put( "bool dispatchEvent( event_t *pev )" )
       
        out.block{
            out.put( "bool_t handled_a[ STATE_COUNT ] = {false};" )
            out.endLine
            generateCodeForState( root, stateChart ) 
            val rootStateName = stateChart.root.getCName
            out.put( s"return handled_a[ ${globalMacro(root)} ];" )
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
        out.comment( "Except the first, each state has a local index (L_INDEX) that is unique among its siblings." )
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
                assert( false ) ;
            else if state.childStates.size == 1 then
                val child = state.childStates.head
                generateCodeForState( child, stateChart )
                out.put( s"handled_a[ $globalIndexMacro ] = handled_a[ ${globalMacro(child)} ] ;")
                out.endLine
            else /* state.childStates.size > 1 */
                out.switchComm(true, s"activeChild_a[ $globalIndexMacro]"  ) {
                    for child <- state.childStates do
                        out.caseComm( localMacro(child)  ) {
                            generateCodeForState( child, stateChart )
                            out.put( s"handled_a[ $globalIndexMacro ] = handled_a[ ${globalMacro(child)} ] ;")
                            out.endLine
                        }
                        out.endLine
                    end for
                }
            end if
            if needCodeForEvents( state, stateChart ) then
                out.ifComm( s"! handled_a[ ${globalMacro(state)} ]" ){
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
                val str = if first then "" else s" handled_a[ $globalIndexMacro ] ||"
                out.put( s"handled_a[ $globalIndexMacro ] =$str handled_a[ ${globalMacro(child)} ] ;")
                out.endLine
                first = false 
            end for
            
            
            if needCodeForEvents( state, stateChart ) then
                out.ifComm( s"! handled_a[ ${globalMacro(state)} ]" ){
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
            out.switchComm( false, "eventClassOf(event_p)" ) {
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
            generateIfsForEdges( name, state, edges, true, stateChart ) ;
        }
    }

    def generateIfsForEdges( name : String, node : Node, edges : Set[Edge], nodeIsState : Boolean, stateChart : StateChart) : Unit = {
        val elseGuardedEdges = edges.filter( e => e.guardOpt.map( g => g match{
                                                    case Guard.ElseGuard() => true
                                                    case _ => false } ).getOrElse( false ) ) ;
        val nonElseGuardedEdges = (edges -- elseGuardedEdges).toSeq ;
        val unguardedEdges = nonElseGuardedEdges.filter( e => e.guardOpt.isEmpty )
        if unguardedEdges.size + elseGuardedEdges.size > 1 then 
            logger.fatal( s"More than one transition out of ${node.getFullName} with trigger $name has no guard or an else guard." )
        else if unguardedEdges.size == 1 then
            if nonElseGuardedEdges.size > 1 then
                logger.fatal( s"Vertex ${node.getFullName} with trigger $name has both unguarded and guarded transitions" )
            else
                val edge = unguardedEdges.head
                out.put( s"handled_a[${globalMacro(node)}] = true ; " ) 
                out.endLine
                generateTransition( edge )
        else 
            assert( unguardedEdges.size == 0 )
            assert( elseGuardedEdges.size < 2 )
            // TODO check guards for
            //  Overlap -- 2 guards could both be true
            //  Underlap -- all guards could be false and there is no else. (This would be info for states and warning for branch nodes)
            //  Pointless else -- not all guards can be false but there is an else.
            for edge <- nonElseGuardedEdges do
                val guard = edge.guardOpt.head
                out.ifComm{ generateGuardExpression( guard ) }{
                    if nodeIsState then
                        out.put( s"handled_a[${globalMacro(node)}] = true ; " ) 
                        out.endLine
                    end if
                    generateTransition( edge )
                }
                out.put( " else " )
            end for
            if elseGuardedEdges.size == 1 then
                out.block{ generateTransition( elseGuardedEdges.head ) }
            else if nodeIsState then
                out.block{
                    out.comment( "fall through" ) ; out.endLine
                }
            else
                out.block{
                    logger.warning( s"Vertex ${node.getFullName} with trigger $name has no else guarded transition. If none of the guards are true, the code will crash." )
                    out.put( "assertUnreachable()" )
                    out.endLine
                 }
            end if
        end if
    }

    def generateTransition( edge : Edge ) : Unit = {
        out.comment( s"TODO transition from ${edge.source.getCName} to ${edge.target.getCName}." )
        out.endLine
    }

    def generateGuardExpression( guard : Guard ) : Unit = {
        out.comment( s"TODO code for guard $guard." )
    }

    def needCodeForEvents( state : Node, stateChart : StateChart ) : Boolean = { 
        val edges = stateChart.edges.filter( e => e.source == state )
        ; !(edges.isEmpty)
    }

    def globalMacro( node : Node ) : String = {
        assert( node.isState )
        ("G_INDEX_" + node.getCName )
    }

    def localMacro( node : Node ) : String = {
        assert( node.isState )
        ("L_INDEX_" + node.getCName )
    }
end Backend