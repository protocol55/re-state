(ns re-state.flow)

(defn get-transition-predicates [flow intent state]
  (get-in flow [:tpm state intent]))

(defn get-next-effects [flow intent state]
  (get-in flow [:tem state intent]))

(defn get-next-states [flow state intent]
  (get-in flow [:fsm state intent]))

(defn merge-flows
  [& flows]
  (apply merge-with (partial merge-with (partial merge-with into)) flows))
