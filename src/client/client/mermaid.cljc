(ns client.mermaid
  (:require
   [circuit.protocol :as protocol]
   [clojure.string :as string]))

(defn draw-circuit [circuit]
  (letfn [(render-arg-link [c]
            (if (protocol/something? (protocol/content (protocol/get-cell circuit c)))
              " -.-> "
              " --> "))
          (render-out-link [n]
            (let [args (map (comp protocol/content (partial protocol/get-cell circuit)) (protocol/arg-cells n))]
              (cond 
                (= 'switch (protocol/type-of n))
                (if (and (true? (first args)) (every? protocol/something? args))
                  " -.-> "
                  " --> ")
                (= 'or-switch (protocol/type-of n))
                (if (some true? args)
                  " -.-> "
                  " --> ")
                :else
                (if (every? protocol/something? args)
                  " -.-> "
                  " --> "))))
          (render-cell-class [c]
            (if (protocol/something? (protocol/content (protocol/get-cell circuit c)))
              (if (protocol/contradiction? (protocol/content (protocol/get-cell circuit c)))
                ":::contradiction"
                (if (false? (protocol/content (protocol/get-cell circuit c)))
                  ":::false"
                  (if (true? (protocol/content (protocol/get-cell circuit c)))
                    ":::true"
                    ":::info")))
              ":::empty"))
          (render-neuron-class [n]
            (let [args (map (comp protocol/content (partial protocol/get-cell circuit)) (protocol/arg-cells n))]
              (cond 
                (= 'switch (protocol/type-of n))
                (if (and (every? protocol/something? args) (true? (first args)))
                  (str (protocol/tag n) ":::info")
                  (str (protocol/tag n) ":::empty"))
                (= 'or-switch (protocol/type-of n))
                (if (some true? args)
                  (str (protocol/tag n) ":::info")
                  (str (protocol/tag n) ":::empty"))
                :else
                (if (every? protocol/something? args)
                  (str (protocol/tag n) ":::info")
                  (str (protocol/tag n) ":::empty")))))
          (render-neuron-shape [n]
            (cond 
              (= (protocol/type-of n) 'switch)
              (str (protocol/tag n) "@{ shape: curv-trap, label: 'SWITCH'}")
              (= (protocol/type-of n) 'or-switch)
              (str (protocol/tag n) "@{ shape: hex, label: 'OR SWITCH'}")
              (= (protocol/type-of n) 'and)
              (str (protocol/tag n) "@{ shape: delay, label: 'AND'}")
              (= (protocol/type-of n) 'inverter)
              (str (protocol/tag n) "@{ shape: tri, label: 'NOT'}")
              :else (str (protocol/tag n) "[/" (protocol/tag n) "/]")))]
    (let [neurons (protocol/neurons circuit)
          graph   (mapcat
                   (fn [n]
                     (let [args (for [i (protocol/arg-cells n)]
                                  (str i (render-arg-link i) (protocol/tag n)))
                           out  (str (protocol/tag n) (render-out-link n) (protocol/to-cell n))]
                       (cons
                        (render-neuron-shape n)
                        (cons (render-neuron-class n) (cons out args)))))
                   neurons)
          cells   (->> (map protocol/tag (protocol/cells circuit))
                       (map (fn [c] (str c "(" c ")" (render-cell-class c)))))
          diagram (string/join "\n" (concat graph cells))]
      (str
       "\nflowchart LR\n\n"
       diagram
       "\nlinkStyle default stroke:gray,stroke-width:1px,color:red;\n"))))



(defn draw-decision-diagram [index]
  (letfn
   [(render-outcome-value [node]
      (if (seq (:value node))
        (string/join ", " (map name (:value node)))
        "nothing"))
    (render-outcome-node [node]
      (str (:id node) "[" (render-outcome-value node) "]"))
    (render-variable-node [node]
      (str
       (:id node) "((" (:variable node) "))" "\n"
       (:id node) " --- "  (first (:children node)) "\n"
       (:id node) " -.- " (second (:children node)) "\n"))]
    (let [nodes (->> (vals index)
                     (map
                      (fn [node]
                        (if (:outcome node)
                          (render-outcome-node node)
                          (render-variable-node node))))
                     (string/join "\n"))
          result (str
                  "\nflowchart TB\n\n"
                  nodes
                  "\nlinkStyle default stroke:gray,stroke-width:1px,color:red;\n")]
      result)))