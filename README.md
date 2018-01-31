# re-state

Use state machines to describe your re-frame application. Based on the [SAM
Pattern](http://sam.js.org/) and
[Restate your UI: Creating a user interface with re-frame and state machines](http://blog.cognitect.com/blog/2017/8/14/restate-your-ui-creating-a-user-interface-with-re-frame-and-state-machines).

## Overview

The three maps that define your application are as follows:

- `fsm`: The finite state machine map.
- `tpm`: The transition predicate map.
- `tem`: The transition effects map.

### `fsm`

```clojure
{nil          {:init       #{:empty}}
 :empty       {:add-water  #{:in-between}}
 :in-between  {:add-water  #{:in-between :full}
               :drink      #{:in-between :empty}}
  :full       {:drink      #{:in-between}}}
```

The finite state machine is a map of current state to a map of intent to
possible next states.

### `tpm`

```clojure
{:in-between  {:add-water  state/not-filled?
               :drink      state/not-empty?}
 :full        {:add-water  state/filled?}
 :empty       {:drink      state/empty?
               :init       (constantly true)}}
```

The transition predicate map is a map of next state to a map of intent to
transition predicate.

For example if in the above `fsm` we were to be in the
`:in-between` state with the intent of `:add-water` we'd have the two possible
next states of `:in-between` and `:full`. To determine which is the next valid
state we pass a context to the predicate functions at the paths `[:in-between
:add-water]` and `[:full :add-water]` in the `tpm`. This context looks like the
following:

```clojure
{:prev-db    {:v 1}        ;; the previous db
 :next-db    {:v 2}        ;; the next proposed db
 :event      [:add-water]  ;; the event, a vector of [intent & values]
 :prev-state :in-between}  ;; the previous state
```

Example implementations for `filled?` and `not-filled?` could be.

```
(defn filled? [{:keys [next-db]}]
  (= (:v next-db) 10))

(def not-filled? (complement filled?))
```

Of course the value `10` could be something stored in our db as well, possibly
set during the `:init` event.

In this case `next-db` with `:v` equal to `2` would mean we are still in an
`:in-between` state.

### `tem`

Using a different example than those above, here is what a `tem` for calculating
the factorial of a number might look like:

```
{:test   {:mult dispatch-mult
          :init dispatch-mult}}
```
The transition effects map is a map of next state to a map of intent to
transition effects handler.

The transition effects handler should return a map of effects, like those in
re-frame.

After the `tpm` is used to determine what the next valid state to accept is, the
`tem` is checked using the accepted path and if a handler is found it is called
and its effects are merged.

Like the transition predicate, the transition effects handler takes a context.
It has the same keys as the transition predicate context, but also includes a
`:next-state` key with the accepted state.

## re-frame interceptor

While this library will eventually support different implementations, the
currently available one is for re-frame. The `re-state.re-frame` namespace
exposes the following api:

- `reg-fsm`: a function that takes a handler returning a `fsm`
- `reg-tpm`: a function that takes a handler returning a `tpm`
- `reg-tem`: a function that takes a handler returning a `tem`
- `interceptor`: an interceptor that will use the `fsm`, `tpm` and `tem`,
  associng in a `:re-state.core/state` key into your db with the next state as
  well as adding to the re-frame context any transition effects.

Usage is simple. Just add the interceptor to your `reg-event-x` declarations
where the event name is the same key as the intent in the registered maps. The
handler then returns a new proposed db.

For example:

```clojure
(reg-event-db
  :add-water
  [rs/interceptor]
  (fn [db _]
    (update-in db [:v] inc)))
```

Note that it is still possible to return effects other than `db` using
`reg-event-fx` but since they could be overridden by those returned from a
transition effects handler it is suggested you avoid that.

## Deeper concepts

There are few conceptual points to be aware of that will help when designing
your apps.

### Control State

We're using a somewhat unconventional use of the word "state", at least in the
context of application development. In re-frame the db is what persists the
stateful information in the app. But the states we refer to are more correctly
called control states.

Normally applications don't explicitly define their control states. That is,
they don't try to give a name to any current state the app might be in.

By defining our control states we are distilling a lot of information down into
a unique keyword that we can used to build our app representation. In the
article [Restate your UI: Creating a user interface with re-frame and state
machines](http://blog.cognitect.com/blog/2017/8/14/restate-your-ui-creating-a-user-interface-with-re-frame-and-state-machines)
it is pointed out that fragmenting the control state into several different
properties leads to error prone code. We have to make sure all these different
properties are always in sync, and do this across many different handlers.

What we want in the end is a single value to determine what the current control
state is and to use that to show potential next actions. In practice this means
creating a re-frame subscription to the `:re-state.core/state` key in `db` and
using that as the primary "switch" in your views.

### Events

In a normal re-frame application events have quite a bit of power. With no
interceptors to interpret them they have the ability to directly modify what
will become the next db.

With the interceptor provided in `re-state.re-frame` we instead consider the
updated db from an event handler to be only a proposal. This proposed db can be
compared to the previous db in the transition predicates to determine the next
control state.

While we have to accept a new control state we do not have to commit a new
proposed db. Consider a form where a user submits invalid information. Our event
handler would give us the next db, which could for example involve putting the
resulting values into an entities vector. In the invalid case we would not
accept the next db and we can handle this by returning the value of `:prev-db`
as `:db` in our transition effects handler.

```clojure
(defn invalid-form-effects [{:keys [prev-db]}]
  {:db prev-db})
```

### Transition Predicates

The transition predicates are the "why" of your application. If you were to
reach an unexpected control state you would go back and refer to them to see the
rules that were applied to determine that control state.

How you design your transition predicates depends on the complexity of your db.
You can optimize in cases where there are only a few known states, and
determining which is the correct control state is easy, or you can thoroughly
check the next-db against the prev-db to be absolutely certain what control
state you're in.

Writing your transition predicates using something like `core.logic` would be a
good fit. Most apps probably wouldn't need that though, where simple boolean
checks could be applied.

To understand more about the potential of transition predicates, explore TLA+.