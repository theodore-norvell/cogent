# Cogent Project

Generate Code from PlantUML statecharts.

Author: Theodore Norvell (theo@mun.ca)

## Example and prerequisites

The input is a plant UML spec such as

```
@startuml
    state IDLE 
    state RUNNING
    state C <<choice>>
    [*] -> IDLE
    IDLE -> C : go
    C -> RUNNING : [READY] / start
    C --> IDLE : [else]
    RUNNING -> IDLE : kill / stop
    RUNNING -> IDLE : after(60s) / stop
@enduml
```

From a PlantUML spec, Cogent generates a controller in C that looks like this:

```C
bool dispatchEvent( const event_t *pev ){
    ...
}
```

As events happen they should be fed into the controller and it will react by changing its own state and taking actions.  The result of the controller is `true` if the event was handled and `false` if the event was ignored. In our example, `kill` events are ignored when the state is `IDLE` and `go` events are ignored when the state is `READY`.

The `dispatchEvent` subroutine has many prerequisites which need to be supplied. You need to define

* The `event_t` type.
* A function (or a function-like macro) `eventClassOf(event_p)` which produces an member of an enum type.
* The members of that type correspond to the triggers in the diagram: In the example above there
should be members `go` and `kill`. There should also be a member called `TICK`. For example, this would do

```C
    typedef enum EventClass_e {go, kill, TICK} ;
```

* For each action, there needs to be a function of type `void (const event_p *)` with the same name as the action. For the example above we would need to supply functions

```C
    void start(const event_p *) {
        ...
    }
    void stop(const event_p *) {
        ...
    }
```

* For each guard, there needs to be a function of type `bool (const event_p *)`.  For the example above, we would need

```C
    bool READY(const event_p *) {
        ...
    }
```

* Some functions similar to the FreeRTOS functions `xTaskGetTickCount`, `vTaskDelay`, and `pdMS_TO_TICKS` and a type similar to FreeRTOS's `TickType_t`. If using FreeRTOS, just include the appropriate header files.
* A macro or function "void assertThat( bool )".  This should do nothing if the argument is true. What it does if the argument is false is up to you
* A macro or function "void assertUnreachable()".  What this does is up to you.

## TICK events

TICK events are used to trigger transitions labelled "after( D )" where D is a duration in seconds or milliseconds.  My advice is after every event that makes the controller return true, feed the controller a sequence of TICK events until it returns false.

```C
     /* Do this shortly after an event happens. */

     bool handled = dispatchEvent( &event ) ;
     while( handled ) {
         handled = dispatchEvent( &tick ) ;
     }
```

And you should periodically send the controller a sequence of tick events fairly frequently:

```C
     /* Do this fairly frequently. */
     bool handled = dispatchEvent( &tick ) ;
     while( handled ) {
         handled = dispatchEvent( &tick ) ;
     }
```

## Details

### States and pseudostates

States and choice pseudostates (TODO: this may change. Revisit this statement later)should have names that are proper C identifiers and should be less or equal to 20 characters.

States can be

#### Basic states

Like in the first example:

#### Compound states

I.e. states with children.

```
@startuml
state W {
            [*] -> W1
            state W1
            state WC <<choice>>
            state W2 {
                [*] -> W21
                state W21
                state W22
                W22 -> WC : a / p
            }
            state W3 {
                [*] -> W31
                state W31
                state W32
            }
            WC --> W1 : [A] / q
            WC --> W32 : [B] / r
}
@enduml
```

#### Concurrency states

I.e. States with two or more regions that operate concurrently.

```
@startuml
    state A {
        note "B" as B
        [*] -> C
        state C
        state D
        C -> D: a [g] / w
        C -> D: a [h] / x
        D -> C : a [g] / w
        D -> C : a [h] / x
        D -> C : a [else] / y
        --
        note "E" as E
        [*] -> F
        state F
        state G
        state H <<choice>>
        F -> H: a / y
        H -> F: [ok] 
        H -> G: [else]
        F --> E : XYZ
    state A
@enduml
```

Cogent will make up names for the regions, like `A_region_0` and `A_region_1`. Currently these might be in any order and the order might vary from run to run of the program.

#### How Cogent sees states

Cogent calls regions, OR states. It also calls compound states that don't have multiple regions OR states.  And it considers the whole diagram an OR state, called the root state.  Compound states with multiple regions it calls AND states.

For example in the diagram above:

