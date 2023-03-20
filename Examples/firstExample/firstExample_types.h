#ifndef FIRST_EXAMPLE_TYPES
#define FIRST_EXAMPLE_TYPES

    typedef enum eventClass_e {GO, KILL, TICK} eventClass_t;
    typedef struct event_s {
        eventClass_t tag ;
        union{
            struct {
                int a ;
                char b ;
            } go ;
            struct {
                short d ;
            } kill ;
        } ;
    } event_t ;
    typedef event_t *event_p ;

    #define eventClassOf( event_p ) ((event_p)->tag)

    #define TIME_T unsigned int
    typedef int status_t ;
    typedef bool bool_t ;

    #define OK_STATUS 0

#endif