(ns re-state.spec
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as st]
            [re-state.core :as rs]))

(s/def ::event vector?)

(s/def ::context (s/keys :req-un [::event]))

(s/fdef rs/get-tpred
        :args (s/cat :tpm map? :intent keyword? :state keyword?))

(s/fdef rs/accept?
        :args (s/cat :tpred (s/or :fn fn?
                                  :fns (s/every fn?)
                                  :bool boolean?)
                     :context map?))
