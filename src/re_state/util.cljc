(ns re-state.util)

(defn accept? [tpred context]
  (cond
    (fn? tpred)
    (tpred context)

    (coll? tpred)
    (every? #(% context) tpred)

    (boolean? tpred)
    tpred))

(defn get-tpred [tpm intent state]
  (get-in tpm [state intent]))

(defn get-effects-fn [tem intent state]
  (get-in tem [state intent] (constantly nil)))

(defn merge-tpms
  ([x] x)
  ([x & tpms]
    (apply merge-with merge x tpms)))

(def merge-tems merge-tpms)

(defn merge-fsms
  ([x] x)
  ([x & fsms]
    (apply merge-with (partial merge-with into) x fsms)))

(defn merge-flows
  ([x] x)
  ([x & flows]
    (reduce (fn [flow [k f]]
              (update flow k (partial apply f)
                      (map k flows)))
            x
            {:fsm merge-fsms :tpm merge-tpms :tem merge-tems})))
