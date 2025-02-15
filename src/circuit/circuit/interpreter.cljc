(ns circuit.interpreter
  (:require [instaparse.core :as insta]))

(def circuit-grammar
  "
  circuit = circuit-name? (neuron (<newline> neuron)*)
  circuit-name = <'circuit:'> <whitespace> symbol <newline>
  neuron = <whitespace>? symbol <'('> inputs <')'> <whitespace>? <arrow> <whitespace>? symbol
  <inputs> = symbol (<whitespace>? <','> <whitespace>? symbol)*
  newline = #'\\n'
  whitespace = #'\\s+'
  arrow = '->'
  <symbol> = #'[a-zA-Z0-9!?\\-_.@#]+'
  ")

(comment
  (def parser (insta/parser circuit-grammar))


  (def circuit-str
    "bar(a, b) -> c
     yin(c, d) -> e
     yang(d, e) -> f
     user@domain(input1, input2, input3) -> output#123")

  (parser circuit-str)

  "This works fine, I just want to add a YAML front matter"
  "Example:
   ---
   name: my-circuit
   solution: common-knowledge
   ---
   foo(a,b) -> c
   ...")