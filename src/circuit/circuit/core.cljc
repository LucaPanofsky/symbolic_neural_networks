(ns circuit.core
  (:require [circuit.protocol :as protocol]
            [circuit.structure :as structure]
            [circuit.equilibrium :as equilibrium]
            [circuit.information :as information]))

(defn symbolic-apply [t & args] (cons t args))

(defrecord SymbolicNeuron [_name _sources _to _action _strategy]
  protocol/ITag
  (tag [_this] _name)
  protocol/ISymbolicNeuron
  (arg-cells [_this] _sources)
  (to-cell [_this] _to)
  (implement [this impl] (assoc this :_action impl))
  (obey [_ circuit]
    (let [args (map (comp protocol/content (partial protocol/get-cell circuit)) _sources)
          action-fn (or _action (partial symbolic-apply _name))
          increment (try (apply action-fn args)
                         (catch #?(:clj Exception :cljs js/Object) e
                           (information/->contradiction
                            (.getMessage e)
                            {:tag _name
                             :sources _sources
                             :to _to
                             :args args})))]
      (-> circuit
          (protocol/get-cell _to)
          (protocol/merge-information increment _name))))
  (activate [_ circuit]
    (let [args (map (comp protocol/content (partial protocol/get-cell circuit)) _sources)]
      (if (some protocol/nothing? args)
        (protocol/get-cell circuit _to)
        (let [action-fn (or _action (partial symbolic-apply _name))
              increment (try (apply action-fn args)
                             (catch #?(:clj Exception :cljs js/Object) e
                               (information/throw-contradiction-exception
                                e
                                {:tag _name
                                 :sources _sources
                                 :to _to
                                 :args args})))]
          (-> circuit
              (protocol/get-cell _to)
              (protocol/merge-information increment _name)))))))

(defrecord SymbolicRule [_name _sources _to _action _strategy _rule]
  protocol/ITag
  (tag [_this] _name)
  protocol/ISymbolicNeuron
  (arg-cells [_this] _sources)
  (to-cell [_this] _to)
  (implement [this impl] (assoc this :_action impl))
  (activate [this circuit]
    (_rule this circuit)))

(defrecord SymbolicCell [_name _content _signals]
  protocol/IMerge
  (merge-information [this increment signal]
    (let [merged (information/generic-merge-bit _content increment)
          merged (if (protocol/contradiction? merged)
                   (assoc merged :_data {:coordinate {:tag signal}})
                   merged)]
      (-> this
          (assoc :_content merged)
          (update :_signals conj signal))))
  protocol/ITag
  (tag [_this] _name)
  protocol/ISymbolicCell
  (assume [this bit]
    (assoc this :_content bit))
  (assume [this bit signal]
    (-> this
        (assoc :_content bit)
        (update :_signals conj signal)))
  (content [_] _content)
  (signals [_] _signals))