* The diagram is an OR state, the root state.
* A is an AND state and is the only child of the root state
* A's two regions are OR states and are its children.
* C and D are basic states and are the children of one region.
* F and G are basic states and are the children of the other region.

In the previous diagram, W, W2, W3, and the whole diagram are OR states.  The single region inside of W, for example, is not considered to be a state.

It might be logical to consider W to be an AND state with one child (its region) which is an OR state. That way all OR states would correspond to a region. But this is not the way Cogent sees it.  (This might change if we every support internal actions)

#### Start pseudostates

Each OR state should have one (and only one) start pseudostate and it should have one and only one transition out of it. This transition must go to a sibling state which is not labelled.

However, an OR state with only one child state does not need a start pseudo state.

#### Choice pseudostates

As shown in the examples, choice pseudostates can be used. 

#### Notes

Notes are ignored.

#### Final states

Final states are not supported. Completion events are not supported.

#### Forks, Joins, and other pseudostates

Are not supported.

### State Actions

Entry and exit actions for states are not yet supported.

Do actions are not supported.

Internal actions are not supported (yet).

### State semantics

Each OR state has at all times a "current child", which is one of its children.

There is a set of states which are the active states.

When the machine is at rest. We have the following invariants:

* ROOT The root state is always active.
* OR0 For any OR state, including the root state, if the OR state is active, then the current child of the OR state is also active.
* OR1 When an OR state is active, none of its children except its current child are active.
* OR2 When an OR state is not active, none of its children are active.
* AND0 For any AND state, if it is active, all of its children will be active.
* AND1 For any AND state, if it is not active, none of its children will be active.

Invariants OR0 and AND0 could be untrue when the machine is not at rest .  Consider this example

```
@startuml
state W {
            [*] -> W1
            state W1
            state WC <<choice>>
            state W2 {
                [*] -> W21
                state W21
                state W22
                W22 -> WC : a / p
            }
            state W3 {
                [*] -> W31
                state W31
                state W32
            }
            WC --> W1 : [A] / q
            WC --> W32 : [B] / r
}
@enduml
```

Suppose W, W2, and W22 are all active. This implies (by the invariant) that W2 is the current child of W and W22 is the current child of W2.  Let's also suppose that W31 is the current child of W3.

* First W22 is exited, so it becomes inactive.
* At this point, W2 is active, but its current child is not, violating OR0
* Next W2 is exited, so it also becomes inactive.
* That fixes the violation just noted.
* But now W is active, but its current child, W2, is not!, violating OR0 again.
* At this point, action p takes, place. Then guards A and B are evaluated and then either actions p or q. So the code you write might execute while OR0 are violated.
* Supposing B is true and A is false, then W3 will be become active and at the same time it becomes the current child of W, thus correcting the violation of OR0.
* However, since W31 is the current child of W3, then it will be exited and become inactive, creating another violation of OR0.
* Finally, W32 is entered and becomes active and at at same time becomes the active child of W3. This corrects ensures that OR0 is true everywhere.

Similarly at the point when the exit or entry action of an AND state is executed, we can expect that AND0 will be violated for that AND state.

### Transitions

Each transition is labelled with

* A trigger. (Optional)
* A guard (Optional)
* A sequence of actions (possibly empty).

#### Triggers

Triggers can be:

* Named triggers: Just a name. These correspond to event classes.
* After triggers: after( D ) where D is a duration. Currently a duration is a non-negative integer constant followed by either s for seconds or ms for milliseconds.  Examples "after( 0s )", "after( 255 ms)". The maximum duration depends on your implementation of the time functions and `TickType_t` and is not checked by cogent.

### Guards

Guards are either the special guard "[else]" or boolean expressions enclosed in brackets. For example:

* "[ A ]"
* "[ A and not B or C implies D]"
* "[ A && ! B || C ==> D]"

Basic guards are 

* A C identifier. This is translated to a function call.
* Any text between braces. E.g. "{x>10}". These are not translated at all, but output as-is with the braces replaced by parentheses.
* "in S" where S is the name of a state.
This will be true if state S is active at the time.

The boolean operators follow the usual rules of precedence and are right associative.

### Action sequences.

A sequence of one or more actions can follow a slash "/". Each action can be followed by an semicolon, but this is optional.  Examples

* "" an empty sequence
* "/ a" or "/ a;" a sequence of 1 action
* "/ a b c", "/ a; b; c", "/ a; b; c;" etc. A sequence of three actions

