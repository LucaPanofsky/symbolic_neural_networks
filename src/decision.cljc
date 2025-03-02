(ns decision
  "Dev namespace to refactor decision graph"
  (:require [circuit.protocol :as protocol]
            [circuit.core :as core]
            [circuit.structure :as structure]
            [circuit.equilibrium :as equilibrium]
            [clojure.set :as set]
            [clojure.pprint]
            [clojure.string]))

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

(defn the-future [ordering x]
  (rest (drop-while #(not= % x) ordering)))

(defn the-past [ordering x]
  (take-while #(not= % x) ordering))

(defn can-rationally-activate?
  "A Neuron can activate if and only if"
  [circuit n]
  (let [neuron     (protocol/get-neuron circuit n)
        to         (protocol/get-cell circuit (protocol/to-cell neuron))
        args       (map (comp protocol/content (partial protocol/get-cell circuit)) (protocol/arg-cells neuron))
        rational   (fn [] (and
                           (not (contains? (protocol/signals to) n))
                           (every? protocol/something? args)))
        ;; type rationality
        t-rational (fn [] (cond
                            (= (protocol/type-of n) 'switch) (true? (first args))
                            :else true))]
    (and
     (t-rational)
     (rational))))

(defn learn-decision-diagram [circuit]
  (letfn [(connect [graph child parent port]
            (assoc-in graph [parent :_children port] child))
          (add [graph node] (assoc graph (:_id node) node))
          (model [circuit] (:_cells circuit))
          (query-id [node ordering]
            (let [m    (:_model node)
                  past (->> (the-past ordering (:_tag node))
                            (map (fn [x] [x (protocol/content (get m x))]))
                            (into {}))]
              (-> node (assoc :_id {:variable (:_tag node) :path past}))))
          (i-know-i-am-outcome [parent-id graph]
            (update-in graph [parent-id :outcome] (constantly true)))
          (node-stack [node]
            (let [children (keys (:_children node))]
              (for [c children]
                [(:_id node) c])))
          (boolean-node [circuit ordering variable]
            (let [m  (model circuit)
                  s? (protocol/something? (protocol/content (get m variable)))]
              (-> {:_tag variable
                   :_node-type 'boolean
                   :_model m
                   :_children (if s?
                                {:true nil
                                 :false nil}
                                {:nothing nil
                                 :true nil
                                 :false nil})}
                  (query-id ordering))))
          (neuron-node [circuit ordering variable]
            (let [m (model circuit)]
              (-> {:_tag variable
                   :_node-type 'neuron
                   :_model m
                   :_children {:nothing nil
                               :something nil}}
                  (query-id ordering))))
          (make-node [circuit ordering variable]
            (let [c (protocol/get-cell circuit variable)]
              (if (= (protocol/type-of c) 'boolean)
                (boolean-node circuit ordering variable)
                (neuron-node  circuit ordering variable))))
          (learn-process [ordering outcomes stack graph]
            (cond
              (nil? (seq stack))
              graph
              :else
              (let [first-stack (first stack)
                    #_#__ (println "Running stack:" first-stack)
                    parent-node  (get graph (first first-stack))
                    parent-tag   (:_tag parent-node)
                    parent-model (get parent-node :_model)
                    possible-writers (->> (protocol/neurons circuit)
                                          (filter (fn [n] (= (protocol/to-cell n) parent-tag)))
                                          (map protocol/tag)
                                          (into #{}))

                    model-hp   (-> (case (second first-stack)
                                     :nothing (update parent-model parent-tag protocol/assume 'nothing)
                                     :true (update parent-model parent-tag protocol/assume true possible-writers)
                                     :false (update parent-model parent-tag protocol/assume false possible-writers)
                                     :something (update parent-model parent-tag protocol/assume parent-tag possible-writers)))

                    assumption   (update-vals model-hp protocol/content)

                    #_#__ (println "Assumption" assumption)
                    next-variable (->> (the-future ordering parent-tag)
                                       (filter (fn [c] (protocol/nothing? (get assumption c))))
                                       (first))]
                ;; instead of model use cell directly 
                ;; i need to cash CN, in this way i have the signals
                (if next-variable
                  (do #_(println "Next variable found" next-variable)
                   (let [next-node (make-node
                                    (equilibrium/common-knowledge
                                     (assoc circuit :_cells model-hp)
                                     assumption)
                                    ordering
                                    next-variable)

                         _ (let [m (:_model next-node)]
                             (when (some protocol/contradiction? (map protocol/content (vals m)))
                               (println "CONTRADICTION" m)
                               (println "C.ASSUMPTION" assumption)))
                         next-stack (node-stack next-node)
                         new-graph
                         (-> graph
                             (connect (:_id next-node) (:_id parent-node) (second first-stack))
                             (add next-node))]
                     #_(println "RFemaining stack" (rest stack))
                     #_(println "New stack:" next-stack)
                     (learn-process
                      ordering
                      outcomes
                      (concat next-stack (rest stack))
                      new-graph)))
                  (do (println "Next variable not found, acknowledge outcome and next stack element")
                      (learn-process ordering outcomes (rest stack) (i-know-i-am-outcome (:_id parent-node) graph)))))))]
    (let [vo (structure/dev-order-variables
              circuit
              (fn [circuit] (fn [c] (not= 'constant (protocol/type-of (protocol/get-cell circuit c))))))
          outcomes (set (filter (fn [x] (structure/outcome-variable? circuit x)) vo))
          rn (into {} (map-indexed (fn [idx i] [i idx]) vo))
          first-variable (first vo)
          first-node     (make-node circuit vo first-variable)
          stack          (node-stack first-node)]
      (println "outcomes" outcomes)
      (learn-process
       vo
       outcomes
       stack
       {(:_id first-node) first-node}))))

(structure/variables-topological-order x-circuit-1)

(def tree-1
  (learn-decision-diagram
   x-circuit-1))

(clojure.pprint/pprint
 (->> (vals tree-1)
      (map (fn [n]
             (-> (dissoc n :_model)
                 (assoc :_value (get (:_model n) (:_tag n))))))))

(defn debug-with-mermaid [tree]
  (letfn [(id-fn [node] (symbol (str "id_" (hash (:_id node)))))
          (pre-process-0 [nodes]
            (map (fn [n]
                   (-> (dissoc n :_model)
                       (assoc :_value (get (:_model n) (:_tag n)))
                       (assoc :id (id-fn n))))
                 nodes))
          (render-node [n]
            (str (:id n) "[ variable " (:_tag n) " ]"))
          (render-children [n]
            (let [p-id (:id n)]
              (for [[c v] (:_children n)]
                (when v (str p-id "-- " (name c) " -->" "id_" (hash v))))))
          (render-mermaid-node [n]
            (->> (concat [(render-node n)] (render-children n))
                 (clojure.string/join "\n")))]
    (->> (pre-process-0 (vals tree))
         (map render-mermaid-node)
         (clojure.string/join "\n"))))


(map :id
     (debug-with-mermaid tree-1))
(comment
  #?(:clj
     (spit "tree.md"
           (clojure.string/join
            "\n"
            ["```mermaid"
             "graph TB"
             (debug-with-mermaid tree-1)
             "```"]))))