(defn cell
  "Create a symbolic cell"
  ([_name])
  ([_name thing]
   (->SymbolicCell cell thing #{})))

(defrecord SymbolicCircuit [_name _neurons _cells _structure _order _solution]
  #?(:clj clojure.lang.IFn :cljs IFn)

  #?(:clj
     (invoke [this]
             (this
              (into {}
                    (map (juxt protocol/tag protocol/content))
                    (protocol/cells this))))
     :cljs (-invoke [this]
                    (this
                     (into {}
                           (map (juxt protocol/tag protocol/content))
                           (protocol/cells this)))))
  #?(:clj (invoke [this arg-map]
                  (into {}
                        (map (juxt protocol/tag protocol/content))
                        (-> (_solution this arg-map) (protocol/cells))))
     :cljs (-invoke [this arg-map]
                    (into {}
                          (map (juxt protocol/tag protocol/content))
                          (-> (_solution this arg-map) (protocol/cells)))))
  #?(:clj (invoke [this k v] (this (hash-map k v)))
     :cljs (-invoke [this k v] (this (hash-map k v))))
  #?(:clj (invoke [this a b c d] (this (hash-map a b c d)))
     :cljs (-invoke [this a b c d] (this (hash-map a b c d))))
  #?(:clj (invoke [this a b c d e f] (this (hash-map a b c d e f)))
     :cljs (-invoke [this a b c d e f] (this (hash-map a b c d e f))))
  #?(:clj (invoke [this a b c d e f g h] (this (hash-map a b c d e f g h)))
     :cljs (-invoke [this a b c d e f g h] (this (hash-map a b c d e f g h))))
  #?(:clj (invoke [this a b c d e f g h i l] (this (hash-map a b c d e f g h i l)))
     :cljs (-invoke [this a b c d e f g h i l] (this (hash-map a b c d e f g h i l))))
  #?(:clj (applyTo [this args] (this (apply hash-map args))))

  protocol/ITag
  (tag [_] _name)
  protocol/IGame
  (learn [this facts] (_solution this facts))
  (mechanism [this impls]
    (update
     this
     :_neurons
     update-vals
     (fn [neuron]
       (let [impl (or (get impls (protocol/tag neuron))
                      (get impls (keyword (protocol/tag neuron))))]
         (if impl
           (protocol/implement neuron impl)
           (information/throw-contradiction
            (information/->contradiction
             (str "Implementation not found for neuron: " (protocol/tag neuron))
             {:mechanism impls})))))))
  (solution [_this solution-concept]
    (new SymbolicCircuit
         _name _neurons _cells _structure _order solution-concept))
  protocol/IAbstractCircuit
  (order [_] _order)
  (structure [_] _structure)

  protocol/IOrganism
  (neurons [_] (vals _neurons))
  (cells [_] (vals _cells))

  (acknowledge [this cell]
    (assoc-in
     this
     [:_cells (protocol/tag cell)] cell))
  (get-neuron [_this t] (get _neurons t))
  (get-cell [_this t] (get _cells t))
  (assimilate [this facts]
    (update
     this
     :_cells
     update-vals
     (fn [cell]
       (let [fact (or (get facts (protocol/tag cell))
                      (get facts (keyword (protocol/tag cell))))]
         (if fact
           (protocol/assume cell fact)
           cell)))))
  (add-cell [this cell]
    (assoc-in this [:_cells (protocol/tag cell)] cell))
  (add-neuron [this neuron]
    (assoc-in this [:_neurons (protocol/tag neuron)] neuron)))

(defn make-neuron [tag & cells]
  (let [out            (last cells)
        instance       (->SymbolicNeuron
                        tag
                        (butlast cells)
                        out
                        nil
                        nil)]
    (fn join-into
      ([] (join-into {}))
      ([circuit]
       (reduce
        (fn [circuit cell]
          (protocol/add-cell circuit (->SymbolicCell cell protocol/nothing #{})))
        (protocol/add-neuron circuit instance)
        cells)))))

(defn make-rule [rule]
  (fn  [tag & cells]
    (let [out            (last cells)
          instance       (->SymbolicRule
                          tag
                          (butlast cells)
                          out
                          nil
                          nil
                          rule)]
      (fn join-into
        ([] (join-into {}))
        ([circuit]
         (reduce
          (fn [circuit cell]
            (protocol/add-cell circuit (->SymbolicCell cell protocol/nothing #{})))
          (protocol/add-neuron circuit instance)
          cells))))))

(def empty-symbolic-circuit
  (->SymbolicCircuit protocol/nothing {} {} (make-hierarchy) nil equilibrium/topological-order))

(defn make-abstract-circuit
  [& neurons]
  (letfn [(join [a f] (f a))
          (make-game [neurons]
            (reduce
             join
             empty-symbolic-circuit
             neurons))]
    (-> neurons
        make-game)))

(defn learn-structure [network]
  (-> network
      (assoc
       :_structure
       (structure/make-structure (protocol/neurons network)))))

(defn learn-order [network]
  (-> network
      (assoc
       :_order
       (structure/make-order network))))

(defn symbolic-neural-network [_name & neurons]
  (-> (apply make-abstract-circuit neurons)
      (assoc :_name _name)
      (learn-structure)
      (learn-order)))


(comment
  "Improve make-rule api
   (make-rule [rule-name activate? activate-fn not-activate-fn])"

  " there are 3 kind of rules: 
    - behavioral, depends only on args
    - rational, depends on circuit
    - strategic, depends on circuit and preferences")

(def test-rule
  "I do nothing special but I log myself"
  (make-rule
   (fn [neuron circuit]
     (println "Test rule is deciding ...")
     (let [args (map (comp protocol/content (partial protocol/get-cell circuit)) (protocol/arg-cells neuron))]
       (if (some protocol/nothing? args)
         (protocol/get-cell circuit (protocol/to-cell neuron))
         (let [action-fn (partial symbolic-apply (protocol/tag neuron))
               increment (apply action-fn args)]
           (-> circuit
               (protocol/get-cell (protocol/to-cell neuron))
               (protocol/merge-information increment (protocol/tag neuron)))))))))