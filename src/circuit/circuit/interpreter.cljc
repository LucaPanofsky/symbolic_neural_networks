(ns circuit.interpreter
  (:require [instaparse.core :as insta]
            [circuit.core :as core]
            [clojure.string :as string]))

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
(def switch core/make-switch)
(def circuit core/symbolic-neural-network)
(def inverter core/make-inverter)
(def and-node core/make-and)
(def or-node core/make-or-switch)

(defn switch? [n] (string/starts-with? (second n) "switch"))
(defn inverter? [n] (string/starts-with? (second n) "inverter"))
(defn and? [n] (string/starts-with? (second n) "and"))
(defn or? [n] (string/starts-with? (second n) "or"))

(defn read-neuron [n]
  (cond
    (switch? n) (apply switch (map symbol (drop 2 n)))
    (inverter? n) (apply inverter (map symbol (drop 2 n)))
    (and? n) (apply and-node (map symbol (drop 2 n)))
    (or? n) (apply or-node (map symbol (drop 2 n)))
    :else (apply neuron (map symbol (rest n)))))

(defn read-circuit [c-string]
  (let [parsed (parser c-string)
        neurons (map read-neuron (rest parsed))]
    (apply circuit (cons 'editor-circuit neurons))))


(comment
  (def circuit-str
    "f(x, y) -> z
g(q, m) -> z
switch(m, x) -> y")

  (read-circuit circuit-str)

  "This works fine, I just want to add a YAML front matter"
  "Example:
   ---
   name: my-circuit
   solution: common-knowledge
   ---
   foo(a,b) -> c
   ...")

(defn separe-front-matter
  "Returns a map
   ```
   {:front-matter ... 
    :document ...}
   ```
   The front matter is any content of the form 
   ```
   ---
   contents ..
   ---
   ```"
  [document]
  (let [front-matter-pattern #"(?s)^---\n(.*?)\n---\n"
        match (re-find front-matter-pattern document)]
    (when match
      (let [front-matter (-> match (second))]
        {:front-matter front-matter
         :document (-> (string/replace document "---" "")
                       (string/replace front-matter "")
                       (string/trim))}))))


(def document
  "---
title: My Awesome Post
author: John Doe
tags:
  - ClojureScript
  - YAML
---
This is the content of the post.
It can have multiple lines.
")


(separe-front-matter document)

(def example-front-matter-circuit
  "---
name: my-circuit
solution: common-knowledge
---
foo(a,b) -> c")

(separe-front-matter example-front-matter-circuit)