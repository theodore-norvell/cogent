typedef int bool_t ;

typedef enum event_e {
    TICK, a 
} event_t ;
typedef struct event_s {
    event_class_t event_class ;
} event_t ;

#define eventClassOr( evp ) (evp->event_class)

typedef int localIndex_t ;

#include "foo.c"
