#include <stdbool.h>

typedef enum { BASIC, OR, AND } StateKindT ;

typedef struct StateS StateT ;

typedef struct EventS EventT ;

typedef struct TransitionS TransitionT ;

typedef bool GuardT( EventT *pEvent, TransitionT *pTrans ) ;

typedef void ActionT( EventT *pEvent, TransitionT *pTrans ) ;

struct StateS {
    StateKindT kind ;
    char *pcName ;
    int transitionCount ;
    TransitionT **ppTransitions ;
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
    StateT *pSource ; // Not needed? 
    EventClassT eventClass ;
    GuardT *pGuard ;
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
static StateT rootState ;

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

/* Guards */

bool alwaysGuard( EventT *pEvent, TransitionT *pTrans ) {
    return true ;
}

/* Actions. */

void enterNorthSouthGreenAct( EventT *pEvent, TransitionT *pTrans ) { }
void enterAllRedAct( EventT *pEvent, TransitionT *pTrans ) { }
void enterEastWestAmberAct( EventT *pEvent, TransitionT *pTrans ) { }
void enterNorthSouthAmberAct( EventT *pEvent, TransitionT *pTrans ) { }


/* The Transitions Definitions. */
TransitionT initTrans =  { &startState, TICK_EC, &alwaysGuard, &enterNorthSouthGreenAct, &northSouthGreen } ;

TransitionT northSouthGreen2AllRed =  { &northSouthGreen, EV_REC_APPROACHES_EC, &alwaysGuard, enterAllRedAct, &allRed } ;
TransitionT northSouthGreen2northSouthAmber =  { &northSouthGreen, TIMER_EC, &alwaysGuard, &enterNorthSouthAmberAct, &northSouthAmber } ;

TransitionT northSouthAmber2AllRed =  { &northSouthAmber, EV_REC_APPROACHES_EC, &alwaysGuard, enterAllRedAct, &allRed } ;
TransitionT northSouthAmber2eastWestGreen =  { &northSouthAmber, TIMER_EC, &alwaysGuard, &enterEastWestAmberAct, &eastWestGreen } ;

TransitionT eastWestGreen2AllRed =  { &eastWestGreen, EV_REC_APPROACHES_EC, &alwaysGuard, &enterAllRedAct, &allRed } ;
TransitionT eastWestGreen2eastWestAmber =  { &eastWestGreen, TIMER_EC, &alwaysGuard, &enterEastWestAmberAct, &eastWestAmber } ;

TransitionT eastWestAmber2AllRed =  { &eastWestGreen, EV_REC_APPROACHES_EC, &alwaysGuard, enterAllRedAct, &allRed } ;
TransitionT eastWestAmber2NorthSouthGreen = { &eastWestGreen, TIMER_EC, &alwaysGuard, &enterNorthSouthGreenAct, &northSouthGreen } ;
    
TransitionT AllRed2NorthSouthGreen = { &allRed, EV_REC_CLEAR_EC, &alwaysGuard, &enterNorthSouthGreenAct, &northSouthGreen } ;

/* The states and their transitionLists.*/
static TransitionT *(startStateTransitions[]) = { &initTrans, 0 } ;
static StateT startState = { BASIC, "startState", 1, startStateTransitions } ;

static TransitionT *(northSouthGreenTransitions[]) = {
                        &northSouthGreen2AllRed,
                        &northSouthGreen2northSouthAmber,
                        0 } ;
static StateT northSouthGreen = { BASIC, "northSouthGreen", 2, northSouthGreenTransitions } ;

static TransitionT *(northSouthAmberTransitions[]) = {
                        &northSouthAmber2AllRed,
                        &northSouthAmber2eastWestGreen,
                        0 } ;
static StateT northSouthAmber = { BASIC, "northSouthAmber", 2, northSouthAmberTransitions } ;

static TransitionT *(eastWestGreenTransitions[]) = {
                        &eastWestAmber2AllRed,
                        &eastWestGreen2eastWestAmber,
                        0 } ;
static StateT eastWestGreen = { BASIC, "eastWestGreen", 2, eastWestGreenTransitions } ;

static TransitionT *(eastWestAmberTransitions[]) = {
                        &eastWestGreen2AllRed,
                        &eastWestAmber2NorthSouthGreen,
                        0 } ;
static StateT eastWestAmber = { BASIC, "eastWestAmber", 2, eastWestAmberTransitions } ;

static TransitionT *(allRedTransitions[]) = {
                        &AllRed2NorthSouthGreen,
                        0 } ;
static StateT allRed = { BASIC, "allRed", 1, allRedTransitions } ;

static StateT *(rootChildren[]) = { &startState, &northSouthGreen,
                                    &northSouthAmber, &eastWestGreen,
                                    &eastWestAmber, &allRed,
                                    (StateT*)0 } ;
static StateT  rootState = {OR, "root", 0, 0, 6, rootChildren } ;
