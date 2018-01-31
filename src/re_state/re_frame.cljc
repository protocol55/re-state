(ns re-state.re-frame
  (:require [clojure.set :refer [rename-keys]]
            [re-frame.core :as rf]
            [re-state.core :as rs]))

(defn get-intent [context]
  (get-in context [:coeffects :event 0]))

(defn get-state [context]
  (get-in context [:coeffects ::rs/state]))

(def effects-keys-alias
  {:db :next-db ::rs/state :next-state})

(def coeffects-keys-alias
  {:event :event :db :prev-db ::rs/state :prev-state})

(defn context->rs-context
  [context]
  (merge (rename-keys (:effects   context) effects-keys-alias)
         (rename-keys (:coeffects context) coeffects-keys-alias)))

(defn make-accept? [context]
  (let [rs-context (context->rs-context context)
        tpm (get-in context [:coeffects ::tpm])]
    (fn [intent state]
      (rs/accept? (rs/get-tpred tpm intent state) rs-context))))

(defn get-effects [context]
  (let [rs-context (context->rs-context context)
        tem (get-in context [:coeffects ::tem])
        intent (get-intent context)
        next-state (get-in context [:effects ::rs/state])]
    ((rs/get-effects-fn tem intent next-state) rs-context)))

(defn get-next-states [context]
  (get-in context [:coeffects
                   ::fsm
                   (get-state context)
                   (get-intent context)]))

(defn transition [context]
  {:post [(some? (get-in % [:effects ::rs/state]))]}
  (let [accept? (make-accept? context)
        intent (get-intent context)]
    (assoc-in context [:effects ::rs/state]
              (some #(when (accept? intent %) %)
                    (get-next-states context)))))

(defn next-effects [context]
  (update-in context [:effects] merge (get-effects context)))

(defn assoc-db-state [context]
  (assoc-in context [:effects :db ::rs/state]
            (get-in context [:effects ::rs/state])))

(defn interceptor-handler
  "Takes a re-frame context and returns a new context with potentially a new
  state in effects as well as any other effects the transition requires.
  
  Requires the following to be in coeffects:

  state - The current control state
  fsm   - The finite state machine map
  tpm   - The transition predicate map
  tem   - The transition effects map
  "
  [context]
  (-> context
      (transition)
      (next-effects)
      (assoc-db-state)))

(def interceptor
  [(rf/inject-cofx ::rs/state)
   (rf/inject-cofx ::tem)
   (rf/inject-cofx ::fsm)
   (rf/inject-cofx ::tpm)
   (rf/->interceptor
     :id ::step
     :after #(interceptor-handler %))])

(def app-state (atom nil))

(rf/reg-cofx ::rs/state
             (fn [coeffects]
               (assoc coeffects ::rs/state @app-state)))

(rf/reg-fx ::rs/state
           (fn [value]
             (reset! app-state value)))

(defn reg-fsm [handler]
  (rf/reg-cofx ::fsm
             (fn [coeffects]
               (assoc coeffects ::fsm (handler coeffects)))))

(defn reg-tpm [handler]
  (rf/reg-cofx ::tpm
             (fn [coeffects]
               (assoc coeffects ::tpm (handler coeffects)))))

(defn reg-tem [handler]
  (rf/reg-cofx ::tem
             (fn [coeffects]
               (assoc coeffects ::tem (handler coeffects)))))

(comment
  (def context
    {:effects {:db {}}
     :coeffects {:db {}
                 :event [:route :entry]
                 ::rs/state :ready
                 ::fsm {:ready {:route #{:entry}}}
                 ::tpm {:entry {:route (constantly true)}}
                 ::tem {:entry {:route (constantly {:dispatch [:load-entries]})}}}})

  (-> context
      (transition)
      (next-effects)
      (assoc-db-state)))

