#include "submachineTest1_preamble.h"

#ifndef TIME_T
    #define TIME_T unsigned int
#endif

#ifndef IS_AFTER
    #define IS_AFTER(d, t0, t1) ((TIME_T)(d) <= (TIME_T)((t1)-(t0)))
#endif

#ifndef TO_DURATION
    #define TO_DURATION(x) x##u
#endif

#ifndef EVENT
    #define EVENT(name) name
#endif

#ifndef GUARD
    #define GUARD(name) name
#endif

#ifndef ACTION
    #define ACTION(name) name
#endif

#define STATE_COUNT 6
#define OR_STATE_COUNT 2

/* Each state has a unique global index (G_INDEX) */
/* Except the root, each state has a local index (L_INDEX) that is unique among its siblings. */
/* Initial states have a local index of 0. */
#define LOCAL_INDEX_T int
#define G_INDEX_Sub1 0
#define L_INDEX_Sub1 2

#define G_INDEX_root 1

#define G_INDEX_B 2
#define L_INDEX_B 1

#define G_INDEX_V__Sub1 3
#define L_INDEX_V__Sub1 1

#define G_INDEX_U__Sub1 4
#define L_INDEX_U__Sub1 0

#define G_INDEX_A 5
#define L_INDEX_A 0

#define L_INDEX_Y__Sub1 3

#define L_INDEX_X__Sub1 2

static void enter_Sub1 ( LOCAL_INDEX_T, TIME_T ) ; static void exit_Sub1 ( LOCAL_INDEX_T ) ;
static void enter_root ( LOCAL_INDEX_T, TIME_T ) ; 
static void enter_B ( LOCAL_INDEX_T, TIME_T ) ; static void exit_B ( LOCAL_INDEX_T ) ;
static void enter_V__Sub1 ( LOCAL_INDEX_T, TIME_T ) ; static void exit_V__Sub1 ( LOCAL_INDEX_T ) ;
static void enter_U__Sub1 ( LOCAL_INDEX_T, TIME_T ) ; static void exit_U__Sub1 ( LOCAL_INDEX_T ) ;
static void enter_A ( LOCAL_INDEX_T, TIME_T ) ; static void exit_A ( LOCAL_INDEX_T ) ;

// This array maps the global index of each OR state to the local index of its currently active state
static LOCAL_INDEX_T currentChild_a[ OR_STATE_COUNT ] ;
// This array maps keeps track of which states are active
static bool_t isIn_a[ STATE_COUNT ] ;
// This array maps keeps track the time at which each active state was entered
static TIME_T timeEntered_a[ STATE_COUNT ] ;

void initStateMachine_submachineTest1( TIME_T now) {
    enter_root( -1, now ) ;
}

bool_t dispatchEvent_submachineTest1( event_t *event_p, TIME_T now ) {
    bool_t handled_a[ STATE_COUNT ] = {false};
    /* Code for OR state 'root' */{
        switch( currentChild_a[ G_INDEX_root] ) {
            case L_INDEX_B : {
                /* Code for basic state 'B' */{
                    /* Event handling code for state B */
                    switch( eventClassOf(event_p) ) {
                        case TICK : {
                            /* Code for after( 0.0 ms ) */
                            if(    ! handled_a[G_INDEX_B] ){
                                status_t status = OK_STATUS ;
                                handled_a[G_INDEX_B] = true ; 
                                /* Transition from B to A. */
                                exit_B( -1 ) ;
                                enter_A( -1, now ) ;
                            }
                        } break ;
                        default : { }
                    }
                }/* End of basic state 'B' */
                handled_a[ G_INDEX_root ] = handled_a[ G_INDEX_B ] ;
            } break ;
            case L_INDEX_Sub1 : {
                /* Code for OR state 'Sub1' */{
                    switch( currentChild_a[ G_INDEX_Sub1] ) {
                        case L_INDEX_U__Sub1 : {
                            /* Code for basic state 'U__Sub1' */{
                                /* Event handling code for state U__Sub1 */
                                switch( eventClassOf(event_p) ) {
                                    case EVENT(x) : {
                                        status_t status = OK_STATUS ;
                                        handled_a[G_INDEX_U__Sub1] = true ; 
                                        /* Transition from U__Sub1 to V__Sub1. */
                                        exit_U__Sub1( -1 ) ;
                                        enter_V__Sub1( -1, now ) ;
                                    } break ;
                                    default : { }
                                }
                            }/* End of basic state 'U__Sub1' */
                            handled_a[ G_INDEX_Sub1 ] = handled_a[ G_INDEX_U__Sub1 ] ;
                        } break ;
                        case L_INDEX_V__Sub1 : {
                            /* Code for basic state 'V__Sub1' */{
                                /* Event handling code for state V__Sub1 */
                                switch( eventClassOf(event_p) ) {
                                    case EVENT(y) : {
                                        status_t status = OK_STATUS ;
                                        handled_a[G_INDEX_V__Sub1] = true ; 
                                        /* Transition from V__Sub1 to Y__Sub1. */
                                        exit_V__Sub1( -1 ) ;
                                        /* Transition from Y__Sub1 to B. */
                                        exit_Sub1( L_INDEX_Y__Sub1 ) ;
                                        enter_B( -1, now ) ;
                                    } break ;
                                    default : { }
                                }
                            }/* End of basic state 'V__Sub1' */
                            handled_a[ G_INDEX_Sub1 ] = handled_a[ G_INDEX_V__Sub1 ] ;
                        } break ;
                        default : { assertUnreachable() ; }
                    }
                    /* State Sub1 has no outgoing transitions. */
                }/* End of OR state 'Sub1' */
                handled_a[ G_INDEX_root ] = handled_a[ G_INDEX_Sub1 ] ;
            } break ;
            case L_INDEX_A : {
                /* Code for basic state 'A' */{
                    /* Event handling code for state A */
                    switch( eventClassOf(event_p) ) {
                        case EVENT(P) : {
                            status_t status = OK_STATUS ;
                            handled_a[G_INDEX_A] = true ; 
                            /* Transition from A to X__Sub1. */
                            exit_A( -1 ) ;
                            enter_Sub1( L_INDEX_X__Sub1, now ) ;
                            /* Transition from X__Sub1 to U__Sub1. */
                            /* Code for action NamedAction(act1). */
                            status = ACTION(act1)( event_p, status ) ;
                            enter_U__Sub1( -1, now ) ;
                        } break ;
                        default : { }
                    }
                }/* End of basic state 'A' */
                handled_a[ G_INDEX_root ] = handled_a[ G_INDEX_A ] ;
            } break ;
            default : { assertUnreachable() ; }
        }
        /* State root has no outgoing transitions. */
    }/* End of OR state 'root' */
    return handled_a[ G_INDEX_root ];
}

