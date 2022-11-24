#include "firstExample_preamble.h"

#ifndef TIME_T
    #define TIME_T unsigned int
#endif

#ifndef IS_AFTER
    #define IS_AFTER(d, t0, t1) ((TIME_T)(d) <= (TIME_T)((t1)-(t0)))
#endif

#ifndef TO_DURATION
    #define TO_DURATION(x) x##u
#endif

#define STATE_COUNT 3
#define OR_STATE_COUNT 1

/* Each state has a unique global index (G_INDEX) */
/* Except the root, each state has a local index (L_INDEX) that is unique among its siblings. */
/* Initial states have a local index of 0. */
#define LOCAL_INDEX_T int
#define G_INDEX_root 0

#define G_INDEX_IDLE 1
#define L_INDEX_IDLE 0

#define G_INDEX_RUNNING 2
#define L_INDEX_RUNNING 1

#define L_INDEX_C 2

static void enter_root ( LOCAL_INDEX_T, TIME_T ) ; 
static void enter_IDLE ( LOCAL_INDEX_T, TIME_T ) ; static void exit_IDLE ( LOCAL_INDEX_T ) ;
static void enter_RUNNING ( LOCAL_INDEX_T, TIME_T ) ; static void exit_RUNNING ( LOCAL_INDEX_T ) ;

// This array maps the global index of each OR state to the local index of its currently active state
static LOCAL_INDEX_T currentChild_a[ OR_STATE_COUNT ] ;
// This array maps keeps track of which states are active
static bool_t isIn_a[ STATE_COUNT ] ;
// This array maps keeps track the time at which each active state was entered
static TIME_T timeEntered_a[ STATE_COUNT ] ;

void initStateMachine_firstExample( TIME_T now) {
    enter_root( -1, now ) ;
}

bool_t dispatchEvent_firstExample( event_t *event_p, TIME_T now ) {
    bool_t handled_a[ STATE_COUNT ] = {false};
    /* Code for OR state 'root' */{
        switch( currentChild_a[ G_INDEX_root] ) {
            case L_INDEX_IDLE : {
                /* Code for basic state 'IDLE' */{
                    /* Event handling code for state IDLE */
                    switch( eventClassOf(event_p) ) {
                        case GO : {
                            status_t status = OK_STATUS ;
                            handled_a[G_INDEX_IDLE] = true ; 
                            /* Transition from IDLE to C. */
                            exit_IDLE( -1 ) ;
                            if( 
                                ready_query( event_p, status )
                             ){
                                /* Transition from C to RUNNING. */
                                /* Code for action NamedAction(start). */
                                status = start( event_p, status ) ;
                                enter_RUNNING( -1, now ) ;
                            } else {
                                /* Transition from C to IDLE. */
                                enter_IDLE( -1, now ) ;
                            }
                        } break ;
                        default : { }
                    }
                }/* End of basic state 'IDLE' */
                handled_a[ G_INDEX_root ] = handled_a[ G_INDEX_IDLE ] ;
            } break ;
            case L_INDEX_RUNNING : {
                /* Code for basic state 'RUNNING' */{
                    /* Event handling code for state RUNNING */
                    switch( eventClassOf(event_p) ) {
                        case KILL : {
                            status_t status = OK_STATUS ;
                            handled_a[G_INDEX_RUNNING] = true ; 
                            /* Transition from RUNNING to IDLE. */
                            exit_RUNNING( -1 ) ;
                            /* Code for action NamedAction(stop). */
                            status = stop( event_p, status ) ;
                            enter_IDLE( -1, now ) ;
                        } break ;
                        case TICK : {
                            /* Code for after( 60000.0 ms ) */
                            if(    ! handled_a[G_INDEX_RUNNING]
                                && IS_AFTER( TO_DURATION(60000), timeEntered_a[ G_INDEX_RUNNING ], now ) ){
                                status_t status = OK_STATUS ;
                                handled_a[G_INDEX_RUNNING] = true ; 
                                /* Transition from RUNNING to IDLE. */
                                exit_RUNNING( -1 ) ;
                                /* Code for action NamedAction(stop). */
                                status = stop( event_p, status ) ;
                                enter_IDLE( -1, now ) ;
                            }
                        } break ;
                        default : { }
                    }
                }/* End of basic state 'RUNNING' */
                handled_a[ G_INDEX_root ] = handled_a[ G_INDEX_RUNNING ] ;
            } break ;
            default : { assertUnreachable() ; }
        }
        /* State root has no outgoing transitions. */
    }/* End of OR state 'root' */
    return handled_a[ G_INDEX_root ];
}

static void enter_root ( LOCAL_INDEX_T childIndex, TIME_T now ) {
    isIn_a[ G_INDEX_root ] = true ;
    timeEntered_a[ G_INDEX_root ] = now ;
    if(  childIndex == -1   ) {
        enter_IDLE( -1, now ) ;
    }
}

static void enter_IDLE ( LOCAL_INDEX_T childIndex, TIME_T now ) {
    isIn_a[ G_INDEX_IDLE ] = true ;
    timeEntered_a[ G_INDEX_IDLE ] = now ;
    currentChild_a[ G_INDEX_root ] = L_INDEX_IDLE ;
}
static void exit_IDLE ( LOCAL_INDEX_T childIndex )
{
    isIn_a[ G_INDEX_IDLE ] = false ;
}

static void enter_RUNNING ( LOCAL_INDEX_T childIndex, TIME_T now ) {
    isIn_a[ G_INDEX_RUNNING ] = true ;
    timeEntered_a[ G_INDEX_RUNNING ] = now ;
    currentChild_a[ G_INDEX_root ] = L_INDEX_RUNNING ;
}
static void exit_RUNNING ( LOCAL_INDEX_T childIndex )
{
    isIn_a[ G_INDEX_RUNNING ] = false ;
}
