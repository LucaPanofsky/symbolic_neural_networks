(ns decision
  "Dev namespace to refactor decision graph"
  (:require [circuit.protocol :as protocol]
            [circuit.core :as core]
            [circuit.structure :as structure]
            [circuit.equilibrium :as equilibrium]
            [clojure.set :as set]
            [clojure.pprint]
            [clojure.string] :reload))

(def circuit core/symbolic-neural-network)
(def neuron core/make-neuron)
(def switch core/make-switch)
(def inverter core/make-inverter)
(def and-node core/make-and)

(
 (circuit 
  'dev 
  (and-node 'x 'y 'z))
 {'x true 'y false})

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

(defn the-future [ordering x]
  (rest (drop-while #(not= % x) ordering)))

(defn the-past [ordering x]
  (take-while #(not= % x) ordering))


;; First version, seems ok
;; Need refactoring for performances
(defn learn-decision-diagram [circuit]
  (let [ordering (structure/dev-order-variables
                  circuit
                  (fn [circuit] (fn [c] (not= 'constant (protocol/type-of (protocol/get-cell circuit c))))))
        outcomes (set (filter (fn [x] (structure/outcome-variable? circuit x)) ordering))]
    (letfn [(outcome-variable? [x] (contains? outcomes x))
            (connect [graph child parent port]
              (assoc-in graph [parent :_children port] child))
            (add [graph node] (assoc graph (:_id node) node))
            (model [circuit] (:_cells circuit))
            (query-id [node]
              (let [m    (:_model node)
                    past (->> (the-past ordering (:_tag node))
                              (map (fn [x] [x (protocol/content (get m x))]))
                              (into {}))]
                (-> node (assoc :_id {:variable (:_tag node) :path past}))))
            (node-stack [node]
              (let [children (keys (:_children node))]
                (for [c children]
                  [(:_id node) c])))
            (boolean-node [circuit variable]
              (let [m  (model circuit)
                    x (protocol/content (get m variable))
                    s? (protocol/something? (protocol/content (get m variable)))]
                (-> {:_tag variable
                     :_node-type 'boolean
                     :_model m
                     :_children (if s?
                                  (cond (true? x)
                                        {:true nil}
                                        (false? x)
                                        {:false nil}
                                        :else {:true nil
                                               :false nil})
                                  #_{:true nil
                                     :false nil}
                                  {:nothing nil
                                   :true nil
                                   :false nil})}
                    (query-id))))
            (neuron-node [circuit variable]
              (let [m (model circuit)]
                (-> {:_tag variable
                     :_node-type 'neuron
                     :_model m
                     :_children {:nothing nil
                                 :something nil}}
                    (query-id))))
            (outcome-node [circuit variable]
              (let [m (model circuit)]
                (-> {:_tag variable
                     :_node-type 'outcome
                     :_model m
                     :outcome true
                     :_value (protocol/content (get m variable))}
                    (query-id))))
            (make-node [circuit  variable]
              (let [c (protocol/get-cell circuit variable)]
                (cond
                  (= (protocol/type-of c) 'boolean)
                  (boolean-node circuit variable)
                  (outcome-variable? variable)
                  (outcome-node circuit variable)
                  :else
                  (neuron-node  circuit variable))))
            (learn-process [stack graph]
              (cond
                (nil? (seq stack))
                graph
                :else
                (let [first-stack (first stack)

                      parent-node  (get graph (first first-stack))
                      parent-tag   (:_tag parent-node)
                      parent-model (get parent-node :_model)

                      possible-writers (->> (protocol/neurons (assoc circuit :_cells parent-model))
                                            (filter (fn [n] (= (protocol/to-cell n) parent-tag)))
                                            (map protocol/tag)
                                            (into #{}))

                      variables-that-depend-on-me (->> (descendants (protocol/structure circuit) parent-tag)
                                                       (keep (fn [d] (protocol/get-cell circuit d)))
                                                       (remove (fn [c] (= 'constant (protocol/type-of c))))
                                                       (map protocol/tag))
                      model-hp   (reduce
                                  (fn [cells x]
                                    (update cells x (fn [y] (-> (protocol/assume y 'nothing)
                                                                (assoc :_signals #{})))))
                                  (-> (case (second first-stack)
                                        :nothing (update parent-model parent-tag protocol/assume 'nothing)
                                        :true (update parent-model parent-tag protocol/assume true possible-writers)
                                        :false (update parent-model parent-tag protocol/assume false possible-writers)
                                        :something (update parent-model parent-tag protocol/assume parent-tag)))
                                  variables-that-depend-on-me)

                      assumption   (update-vals model-hp protocol/content)

                      next-variable (->> (the-future ordering parent-tag)
                                         (filter (fn [c] (protocol/nothing? (get assumption c))))
                                         (first))]
                ;; instead of model use cell directly 
                ;; i need to cash CN, in this way i have the signals
                  (if next-variable
                    (let [next-node (make-node
                                     (equilibrium/common-knowledge
                                      (assoc circuit :_cells model-hp)
                                      assumption)
                                     next-variable)

                          _ (let [m (:_model next-node)]
                              (when (some protocol/contradiction? (map protocol/content (vals m)))
                                (println "CONTRADICTION" m)
                                (println "C.ASSUMPTION" assumption)))
                          next-stack (node-stack next-node)


                          new-graph
                          (if (get graph (:_id next-node))
                            graph
                            (-> graph
                                (connect (:_id next-node) (:_id parent-node) (second first-stack))
                                (add next-node)))]
                      (learn-process
                       (concat next-stack (rest stack))
                       new-graph))
                    (do (println "Next variable not found, acknowledge outcome and next stack element")
                        (println "Parent were" parent-tag (:_id parent-node))
                        (let [equilibrium (equilibrium/common-knowledge
                                           (assoc circuit :_cells model-hp)
                                           assumption)

                              outcomes (keep (fn [[k v]] (when (outcome-variable? k)
                                                           (when (protocol/something? (protocol/content v))
                                                             v))) (:_cells equilibrium))

                              c* (assoc circuit :_cells (:_cells equilibrium))

                              outcome-nodes (for [o outcomes]
                                              (outcome-node c* (protocol/tag o)))

                              my-last (->> (the-future ordering parent-tag)
                                           (first))

                              next-node (make-node
                                         (equilibrium/common-knowledge
                                          (assoc circuit :_cells model-hp)
                                          assumption)
                                         my-last)]
                          (if (outcome-variable? my-last)
                            (learn-process (rest stack)
                                           (-> graph
                                               (connect (:_id next-node) (:_id parent-node) (second first-stack))
                                               (add next-node)))
                            (learn-process (rest stack)
                                           (reduce
                                            (fn [g o]
                                              (-> g
                                                  (connect (:_id o) (:_id parent-node) (second first-stack))
                                                  (add o)))
                                            graph
                                            outcome-nodes)))))))))]
      (let [vo (structure/dev-order-variables
                circuit
                (fn [circuit] (fn [c] (not= 'constant (protocol/type-of (protocol/get-cell circuit c))))))
            outcomes (set (filter (fn [x] (structure/outcome-variable? circuit x)) vo))
            rn (into {} (map-indexed (fn [idx i] [i idx]) vo))
            first-variable (first vo)
            first-node     (make-node circuit first-variable)
            stack          (node-stack first-node)]
        (println "outcomes" outcomes)
        (learn-process
         stack
         {(:_id first-node) first-node})))))

(defn remove-paren [x]
  (->
   (clojure.string/replace x "(" "")
   (clojure.string/replace ")" "")))

(def tree-1
  (->
   (learn-decision-diagram
    x-circuit-1)))

(defn debug-with-mermaid [tree]
  (letfn [(id-fn [node] (symbol (str "id_" (hash (:_id node)))))
          (pre-process-0 [nodes]
            (map (fn [n]
                   (-> (dissoc n :_model)
                       (update :_value (fn [v] (if v v (protocol/content (get (:_model n) (:_tag n))))))

                       (assoc :id (id-fn n))))
                 nodes))
          (render-node [n]
            (str (:id n)
                 "["
                 "<div>variable " (:_tag n) "</div>"
                 "<div>value " (remove-paren (str (:_value n))) "</div>"
                 "]"))
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


(defn simplify-outcomes [tree]
  (let [nodes (vals tree)
        outcomes (filter :outcome nodes)
        by-value (group-by :_value outcomes)

        new-outcomes (-> by-value
                         (update-keys #(str "outcome_" (hash %)))
                         (update-vals (fn [x]
                                        (-> (first x)
                                            (assoc :_id (str "outcome_" (hash (:_value (first x)))))))))

        index (->> outcomes
                   (map (fn [n] (assoc n :outcome_id (str "outcome_" (hash (:_value n))))))
                   (map (juxt :_id :outcome_id))
                   (into {}))]
    (->> nodes
         (remove :outcome)
         (map (fn [n]
                (update
                 n
                 :_children
                 update-vals
                 (fn [id]
                   (or (get index id) id)))))
         (concat (vals new-outcomes))
         (map (juxt :_id identity))
         (into {}))))


(defn simplify-nodes [tree]
  (let [nodes (vals tree)
        desc  (->> nodes
                   (group-by :_children)
                   (keep (fn [[k v]]
                           (when (and k (> (count v) 1))
                             [k v])))
                   (map (fn [[k v]]
                          (let [remove-set (set (map :_id v))
                                id (str "id_" (hash remove-set))]
                            {:remove remove-set
                             :translate (into {} (map (fn [x] [(:_id x) id]) v))
                             :new-node (-> (first v)
                                           (assoc :_id (str "id_" (hash (map :_id v)))))}))))
        plan       (first desc)#_(reduce
                    (fn [a x]
                      (-> a
                          (update :remove set/union (:remove x))
                          (update :translate merge (:translate x))
                          (update :new-node conj (:new-node x))))
                    {:remove #{}
                     :translate {}
                     :new-node []}
                    desc)]
    (println "new node?"
             (:new-node plan))
    
    (->> nodes
         (remove #(contains? (:remove plan) (:_id %)))
         (map (fn [n]
                (-> n
                    (update :_children update-vals (fn [x] (or (get (:translate plan) x) x))))))
         #_(concat [(:new-node plan)])
         (map (juxt :_id identity))
         (into {}))))

(def xtest
  (->> (simplify-outcomes tree-1)
       (simplify-nodes)))

xtest

(comment
  #?(:clj
     (spit "tree.md"
           (clojure.string/join
            "\n"
            ["```mermaid"
             "graph TB"
             (debug-with-mermaid xtest)
             "```"]))))
