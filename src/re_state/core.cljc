(ns re-state.core
  (:require [clojure.set :refer [rename-keys]]
            [re-frame.core :as rf]
            [re-state.flow :as fl]))

(defn get-intent [context]
  (get-in context [:coeffects :event 0]))

(defn get-state [context]
  (get-in context [:coeffects ::state]))

(defn get-flow [context]
  (get-in context [:coeffects ::flow]))

(def effects-keys-alias
  {:db :next-db ::state :next-state})

(def coeffects-keys-alias
  {:event :event :db :prev-db ::state :prev-state})

(defn context->rs-context
  [context]
  (merge (rename-keys (:effects   context) effects-keys-alias)
         (rename-keys (:coeffects context) coeffects-keys-alias)))

(defmulti accept?
  "Checks if the transition predicate keyword provided as the first argument
  should be accepted in the context passed in the second argument."
  (fn [k _] k))

(defmulti effects
  "Returns the effects for the transition effects keyword provided as the first
  argument in the context passed in the second argument. Noop if k is nil."
  (fn [k _] k))

(defmethod effects nil [_ _] nil)

(defmulti resolve-effects-conflict
  "Returns a vector of [effect-name effect-value] for the duplicate effects key
  found when collecting all effects."
  (fn [k _ _] k))

(defmethod resolve-effects-conflict :dispatch [_ x y]
  [:dispatch-n [x y]])

(defmethod resolve-effects-conflict :dispatch-n [_ x y]
  [:dispatch-n (into x y)])

(defn merge-effects-with [f m [k v]]
  (if (contains? m k)
    (recur f (dissoc m k) (f k (get m k) v))
    (assoc m k v)))

(defn make-accept? [context]
  (let [rs-context (context->rs-context context)]
    (fn [intent state]
      (every? #(accept? % rs-context)
              (fl/get-transition-predicates (get-flow context) intent state)))))

(defn get-effects [context]
  (let [rs-context (context->rs-context context)
        intent (get-intent context)
        next-state (get-in context [:effects ::state])]
    (->> (fl/get-next-effects (get-flow context) intent next-state)
         (map #(effects % rs-context))
         (apply concat)
         (reduce (partial merge-effects-with resolve-effects-conflict) {}))))

(defn get-next-states [context]
  (fl/get-next-states (get-flow context)
                      (get-state context)
                      (get-intent context)))

(defn transition [context]
  {:post [(some? (get-in % [:effects ::state]))]}
  (let [accept? (make-accept? context)
        intent (get-intent context)]
    (assoc-in context [:effects ::state]
              (some #(when (accept? intent %) %)
                    (get-next-states context)))))

(defn next-effects [context]
  (update-in context [:effects] merge (get-effects context)))

(defn assoc-db-state [context]
  (assoc-in context [:effects :db ::state]
            (get-in context [:effects ::state])))

;; -------------------------
;; INTERCEPTOR

(defn interceptor-handler
  "Takes a re-frame context and returns a new context with potentially a new
  state in effects as well as any other effects the transition requires.
  
  Requires the following to be in coeffects:

  state - The current control state
  flow  - The application flow
  "
  [context]
  (-> context
      (transition)
      (next-effects)
      (assoc-db-state)))

(def interceptor
  [(rf/inject-cofx ::state)
   (rf/inject-cofx ::flow)
   (rf/->interceptor
     :id ::step
     :after #(interceptor-handler %))])

;; -------------------------
;; COFX

(def app-state (atom nil))

(rf/reg-cofx ::state
             (fn [coeffects]
               (assoc coeffects ::state @app-state)))

(rf/reg-fx ::state
           (fn [value]
             (reset! app-state value)))

(def app-flow (atom nil))

(rf/reg-cofx ::flow
             (fn [coeffects]
               (assoc coeffects ::flow @app-flow)))

(rf/reg-fx ::flow
           (fn [value]
             (reset! app-flow value)))

(comment
  (def context
    {:effects {:db {}}
     :coeffects {:db {}
                 :event [:route :entry]
                 ::state :ready
                 ::flow {:fsm {:ready {:route #{:entry}}}
                            :tpm {:entry {:route #{:always-true}}}
                            :tem {:entry {:route #{:load-entries :route-home}}}}}})


  (defmethod effects :route-home [_ _]
    {:dispatch [:route :home]})

  (defmethod accept? :always-true [_ _] true)

  (defmethod effects :load-entries [_ _]
    {:dispatch [:load-entries]})

  (-> context
      (transition)
      (next-effects)
      (assoc-db-state)))
