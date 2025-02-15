(ns circuit.structure
  (:require [circuit.protocol :as protocol]))

(defn descend-a-from-b? [h a b] (contains? (descendants h b) a))
(defn has-a-more-ancestors? [h a b] (> (count (ancestors h a)) (count (ancestors h b))))

(defn make-order-comparator
  [circuit]
  (let [h (protocol/structure circuit)]
    (fn [a b]
      (cond
        (descend-a-from-b? h a b) 1
        (descend-a-from-b? h b a) -1
        (has-a-more-ancestors? h a b) -1
        (has-a-more-ancestors? h b a) 1
        :else (compare (name a) (name b))))))

(defn make-structure
  [neurons]
  (letfn [(safe-derive [h t p] (try (derive h t p) (catch Exception _ h)))]
    (reduce
     (fn [h neuron]
       (reduce
        (fn [h i] (safe-derive h (protocol/tag neuron) i))
        (safe-derive h (protocol/to-cell neuron) (protocol/tag neuron))
        (protocol/arg-cells neuron)))
     (make-hierarchy)
     neurons)))

(defn make-order [circuit]
  (into
   (sorted-set-by (make-order-comparator circuit))
   (map
    protocol/tag
    (protocol/neurons circuit))))

(defn variables-topological-order [circuit]
  (into
   (sorted-set-by (make-order-comparator circuit))
   (map
    protocol/tag
    (protocol/cells circuit))))