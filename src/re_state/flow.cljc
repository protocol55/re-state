(ns re-state.flow)

(defn get-transition-predicates [flow intent state]
  (get-in flow [state :preds]))

(defn get-next-effects [flow intent state]
  (get-in flow [state :effects]))

(defn get-next-states [flow state intent]
  (get-in flow [state :intents intent]))

(defn- merge-state-vals [l r]
  (cond
    (map? r)
    (merge-with into l r)

    :else
    (into l r)))

(defn merge-flows
  [& flows]
  (apply merge-with (partial merge-with merge-state-vals) flows))
