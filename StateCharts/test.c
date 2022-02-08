typedef enum { BASIC, OR, AND } StateKindT ;

typedef struct StateS StateT ;

typedef struct EventS EventT ;

typedef struct TransitionS TransitionT ;

typedef void EventHandlerT( EventT *evt, TransitionT **ppTransition ) ;

typedef  void ActionT( EventT *pEvent, TransitionT *pTrans ) ;

struct StateS {
    StateKindT kind ;
    char *pcName ;
    EventHandlerT *pEventHandler ;
    int childCount ;
    StateT **ppChildren ;
    StateT *pParent ;
    int depth ;
    int index ;
    int activeChild ; } ;

typedef enum {
    TICK_EC,
    TIMER_EC,
    EV_REC_APPROACHES_EC,
    EV_REC_CLEAR_EC
}  EventClassT ;

struct EventS
{
    EventClassT tag ;
    union {
        int time ;
    } data ; }  ;


struct TransitionS {
    StateT *pSource ;
    ActionT *pAction ;
    StateT *pTarget ;
}  ;

/* Declarations of states. */

static StateT startState ;
static StateT northSouthGreen ;
static StateT northSouthAmber ;
static StateT eastWestGreen ;
static StateT eastWestAmber ;
static StateT allRed ;
static StateT  rootState ;

/* Transition declarations */
TransitionT initTrans ;

TransitionT northSouthGreen2AllRed ;
TransitionT northSouthGreen2northSouthAmber ;

TransitionT northSouthAmber2AllRed ;
TransitionT northSouthAmber2eastWestGreen ;

TransitionT eastWestGreen2AllRed ;
TransitionT eastWestGreen2eastWestAmber ;

TransitionT eastWestAmber2AllRed ;
TransitionT eastWestAmber2NorthSouthGreen ;
    
TransitionT AllRed2NorthSouthGreen ;

/*Event handler definitions*/
void startStateEH( EventT *pEvent, TransitionT **ppTransition )  {
    switch( pEvent->tag ) {
        case TICK_EC: {
            *ppTransition = & initTrans ;
        } break ;
        default : {}
    }
}

void northSouthGreenEH( EventT *pEvent, TransitionT **ppTransition )  {
    switch( pEvent->tag ) {
        case TIMER_EC: {
            *ppTransition = & northSouthGreen2northSouthAmber ;
        } break ;
        case EV_REC_APPROACHES_EC: {
            *ppTransition = & northSouthGreen2AllRed ;
        } break ;
        default:  {}
    }
}

void doNothingEH( EventT *pEvent, TransitionT **ppTransition )  {
}

/* Actions. */

void enterNorthSouthGreenAct( EventT *pEvent, TransitionT *pTrans ) { }
void enterAllRedAct( EventT *pEvent, TransitionT *pTrans ) { }
void enterEastWestAmberAct( EventT *pEvent, TransitionT *pTrans ) { }
void enterNorthSouthAmberAct( EventT *pEvent, TransitionT *pTrans ) { }


/* The Transitions Definitions. */
TransitionT initTrans =  { &startState, &enterNorthSouthGreenAct, &northSouthGreen } ;

TransitionT northSouthGreen2AllRed =  { &northSouthGreen, enterAllRedAct, &allRed } ;
TransitionT northSouthGreen2northSouthAmber =  { &northSouthGreen, &enterNorthSouthAmberAct, &northSouthAmber } ;

TransitionT northSouthAmber2AllRed =  { &northSouthAmber, enterAllRedAct, &allRed } ;
TransitionT northSouthAmber2eastWestGreen =  { &northSouthAmber, &enterEastWestAmberAct, &eastWestGreen } ;

TransitionT eastWestGreen2AllRed =  { &eastWestGreen, &enterAllRedAct, &allRed } ;
TransitionT eastWestGreen2eastWestAmber =  { &eastWestGreen, &enterEastWestAmberAct, &eastWestAmber } ;

TransitionT eastWestAmber2AllRed =  { &eastWestGreen, enterAllRedAct, &allRed } ;
TransitionT eastWestAmber2NorthSouthGreen =  { &eastWestGreen, &enterNorthSouthGreenAct, &northSouthGreen } ;
    
TransitionT AllRed2NorthSouthGreen =  { &allRed, &enterNorthSouthGreenAct, &northSouthGreen } ;

/* The states and their children.*/
static StateT startState = { BASIC, "startState", startStateEH } ;
static StateT northSouthGreen = { BASIC, "northSouthGreen", northSouthGreenEH } ;
static StateT northSouthAmber = { BASIC, "northSouthAmber", 0 } ;
static StateT eastWestGreen = { BASIC, "eastWestGreen", 0 } ;
static StateT eastWestAmber = { BASIC, "eastWestAmber", 0 } ;
static StateT allRed = { BASIC, "allRed", 0 } ;

static StateT *(rootChildren[]) = { &startState, &northSouthGreen,
                                    &northSouthAmber, &eastWestGreen,
                                    &eastWestAmber, &allRed,
                                    (StateT*)0 } ;
static StateT  rootState = {OR, "root", doNothingEH, 6, rootChildren } ;
