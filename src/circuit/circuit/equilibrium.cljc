(ns circuit.equilibrium
  (:require
   [circuit.protocol :as protocol]
   [circuit.structure :as structure]
   [clojure.set :as set]))

(defn eval-circuit [circuit model-instantiation]
  ;; We reduce circuit by applying 
  ;; neurons in topological order.  
  (reduce
   (fn [circuit neuron]
     (protocol/acknowledge
      circuit
      (protocol/activate neuron circuit)))
   ;; this injects knowledge into cells (model instantiation)
   (protocol/assimilate
    circuit
    model-instantiation)
   (map
    (partial protocol/get-neuron circuit)
    (protocol/order circuit))))

(defn topological-order
  "Evaluate the the game in linear topological order. 
   This is equivalent to the lisp eval path. 
   The solution also replicates the behavior of the circuit in linear time (in the number of neurons)"
  [circuit model-instantiation]
  ;; We reduce circuit by applying 
  ;; neurons in topological order.  
  (reduce
   (fn [circuit neuron]
     (protocol/acknowledge
      circuit
      (protocol/activate neuron circuit)))
   ;; this injects knowledge into cells (model instantiation)
   (protocol/assimilate
    circuit
    model-instantiation)
   (map
    (partial protocol/get-neuron circuit)
    (protocol/order circuit))))

(defn common-knowledge-circuit
  "Returns a circuit storing beliefs about what possible (a set) in 
   
   ```-> thing meta :possible```"
  ([circuit]
   (with-meta
     circuit
     {:possible (protocol/order circuit)}))
  ([circuit model-instantiation]
   (with-meta
     (protocol/assimilate
      circuit
      model-instantiation)
     {:possible (protocol/order circuit)})))

(defn can-activate?
  "A Neuron can activate if and only if"
  [circuit n]
  (let [neuron (protocol/get-neuron circuit n)
        to     (protocol/get-cell circuit (protocol/to-cell neuron))
        args   (map (comp protocol/content (partial protocol/get-cell circuit)) (protocol/arg-cells neuron))]
    (and
     (not (contains? (protocol/signals to) n))
     (every? protocol/something? args))))

(defn common-knowledge
  "WIP: Common Knwoledge solution concept."
  [circuit model-instantiation]
  ;; The common knowledge circuit propagates beliefs about what is possible - :possible in meta
  (let [cn-circuit (common-knowledge-circuit circuit model-instantiation)]
    (letfn [(possible [circuit] (:possible (meta circuit)))
            ;; At any point in time we look for a candidate (a neuron that has not run yet and that can activate)
            ;; If there is such a candidate, we remove it from the possible and we continue our reasoning
            ;; The reasoning stops as soon as we conclude nothing else can be done 
            (foreward-induction [circuit]
              (let [candidates (filter (partial can-activate? circuit) (possible circuit))
                    candidate  (first candidates)]
                (if candidate
                  (recur
                   (with-meta
                     (protocol/acknowledge
                      circuit
                      (protocol/obey (protocol/get-neuron circuit candidate) circuit))
                     {:possible (disj (possible circuit) candidate)}))
                  circuit)))]
      (foreward-induction cn-circuit))))

(comment "To do, refactor me!!!"
         "Specifically, start with a small and fluent API to describe queries over the hierarchy"
         "Rewrite let bindings in a functional way to reuse functions")
(defn some-recursive-contradiction? [circuit cells]
  (let [activated-neurons  (map (partial protocol/get-neuron circuit) (reduce set/union (map protocol/signals cells)))
        contradictory-neurons (filter
                               (fn [n] (let [o (protocol/get-cell circuit (protocol/to-cell n))]
                                         (protocol/contradiction? (protocol/content o))))
                               activated-neurons)
        cn-ancestors       (reduce set/union
                                   (map
                                    (fn [cn] (ancestors (protocol/structure circuit) (protocol/tag cn)))
                                    contradictory-neurons))
        loop? (some true?
                    (map (fn [cn]
                           (contains? cn-ancestors (protocol/tag cn)))
                         contradictory-neurons))]
    (if loop? true false)))

(defn boolean-function
  "Calculate the multivalued boolean function of the network
   
   Return a compressed OBDD using variables topological order and common knowledge."
  [circuit]
  (let [variables (structure/variables-topological-order circuit)]
    (letfn
     [;; Make outcome makes the terminal nodes of the OBDD
      (make-outcome [circuit cells]
        ;; Contradictions may stem from loops or partial information
        ;; if loop? exclude contradictory, else merge
        (if (some-recursive-contradiction? circuit cells)
          {:outcome true
           :value
           (into [] (sort
                     (structure/make-order-comparator circuit)
                     (reduce set/union (map protocol/signals (remove (comp protocol/contradiction? protocol/content) cells)))))}

          {:outcome true
           :value
           (into [] (sort
                     (structure/make-order-comparator circuit)
                     (reduce set/union (map protocol/signals cells))))}))
      ;; True branch expansion
      (true-branch [circuit x variables]
        ;; We assign the new variable and solve the common knowledge game
        ;; then, we make inferences to restrict variables and neurons
        ;; finally we calòl #(decision-node ...) we need to trampoline           
        (let [c (common-knowledge circuit {x x})
              p (:possible (meta c))
              true-values (->> (protocol/cells c)
                               (filter (fn [x] (protocol/something? (protocol/content x))))
                               (map protocol/tag))
              next-variables (reduce disj variables true-values)]
          (if (seq (remove (partial can-activate? circuit) p))
            (if (seq next-variables)
              #(decision-node c next-variables)
              (make-outcome c (protocol/cells c)))
            (make-outcome c (protocol/cells c)))))
      ;; False branch expansion
      (false-branch [circuit x variables]
        ;; Similar to true branch, we need to exclude the false variable from the 
        ;; possible future variables  
        (let [c (common-knowledge circuit {})
              p (:possible (meta c))
              true-values (->> (protocol/cells c)
                               (filter (fn [x] (protocol/something? (protocol/content x))))
                               (map protocol/tag)
                               (cons x))
              next-variables (reduce disj variables true-values)]
          (if (seq (remove (partial can-activate? circuit) p))
            (if (seq next-variables)
              #(decision-node c next-variables)
              (make-outcome c (protocol/cells c)))
            (make-outcome c (protocol/cells c)))))
      ;; This algorithm should be written as a dynamic problem ...
      ;; Here I trampoline the true branch and the false branch until exaustion 
      ;; Then I simplify using a simple rules, identical outcomes can be collapsed.
      (decision-node [circuit variables]
        (let [x (first variables)
              O {:variable x
                 :true (trampoline true-branch circuit x variables)

                 :false (trampoline false-branch circuit x variables)}]
          (if (= (:true O) (:false O))
            (:true O)
            O)))]
      (decision-node
       (common-knowledge-circuit circuit)
       variables))))
