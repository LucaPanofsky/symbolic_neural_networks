(ns decision
  "Dev namespace to refactor decision graph"
  (:require [circuit.protocol :as protocol]
            [circuit.core :as core]
            [circuit.structure :as structure]
            [circuit.equilibrium :as equilibrium]
            [clojure.set :as set]))

(def circuit core/symbolic-neural-network)
(def neuron core/make-neuron)
(def switch core/make-switch)
(def inverter core/make-inverter)

(defn infer-boolean-cells [circuit]
  (let [switches (->> (filter #(= 'switch (protocol/type-of %)) (protocol/neurons circuit))
                      (map (fn [n] (first (protocol/arg-cells n))))
                      (into #{}))
        inverter (->> (filter #(= 'inverter (protocol/type-of %)) (protocol/neurons circuit))
                      (mapcat (fn [n] (protocol/arg-cells n)))
                      (into #{}))]
    (reduce
     (fn [c b-cell]
       (update-in c [:_cells b-cell] protocol/assume-type 'boolean))
     circuit
     (set/union switches inverter))))

(comment
  (def x-circuit-0
    (circuit
     'x-circuit-0
     (neuron 'predicate 'c 't 'p-is-true)
     (inverter 'p-is-true 'p-is-false)
     (switch 'p-is-true 't-label 'rule)
     (switch 'p-is-false 'f-label 'rule)))

  (def x-circuit-1
    (->
     x-circuit-0
     infer-boolean-cells
     (protocol/types
      {'t-label {:type 'constant :content "label-if-true"}
       'f-label {:type 'constant :content "label-if-false"}})))

  (x-circuit-1 't 't 'c 'c)
  
  (protocol/order x-circuit-1))

(def dev-circuit
  (circuit
   'rule-circuit
   (neuron 'predicate 'c 't 'predicate-is-true)
   (neuron 'cond 'predicate-is-true 't-label 'f-label 'rule)))

(protocol/structure dev-circuit)

(def dev-circuit-2
  (circuit
   'rule-circuit
   (neuron 'predicate 'c 't 'predicate-is-true)
   (switch 'predicate-is-true 't-label 'rule)))

(def dev-circuit-2-k1
  (-> dev-circuit-2
      (protocol/types
       {'c
        {:type 'constant :content "yes"}
        't-label
        {:type 'constant :content "label-if-yes"}
        'predicate-is-true
        'boolean})
      (protocol/mechanism
       {'predicate =})))

(dev-circuit-2-k1 {'t "yessd"})

dev-circuit-2

(comment
  "To do: 
   - lift cond and switch to first class citizen"
  "This clearly works, next we want to refactor the code generating the decision graph")
(def dev-circuit-k1
  (-> dev-circuit
      (protocol/types
       {'c
        {:type 'constant :content "yes"}
        't-label
        {:type 'constant :content "label-if-yes"}
        'f-label
        {:type 'constant :content "label-if-false"}
        'predicate-is-true
        'boolean})
      (protocol/mechanism
       {'predicate =
        'cond (fn [test then else] (if (true? test) then else))})))

(comment
  (-> (protocol/cells dev-circuit-k1))
  (dev-circuit-k1 {'c :some-c 't :some-t}))

(defn boolean-branch? [cell]
  (= (protocol/type-of cell) 'boolean))

(defn decision-graph-order [circuit]
  (structure/order-variables
   circuit
   (fn [_circuit]
     (let [h (protocol/structure _circuit)]
       (fn [cell]
         (and
          (not= (protocol/type-of cell) 'constant)
          (seq (descendants h (protocol/tag cell)))
          #_(not (structure/independent-variable? circuit (protocol/tag cell)))))))))

(defn learn-decision-diagram [circuit make-variable-order]
  (letfn [(learn-process [stack graph]
            (cond 
              (nil? (seq stack))
              graph 
              :else 
              (println "Running stack:" stack)))]
    (let [vo (make-variable-order circuit)
          rn (into {} (map-indexed (fn [idx i] [i idx]) vo))
          initial-condition
          {:variable-ordering vo
           :ranking rn
           :circuit circuit
           :decision-graph {}}]
      initial-condition)))

(structure/variables-topological-order x-circuit-1)

(learn-decision-diagram
 x-circuit-1
 decision-graph-order)

(sort [123 2 9 0 16 4 7])