(ns server.mermaid
  (:require
   [circuit.protocol :as protocol]
   [clojure.string :as string]))

(defn draw-circuit [circuit]
  (let [neurons (protocol/neurons circuit)
        graph   (mapcat
                 (fn [n]
                   (let [args (for [i (protocol/arg-cells n)] (str i " --> " (protocol/tag n)))
                         out  (str (protocol/tag n) " --> " (protocol/to-cell n))]
                     (cons out args)))
                 neurons)
        cells   (->> (map protocol/tag (protocol/cells circuit))
                     (map (fn [c] (str c "(" c ")"))))
        diagram (string/join "\n" (concat graph cells))]
    (str
     "%%{init: {\"flowchart\": {\"htmlLabels\": false, \"defaultRenderer\": \"elk\"}} }%% \n flowchart LR\n"
     diagram
     "\n linkStyle default stroke:black,stroke-width:1px,color:red;\n    classDef default fill:#f9f,stroke:#333,stroke-width:2px;")))