package cogent
class Backend( val logger : Logger, val out : COutputter ) :

    def generateCCode( stateChart : StateChart ) : Unit = {
        val root = stateChart.root

        generateDefines( stateChart )
        
        out.blankLine
        out.put( "// This array maps the global index of each OR state to the local index of its currently active state" )
        out.endLine
        out.put( "static localIndex_t activeChild_a[ OR_STATE_COUNT ] ;" )
        out.endLine( )
        out.put( "static bool_t isIn_a[ STATE_COUNT ] ;" )
        out.blankLine
        
        out.put( "bool dispatchEvent( event_t *pev )" )
       
        out.block{
            out.put( "bool_t handled_a[ STATE_COUNT ] ;" )
            out.endLine
            generateCodeForState( root ) 
            val rootStateName = stateChart.rootState.cName
            out.put( s"return handled_a[ G_INDEX_$rootStateName ] ;" )
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
        out.comment( "Except the first, each state has a local index (L_INDEX) that is unique among its siblings.")
        out.comment( "Initial states have a local index of 0.")
        var index = 0
        for state <- stateList do
            logger.debug( s"State ${state.getFullName} has global index of ${state.getGlobalIndex}.")
            assert( state.getGlobalIndex == index )
            assert( state.isOrState == (index < orStateCount) )

            val cname = state.getCName
            out.put( s"#define G_INDEX_$cname $index")
            out.endLine
            val localIndex = state.getLocalIndex
            if localIndex >= 0 then 
                out.put( s"#define L_INDEX_$cname $localIndex")
            end if
            out.blankLine
            index += 1
        end for
    }


    def generateCodeForState( root : Node ) : Unit = {
        root match 
            case x @ Node.BasicState( _ ) =>
                generateEventCodeForState( x )
            case x @ Node.OrState( _, _ ) =>
                generateCodeForOrState( x )
            case x @ Node.AndState( _, _ ) =>
                generateCodeForAndState( x )
            case _ => assert( false ) 
    }

    def generateCodeForOrState( state : Node.OrState ) : Unit = {
        out.put( s"// Code for OR node '${state.getCName}'")
        out.endLine
        if state.childStates.size == 1 then
            val child = state.childStates.head
            generateCodeForState( child )
        else
            out.switchComm( s"activeChild_a[ G_INDEX_${state.getCName} ]"  ) {
                for child <- state.childStates do
                    out.caseCommand( child.getLocalIndex.toString ) {
                        generateCodeForState( child )
                    }
                    out.endLine
                end for
            }
        end if
        // TODO set the handledFlag for this state and possibly put an if
        // around the event code.
        generateEventCodeForState( state )
    }

    def generateCodeForAndState( state : Node.AndState ) : Unit = {
        out.put( s"// Code for AND node '${state.getCName}'")
        out.endLine
        for child <- state.childStates do
            out.block{ generateCodeForState( child ) }
            out.endLine
        end for
        
        // TODO set the handledFlag for this state and possibly put an if
        // around the event code.generateEventCodeForState( state )
    }

    def generateEventCodeForState( state : Node ) : Unit = {
        // First collect all the edges out of this state.
        val edges = stateChart.edges.filter( e => s.source == state )
        // Now all named triggers
        val namedTriggers = edges.map(e => e.triggerOpt.flatMap( _.asNamedTrigger )).flatten

        out.switchComm( "event_p.eventClass" ){
            for NamedTrigger( name ) <- namedTriggers do
                generateCaseFor( name, state )
            end for
        }
    }

end Backend
