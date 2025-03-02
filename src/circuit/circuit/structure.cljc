(ns circuit.structure
  (:require
   [circuit.protocol :as protocol]
   [circuit.utils :as utils]))

(defn descend-a-from-b? [h a b] (contains? (descendants h b) a))
(defn has-a-more-ancestors? [h a b] (> (count (ancestors h a)) (count (ancestors h b))))

(defn make-order-comparator
  [circuit]
  (let [h (protocol/structure circuit)]
    (fn [a b]
      (cond
        (descend-a-from-b? h a b) 1
        (descend-a-from-b? h b a) -1
        (has-a-more-ancestors? h a b) 1
        (has-a-more-ancestors? h b a) -1
        :else (compare (name a) (name b))))))

(defn make-structure
  [neurons]
  (reduce
   (fn [h neuron]
     (reduce
      (fn [h i] (utils/safe-derive h (protocol/tag neuron) i))
      (utils/safe-derive h (protocol/to-cell neuron) (protocol/tag neuron))
      (protocol/arg-cells neuron)))
   (make-hierarchy)
   neurons))

(defn make-order [circuit]
  (into
   (sorted-set-by (make-order-comparator circuit))
   (map
    protocol/tag
    (protocol/neurons circuit))))

;; this does not work
(defn variables-topological-order [circuit]
  (into
   (sorted-set-by (make-order-comparator circuit))
   (map
    protocol/tag
    (protocol/cells circuit))))

(defn dev-order-variables [circuit select-variable]
  (let [order    (protocol/order circuit)]
    (->> (map (partial protocol/get-neuron circuit) order)
         (mapcat (fn [n] (concat (protocol/arg-cells n) (list (protocol/to-cell n)))))
         (filter (fn [c] ((select-variable circuit) c)))
         (reverse)
         (distinct)
         (reverse))))

(defn independent-variable? [circuit tag]
  (let [h (protocol/structure circuit)
        a (ancestors h tag)]
    (empty? a)))

(defn outcome-variable? [circuit tag]
  (let [h (protocol/structure circuit)
        d (descendants h tag)]
    (empty? d)))

