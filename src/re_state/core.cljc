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

(defn with-context [f x context]
  (cond
    (vector? x)
    (f (first x) (assoc context :params (rest x)))

    :else
    (f x context)))

(defn make-accept? [context]
  (let [rs-context (context->rs-context context)]
    (fn [intent state]
      (every? #(with-context accept? % rs-context)
              (fl/get-transition-predicates (get-flow context) intent state)))))

(defn get-effects [context]
  (let [rs-context (context->rs-context context)
        intent (get-intent context)
        next-state (get-in context [:effects ::state])]
    (->> (fl/get-next-effects (get-flow context) intent next-state)
         (map #(with-context effects % rs-context))
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
  (require '[clojure.set :refer [rename-keys]])
  (require '[re-state.flow :as fl])

  (def rocket
    {nil      {:intents {:init #{:ready}}}
    :ready    {:intents {:start #{:counting}}
                :preds #{:counter-max? :not-aborted? :not-started?}}
    :counting {:intents {:decr #{:launched :counting}
                          :abort #{:aborted}}
                :preds #{:started?}
                :effects #{:count-down}}
    :aborted  {:preds #{:aborted?}}
    :launched {:preds #{:counter-zero?}}})

  (def context
    {:effects {:db {:counter 0 :started? true}}
     :coeffects {:db {}
                 :event [:decr]
                 ::state :counting
                 ::flow rocket}})

  (defmethod accept? :counter-max? [_ {:keys [next-db]}]
    (= (:counter next-db) 10))

  (defmethod accept? :not-aborted? [_ {:keys [next-db]}]
    (false? (:aborted? next-db)))

  (defmethod accept? :aborted? [_ {:keys [next-db]}]
    (true? (:aborted? next-db)))

  (defmethod accept? :not-started? [_ {:keys [next-db]}]
    (false? (:started? next-db)))

  (defmethod accept? :started? [_ {:keys [next-db]}]
    (true? (:started? next-db)))

  (defmethod accept? :counter-zero? [_ {:keys [next-db]}]
    (zero? (:counter next-db)))

  (defmethod effects :count-down [_ _]
    {:dispatch [:decr]})

  (-> context
      (transition)
      (next-effects)
      (assoc-db-state))
  )
