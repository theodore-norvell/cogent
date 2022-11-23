#ifndef X
#define X
    #include <stdbool.h>
    #include "firstExample_types.h"

    void initStateMachine_firstExample( TIME_T now) ;

    bool_t dispatchEvent_firstExample( event_t *event_p, TIME_T now ) ;
#endif