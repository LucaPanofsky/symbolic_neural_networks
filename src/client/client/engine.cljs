(ns client.engine
  (:require
   [circuit.interpreter :as interpreter]
   [circuit.core :as core]))

(def neuron core/make-neuron)
(def circuit core/symbolic-neural-network)

(defn read-circuit [c-string]
  (let [parsed (interpreter/parser c-string)]))

(interpreter/parser
 "bar(a, b) -> c
     yin(c, d) -> e
     yang(d, e) -> f")