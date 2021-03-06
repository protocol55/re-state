# re-state

Use state machines to describe your re-frame application. Based on the [SAM
Pattern](http://sam.js.org/) and
[Restate your UI: Creating a user interface with re-frame and state machines](http://blog.cognitect.com/blog/2017/8/14/restate-your-ui-creating-a-user-interface-with-re-frame-and-state-machines).

## Overview

The main concept of the library is the "flow". This is a map of control state
keys to control state maps, where a control state map has the following keys:

- `:intents`: A map of intent to proposed next states
- `:preds`: A map of predicate keywords
- `:effects`: A map of effects predicate keywords

Below you'll find a description of each.

### `:intents`

```clojure
{nil         {:intents  {:init #{:empty}}}

 :empty      {:intents  {:add-water #{:in-between}}
              :preds    #{:empty?}}

 :in-between {:intents  {:add-water #{:in-between :full}
                         :drink #{:in-between :empty}}
              :preds    #{:not-filled? :not-empty?}}

 :full       {:intents  {:drink #{:in-between}}
              :preds    #{:filled?}}}
```

The intents map an event intent to possible next states.

### `:preds`

```clojure
{nil         {:intents  {:init #{:empty}}}

 :empty      {:intents  {:add-water #{:in-between}}
              :preds    #{:empty?}}

 :in-between {:intents  {:add-water #{:in-between :full}
                         :drink #{:in-between :empty}}
              :preds    #{:not-filled? :not-empty?}}

 :full       {:intents  {:drink #{:in-between}}
              :preds    #{:filled?}}}
```

`:preds` are a set of transition predicate keywords. These keywords are
registered using the multimethod `accept?`. Each must be true for the proposed
state to be accepted.

For example if in the above flow we were to be in the `:in-between` state with
the intent of `:add-water` we'd have the two possible next states of
`:in-between` and `:full`. To determine which is the next valid state we pass a
context to the predicate functions registered for the predicates keywords of
`:in-between` and `:full`. This context looks like the following:

```clojure
{:prev-db    {:v 1}        ;; the previous db
 :next-db    {:v 2}        ;; the next proposed db
 :event      [:add-water]  ;; the event, a vector of [intent & values]
 :prev-state :in-between}  ;; the previous state
```

To implement the predicates `:filled?` and `:not-filled?` we `defmethod`
`re-state.core/accept?`:

```clojure
(defmethod re-state.core/accept? :filled? [_ {:keys [next-db]}]
  (= (:v next-db) 10))

(defmethod re-state.core/accept? :not-filled? [_ {:keys [next-db]}]
  (< (:v next-db) 10))
```

Of course the value `10` could be something stored in our db as well, possibly
set during the `:init` event.

In this case `next-db` with `:v` equal to `2` would mean we are still in an
`:in-between` state.

### `:effects`

Using a different example than those above, here is what a flow for
launching a rocket with a countdown might look like:

```clojure
{nil       {:intents {:init #{:ready}}}
 :ready    {:intents {:start #{:counting}}
            :preds #{:counter-max? :not-aborted? :not-started?}}
 :counting {:intents {:decr-counter #{:launched :counting}
                      :abort #{:aborted}}
            :preds #{:started?}
            :effects #{:countdown}}
 :aborted  {:preds #{:aborted?}}
 :launched {:preds #{:counter-zero?}}}
```
The transition effects are a set of transition effect keywords.

The transition effects handler should return a map of effects, like those in
re-frame. To implement transition effects we `defmethod`
`re-state.core/effects`.

```clojure
(defmethod re-state.core/effects :countdown [_ _]
  {:dispatch [:decr-counter]})
```

After the `:preds` are used to determine what the next valid state to accept is,
the `:effects` are checked using the accepted state and if a handler is found it
is called and its effects are merged.

Like the transition predicate, the transition effects handler takes a context.
It has the same keys as the transition predicate context, but also includes a
`:next-state` key with the accepted state.

## re-frame interceptor

- `interceptor`: an interceptor that will use the `:re-state.core/flow`,
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

### Setup

You'll have to initally bootstrap your application with an event that will set
the `:re-state.core/flow` for your app. For example:

```clojure
(rf/reg-event-fx
 :bootstrap
 (fn [cofx event]
   {:re-state.core/flow app-flow}))
```

Once you do this your events can start using the interceptor.

### Dynamically adding flows

As you noticed above we can create an effect which will set the flow. This means
we can modify the flow of the app later, merging more flows into it, growing the
capabilities of the app. This is useful if you were to store flows in a database
and send them via ajax to your app. The `re-state.flow` namespace provides a
useful function to facilitate this: `merge-flows`. It takes one or more flows as
arguments and merges them into a single flow.

### Many effects

It is intuitive to be able to return multiple effects, but how do we resolve
conflicts if lets say two effects return a `:dispatch` in their map?

For this we have the multimethod `re-state.core/resolve-effects-conflict`. This
takes the effects key and two values for that effect we must resolve by
returning a vector of `[new-effect new-value]`. Included by default are handlers
for `:dispatch` and `:dispatch-n`. These look the like the following:

```
(defmethod resolve-effects-conflict :dispatch [_ x y]
  [:dispatch-n [x y]])

(defmethod resolve-effects-conflict :dispatch-n [_ x y]
  [:dispatch-n (into x y)])
```

Any app specific effects your app generates that could conflict can be handled
this way.

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

With the interceptor provided in `re-state.core` we instead consider the
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
