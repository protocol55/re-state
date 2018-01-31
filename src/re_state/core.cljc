(ns re-state.core)

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