static void enter_Sub1 ( LOCAL_INDEX_T childIndex, TIME_T now ) {
    isIn_a[ G_INDEX_Sub1 ] = true ;
    timeEntered_a[ G_INDEX_Sub1 ] = now ;
    currentChild_a[ G_INDEX_root ] = L_INDEX_Sub1 ;
    if(  childIndex == -1   ) {
        enter_U__Sub1( -1, now ) ;
    }
}
static void exit_Sub1 ( LOCAL_INDEX_T childIndex )
{
    if( childIndex == -1  ) {
        LOCAL_INDEX_T current = currentChild_a[ G_INDEX_Sub1 ] ;
        switch( current ) {
            case L_INDEX_U__Sub1 : {
                exit_U__Sub1( -1 ) ; 
            } break ;
            case L_INDEX_V__Sub1 : {
                exit_V__Sub1( -1 ) ; 
            } break ;
            default : { assertUnreachable() ; }
        }
    }
    isIn_a[ G_INDEX_Sub1 ] = false ;
}

static void enter_root ( LOCAL_INDEX_T childIndex, TIME_T now ) {
    isIn_a[ G_INDEX_root ] = true ;
    timeEntered_a[ G_INDEX_root ] = now ;
    if(  childIndex == -1   ) {
        enter_A( -1, now ) ;
    }
}

static void enter_B ( LOCAL_INDEX_T childIndex, TIME_T now ) {
    isIn_a[ G_INDEX_B ] = true ;
    timeEntered_a[ G_INDEX_B ] = now ;
    currentChild_a[ G_INDEX_root ] = L_INDEX_B ;
}
static void exit_B ( LOCAL_INDEX_T childIndex )
{
    isIn_a[ G_INDEX_B ] = false ;
}

static void enter_V__Sub1 ( LOCAL_INDEX_T childIndex, TIME_T now ) {
    isIn_a[ G_INDEX_V__Sub1 ] = true ;
    timeEntered_a[ G_INDEX_V__Sub1 ] = now ;
    currentChild_a[ G_INDEX_Sub1 ] = L_INDEX_V__Sub1 ;
}
static void exit_V__Sub1 ( LOCAL_INDEX_T childIndex )
{
    isIn_a[ G_INDEX_V__Sub1 ] = false ;
}

static void enter_U__Sub1 ( LOCAL_INDEX_T childIndex, TIME_T now ) {
    isIn_a[ G_INDEX_U__Sub1 ] = true ;
    timeEntered_a[ G_INDEX_U__Sub1 ] = now ;
    currentChild_a[ G_INDEX_Sub1 ] = L_INDEX_U__Sub1 ;
}
static void exit_U__Sub1 ( LOCAL_INDEX_T childIndex )
{
    isIn_a[ G_INDEX_U__Sub1 ] = false ;
}

static void enter_A ( LOCAL_INDEX_T childIndex, TIME_T now ) {
    isIn_a[ G_INDEX_A ] = true ;
    timeEntered_a[ G_INDEX_A ] = now ;
    currentChild_a[ G_INDEX_root ] = L_INDEX_A ;
}
static void exit_A ( LOCAL_INDEX_T childIndex )
{
    isIn_a[ G_INDEX_A ] = false ;
}
