typedef int bool_t ;
const int true = 1;
const int false = 0 ;

typedef enum event_e {
    TICK, a 
} event_class_t ;

typedef struct event_s {
    event_class_t event_class ;
} event_t ;

#define eventClassOf( evp ) (evp->event_class)

typedef int status_t ;
#define OK_STATUS (0)
#define OK( s ) ( (s)==OK_STATUS )

#define assertUnreachable() (0)

#include "foo.c"
