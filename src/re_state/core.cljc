(ns re-state.core
  (:require [clojure.set :refer [rename-keys]]
            [re-frame.core :as rf]
            [re-state.util :as util]))

(defn get-intent [context]
  (get-in context [:coeffects :event 0]))

(defn get-state [context]
  (get-in context [:coeffects ::state]))

(def effects-keys-alias
  {:db :next-db ::state :next-state})

(def coeffects-keys-alias
  {:event :event :db :prev-db ::state :prev-state})

(defn context->rs-context
  [context]
  (merge (rename-keys (:effects   context) effects-keys-alias)
         (rename-keys (:coeffects context) coeffects-keys-alias)))

(defn make-accept? [context]
  (let [rs-context (context->rs-context context)
        tpm (get-in context [:coeffects ::flow :tpm])]
    (fn [intent state]
      (util/accept? (util/get-tpred tpm intent state) rs-context))))

(defn get-effects [context]
  (let [rs-context (context->rs-context context)
        tem (get-in context [:coeffects ::flow :tem])
        intent (get-intent context)
        next-state (get-in context [:effects ::state])]
    ((util/get-effects-fn tem intent next-state) rs-context)))

(defn get-next-states [context]
  (get-in context [:coeffects
                   ::flow :fsm
                   (get-state context)
                   (get-intent context)]))

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
                            :tpm {:entry {:route (constantly true)}}
                            :tem {:entry {:route (constantly {:dispatch [:load-entries]})}}}}})

  (-> context
      (transition)
      (next-effects)
      (assoc-db-state)))