### Actions

Each action is either

* A C identifier, such as "f". This is translated into a function call "f( event_p );".
* Any code within braces.  This is copied verbatim into the controller.


### Restrictions on transitions.

Transitions come in three flavours

* Strawberry: From a start pseudo state to a state with the same parent. These transitions must not be labelled.
* Vanilla: From a state (Basic or compound) to another state or to a choice pseudostate.
* Chocolate: From a choice psuedostate to another choice psuedostate.

Strawberry transitions should not be labelled.

Vanilla transitions should have a trigger.  (If the source state is a basic state, Cogent will rewrite a missing trigger as "after(0s)".)

Chocolate transitions should not have a trigger.

Chocolate transitions may not form a cycle.

For state any state, if there is more than one transition out of it with the same named trigger then of these transitions:

* At most one may be guarded with an else.
* None may be unguarded.

The same restrictions apply to the set of all transitions that leave a given choice pseudo-state.

### Using guards appropriately

A vanilla transition is enabled if its source state is active
* It has no guard.
* It has a guard that is true.
* It has an else guard and all the competing guards are false.

When there are multiple enabled transitions out of a state for the event, only one will fire, but the choice is arbitrary and unpredictable (unless you read the code, but that could change, when it is next generated).  For example if there are guards A, B. The generated code might look like this

```C
   if( A(event_p) ) { do transition guarded by A }
   else if( B(event_p) ) { do transition guarded by B }
   else { do nothing }
```

or like this:

```C
   if( B(event_p) ) { do transition guarded by B }
   else if( A(event_p) ) { do transition guarded by A }
   else { do nothing }
```

For the set of all edges leaving a given state that are all labelled with the same named trigger:

* It is good practice to ensure that at most one guard will be true.

Once a vanilla transition is traversed, the machine is committed, there is no turning back. If it reaches a choice state where no guard is true, the dispatcher will do goodness knows what.

E.g. if the two transitions out of a choice pseudostate are guarded by A and B the generated code could be

```C
   if( A(event_p) ) { do transition guarded by A }
   else if( B(event_p) ) { do transition guarded by B }
   else { assertUnreachable() ; }
```

or

```C
   if( B(event_p) ) { do transition guarded by B }
   else if( A(event_p) ) { do transition guarded by A }
   else { assertUnreachable() ; }
```

Note that if `assertUnreachable` simply reports the problem and returns, the machine will (almost certainly be) left with a set of active states that violates invariants OR0 and/or AND0.

For the set of all transitions leaving a given choice pseudo state:

* It is necessary that you ensure that at least one guard will be true, or that you have an else-guarded transition.
* It is good practice to ensure that at most one guard will be true.

### Pre-emption

When there are enabled transitions out of more than one state that could all fire.  Transitions that leave child (or grandchild, etc) states have priority over transitions out or parent (or grandparent, etc) states.  For example:

```
@startuml


state A {
    state B
    state C
    [*] -> B
    B -> C : a [P] / x
    --
    state D
    state E
    [*] -> D
    D -> E : a [Q] / y
}
state F 
[*] -> A
A -> F : a / z ;

@enduml
```

Suppose the active states are A, B, and D. 

* If not P and not Q, then the transition from A to F will fire.
* If P and not Q, then the transition from B to C will fire. The transition from D to E is blocked because Q is false and the transition from A to F is blocked by pre-emption.
* If not P and Q, the situation is similar. Only the transition from D to E fires.
* If P and Q, then both the transition from B to C and the transition from D to E will fire and the transition from A to F will be blocked by pre-emption.

When two vanilla transitions fire in concurrently, as in the previous example, they actually go sequentially on the same thread, so there is no need to worry about mutual exclusion and race conditions between x and y. The generated code will follow this algorithm:

1. if P : exit B; do x; enter C
1. if Q : exit D; do y; enter E
1. if neither of the above happened: exit B; exit A's upper region; exit D; exit A's lower region; exit A; do z; enter F

or the same with the first two lines swapped.  Likewise the exits on the last line could happen in an alternative order

* exit D; exit A's lower region; exit B; exit A's upper region; exit A;

As you can see from algorithm of the generated code, the x action should not effect the truth of Q and the action y should not affect the truth of P otherwise things get very hard to analyse.  Likewise you have to be careful with "in" guards. For example if P were "in D" and Q is true, that would be ugly since whether that "in D" is true or not depends on whether the other transition is considered first.