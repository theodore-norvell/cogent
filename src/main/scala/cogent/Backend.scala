package cogent
class Backend( val logger : Logger, val out : COutputter ) :

    def generateCCode( stateChart : StateChart ) : Unit = {
        val root = stateChart.root
        
        // TODO declare activeState_cg_v
        out.put( "bool dispatchEvent( event_t *pev )" )
       
        out.block{
             // TODO Set all the handled flags to false
            generateCodeForState( root ) 
            // TODO return command.
        }

        // TODO Initialize the activeState_cg_v
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

    def generateEventCodeForState( state : Node ) : Unit = {
        out.put( s"// Event dispatch code for state '${state.getName}'")
        out.endLine
    }

    def generateCodeForOrState( state : Node.OrState ) : Unit = {
        out.put( s"// Code for OR node '${state.getName}'")
        out.endLine
        if state.childStates.size == 1 then
            val child = state.childStates.head
            generateCodeForState( child )
        else
            out.switchComm( s"activeState_cg_v.${state.getName}"  ) {
                for child <- state.childStates do
                    out.put( s"case ${child.getLocalIndex} : " )
                    out.block{ generateCodeForState( child ) }
                    out.put( "break ;")
                    out.endLine
                end for
            }
        end if
        // TODO set the handledFlag for this state.
        generateEventCodeForState( state )
    }

    def generateCodeForAndState( state : Node.AndState ) : Unit = {
        out.put( s"// Code for AND node '${state.getName}'")
        out.endLine
        for child <- state.childStates do
            out.block{ generateCodeForState( child ) }
            out.endLine
        end for
        // TODO set the handledFlag for this state.
        generateEventCodeForState( state )
    }

end Backend
