(ns circuit.core
  (:require [circuit.protocol :as protocol]
            [circuit.structure :as structure]
            [circuit.equilibrium :as equilibrium]
            [circuit.information :as information]
            [clojure.set :as set]))

(defn symbolic-apply [t & args] (cons t args))

(defn switch-apply [test if-test-true]
  (if (true? test)
    if-test-true
    protocol/nothing))

(defn can-switch-activate? [test if-test-true]
  (and (true? test) (protocol/something? if-test-true)))

(defrecord SymbolicNeuron [_name _type-of _sources _to _action _strategy]
  protocol/IType-of
  (type-of [_this] _type-of)
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
                            #?(:clj (.getMessage e) :cljs (str e))
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

(defrecord SymbolicSwitch [_name _type-of _sources _to]
  protocol/IType-of
  (type-of [_this] _type-of)
  protocol/ITag
  (tag [_this] _name)
  protocol/ISymbolicNeuron
  (arg-cells [_this] _sources)
  (to-cell [_this] _to)
  (obey [_ circuit]
    (let [args (map (comp protocol/content (partial protocol/get-cell circuit)) _sources)
          increment (switch-apply (first args) (second args))]
      (-> circuit
          (protocol/get-cell _to)
          (protocol/merge-information increment _name))))
  (activate [_ circuit]
    (let [args (map (comp protocol/content (partial protocol/get-cell circuit)) _sources)]
      (if (can-switch-activate? (first args) (second args))
        (let [increment (switch-apply (first args) (second args))]
          (-> circuit
              (protocol/get-cell _to)
              (protocol/merge-information increment _name)))
        (protocol/get-cell circuit _to)))))

(defrecord SymbolicInverter [_name _type-of _sources _to]
  protocol/IType-of
  (type-of [_this] _type-of)
  protocol/ITag
  (tag [_this] _name)
  protocol/ISymbolicNeuron
  (arg-cells [_this] _sources)
  (to-cell [_this] _to)
  (obey [_ circuit]
    (let [args (map (comp protocol/content (partial protocol/get-cell circuit)) _sources)
          increment (not (first args))]
      (-> circuit
          (protocol/get-cell _to)
          (protocol/merge-information increment _name))))
  (activate [_ circuit]
    (let [args (map (comp protocol/content (partial protocol/get-cell circuit)) _sources)]
      (if (some protocol/nothing? args)
        (protocol/get-cell circuit _to)
        (let [increment (not (first args))]
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

(defn cell-merge-info- [this increment signal]
  (let [merged (information/generic-merge-bit (protocol/content this) increment)
        merged (if (protocol/contradiction? merged)
                 (assoc merged :_data {:coordinate {:tag signal}})
                 merged)]
    (if signal
      (protocol/assume this merged signal)
      (protocol/assume this merged))))

(defrecord SymbolicCell [_name _content _signals _type-of]
  protocol/IType-of
  (type-of [_this] _type-of)
  (assume-type [this this-type]
    (if (map? this-type)
      (-> this
          (assoc :_type-of (:type this-type))
          (assoc :_content (or (:content this-type) _content)))
      (-> this
          (assoc :_type-of this-type))))
  protocol/IMerge
  (merge-information [this increment signal]
    (cell-merge-info- this increment signal))
  protocol/ITag
  (tag [_this] _name)
  protocol/ISymbolicCell
  (assume [this bit] (assoc this :_content bit))
  (assume [this bit signal]
    (if (set? signal)
      (-> this
          (assoc :_content bit)
          (update :_signals set/union signal))
      (-> this
          (assoc :_content bit)
          (update :_signals conj signal))))
  (content [_] _content)
  (signals [_] _signals))

(defn cell
  "Create a symbolic cell"
  ([_name] (cell _name protocol/nothing))
  ([_name thing]
   (->SymbolicCell _name thing #{} nil)))

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
  (types [this impls]
    (update
     this
     :_cells
     update-vals
     (fn [cell]
       (let [impl (or (get impls (protocol/tag cell))
                      (get impls (keyword (protocol/tag cell))))]
         (if impl
           (protocol/assume-type cell impl)
           cell)))))
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
           neuron)))))
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
       (let [fact (get facts (protocol/tag cell))]
         (if (not (nil? fact))
           (protocol/assume cell fact)
           cell)))))
  (add-cell [this cell-thing]
    (if (ident? cell-thing)
      (assoc-in this [:_cells cell-thing] (cell cell-thing))
      (assoc-in this [:_cells (protocol/tag cell-thing)] cell-thing)))
  (add-neuron [this neuron]
    (assoc-in this [:_neurons (protocol/tag neuron)] neuron)))

(defn make-neuron [tag & cells]
  (let [out            (last cells)
        instance       (->SymbolicNeuron
                        tag
                        'neuron
                        (butlast cells)
                        out
                        nil
                        nil)]
    (fn join-into
      ([] (join-into {}))
      ([circuit]
       (reduce
        (fn [circuit cell]
          (protocol/add-cell circuit cell))
        (protocol/add-neuron circuit instance)
        cells)))))

(defn make-switch [& cells]
  (let [out            (last cells)
        tag            (symbol (str "switch__" (name (second cells)) "__eq__" (name out) "__if__" (name (first cells))))
        instance       (->SymbolicSwitch
                        tag
                        'switch
                        (butlast cells)
                        out)]
    (fn join-into
      ([] (join-into {}))
      ([circuit]
       (reduce
        (fn [circuit cell]
          (protocol/add-cell circuit cell))
        (protocol/add-neuron circuit instance)
        cells)))))

(defn make-inverter [& cells]
  (let [out            (last cells)
        tag            (symbol (str "inverter__" (name (first cells)) "__with__" (name out)))
        instance       (->SymbolicInverter
                        tag
                        'inverter
                        (butlast cells)
                        out)]
    (fn join-into
      ([] (join-into {}))
      ([circuit]
       (reduce
        (fn [circuit cell]
          (protocol/add-cell circuit cell))
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
            (protocol/add-cell circuit (->SymbolicCell cell protocol/nothing #{} nil)))
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

'(def test-rule
   "I do nothing special but I log myself"
   (make-rule
    (fn [neuron circuit]
      (println "Test rule is deciding ...")
      (let [args (map (comp protocol/content (partial protocol/get-cell circuit)) (protocol/arg-cells neuron))]
        (if (test-args neuron circuit)
          (let [action-fn (partial symbolic-apply (protocol/tag neuron))
                increment (apply action-fn args)]
            (-> circuit
                (protocol/get-cell (protocol/to-cell neuron))
                (protocol/merge-information increment (protocol/tag neuron))))
          (protocol/get-cell circuit (protocol/to-cell neuron)))))))