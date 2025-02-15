(ns client.core
  (:require [circuit.protocol :as protocol]
            [circuit.core :as core]
            [circuit.equilibrium :as equilibrium]
            [circuit.interpreter :as interpreter]))

;; start is called by init and after code reloading finishes
(defn ^:dev/after-load start []
  (js/console.log "start")
  (js/console.log
   (interpreter/parser
    "bar(a, b) -> c
     yin(c, d) -> e
     yang(d, e) -> f")))

(defn init []
  ;; init is called ONCE when the page loads
  ;; this is called in the index.html and must be exported
  ;; so it is available even in :advanced release builds
  (js/console.log "init")
  (start))

;; this is called before any code is reloaded
(defn ^:dev/before-load stop []
  (js/console.log "stop"))

(comment
  (def neuron core/make-neuron)
  (def circuit core/symbolic-neural-network)
  (def dev-circuit
    (circuit
     'foo
     (neuron 'bar 'a 'b 'c)
     (neuron 'yin 'c 'd 'e)
     (neuron 'yang 'd 'e 'f)))
  (dev-circuit)
  (apply dev-circuit ['a 'a 'b 'b 'd 'd])
  (dev-circuit {'a :a 'b :b 'd :d}))