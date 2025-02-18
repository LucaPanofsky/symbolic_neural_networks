(ns circuit.interpreter
  (:require [instaparse.core :as insta]
            [circuit.core :as core]))

(def circuit-grammar
  "
  circuit = circuit-name? (neuron (<newline> neuron)*)
  circuit-name = <'circuit:'> <whitespace> symbol <newline>
  neuron = <whitespace>? symbol <'('> inputs <')'> <whitespace>? <arrow> <whitespace>? symbol
  <inputs> = symbol (<whitespace>? <','> <whitespace>? symbol)*
  newline = #'\\n'
  whitespace = #'\\s+'
  arrow = '->'
  <symbol> = #'[a-zA-Z0-9!?\\-_.@#\\/]+'
  ")

(def parser (insta/parser circuit-grammar))

(def neuron core/make-neuron)
(def circuit core/symbolic-neural-network)

(defn read-circuit [c-string]
  (let [parsed (parser c-string)
        neurons (map (fn [n] (apply neuron (map symbol (rest n)))) (rest parsed))]
    (apply circuit (cons 'editor-circuit neurons))))

(read-circuit
 "bar/yolo(a, b) -> c
  yin(c, d) -> e
  yang(d, e) -> f")

(read-circuit
 "bar(a, b) -> c
      yin(c, d) -> e
      yang(d, e) -> f
      user@domain(input1, input2, input3) -> output#123")

(comment
  (def circuit-str
    "f(x, y) -> z
g(q, m) -> z
k(m, x) -> y")

  (parser circuit-str)

  "This works fine, I just want to add a YAML front matter"
  "Example:
   ---
   name: my-circuit
   solution: common-knowledge
   ---
   foo(a,b) -> c
   ...")