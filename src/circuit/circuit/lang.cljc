(ns circuit.lang
  "Work in progress.
   This name space should contain public APIs and shall be the entry point of the library.
   
   At the moment, it collects examples and  repl tests."
  (:require [circuit.protocol :as protocol]
            [circuit.core :as core]
            [circuit.equilibrium :as equilibrium]
            [clojure.set :as set]))

;; ---- Public APIs -----------------------------------------

(def neuron core/make-neuron)
(def circuit core/symbolic-neural-network)

(comment
  (def dev-circuit
    (circuit
     'foo
     (neuron 'bar 'a 'b 'c)
     (neuron 'yin 'c 'd 'e)
     (neuron 'yang 'd 'e 'f)))

  (dev-circuit)
  (apply dev-circuit ['a 'a 'b 'b 'd 'd])
  (dev-circuit {'a :a 'b :b 'd :d})

  (def test-rule-circuit
    (circuit
     'foo
     (neuron 'bar 'a 'b 'c)
     (core/test-rule 'yin 'c 'd 'e)
     (neuron 'yang 'd 'e 'f)))

  (def test-common-knowledge-circuit
    (-> (circuit
         'foo
         (neuron 'fn-a 'a 'b 'c)
         (neuron 'fn-b 'b 'c 'd)
         (neuron 'fn-c 'a 'd 'O))
        (protocol/solution equilibrium/common-knowledge)
        (protocol/mechanism
         {'fn-a (fn [& args] (println "Running fn-a!") (into [:fn-a] args))
          'fn-b  (fn [& args] (println "Running fn-b!") (into [:fn-b] args))
          'fn-c  (fn [& args] (println "Running fn-c!") (into [:fb-c] args))})))

  (test-common-knowledge-circuit 'a 'a 'b 'b)

  (equilibrium/common-knowledge
   (-> (circuit
        'foo
        (neuron 'fn-a 'a 'b 'c)
        (neuron 'fn-b 'b 'c 'd)
        (neuron 'fn-c 'a 'd 'O))
       (protocol/mechanism
        {'fn-a (fn [& args] (println "Running fn-a!") (into [:fn-a] args))
         'fn-b  (fn [& args] (println "Running fn-b!") (into [:fn-b] args))
         'fn-c  (fn [& args] (println "Running fn-c!") (into [:fb-c] args))}))
   {'a 'a 'd 'd})


  ;; This shows that normal DAG works fine
  (equilibrium/boolean-function
   (circuit
    'foo
    (neuron 'fn-a 'a 'b 'c)
    (neuron 'fn-b 'b 'c 'd)
    (neuron 'fn-c 'a 'd 'O)))

  ;; This shows that I handle sinks 
  (equilibrium/boolean-function
   (circuit
    'bidirectional
    (neuron 'backend-1 'processed-a 'b 'c)
    (neuron 'backend-2 'a 'c 'd)
    (neuron 'alert-that-d-is-ready 'd 'the-side-effect-has-happened)
    (neuron 'alert-that-a-is-processed 'processed-a 'the-side-effect-has-happened)
    (neuron 'process-a 'a 'processed-a)
    (neuron 'some-process 'c 'd 'e)))

  ;; This shows that I handle sinks and sinks ...
  (equilibrium/boolean-function
   (circuit
    'bidirectional
    (neuron 'backend-1 'processed-a 'b 'c)
    (neuron 'backend-2 'a 'c 'd)
    (neuron 'alert-that-d-is-ready 'd 'the-side-effect-has-happened)
    (neuron 'alert-that-a-is-processed 'processed-a 'the-side-effect-has-happened)
    (neuron 'another-sink 'the-side-effect-has-happened 'my-new-sink)
    (neuron 'process-a 'a 'processed-a)
    (neuron 'some-process 'c 'd 'e)))

 ;; This shows that bidirectional constraint k1 are handled correctly  
 ;; Need to test that the logic is robust for more complex constrained problem
 ;; I think it is but need test 
  (equilibrium/boolean-function
   (circuit
    'bidirectional
    (neuron 'plus-straight 'a 'b 'c)
    (neuron 'minus-constraint-a 'c 'b 'a)
    (neuron 'minus-constraint-b 'c 'a 'b)))

  (def test-common-knowledge-circuit-2
    (-> (circuit
         'bidirectional
         (neuron 'plus 'a 'b 'c)
         (neuron 'minus-constraint-a 'c 'b 'a)
         (neuron 'minus-constraint-b 'c 'a 'b))
        (protocol/solution equilibrium/common-knowledge)
        (protocol/mechanism
         {'plus +
          'minus-constraint-a  -
          'minus-constraint-b  -})))

  (test-common-knowledge-circuit 'a 'a)
  (test-common-knowledge-circuit-2 'a 1  'c 4)

  (def pipeline-circuit
    (circuit
     'bidirectional
     (neuron 'backend-1 'processed-a 'b 'c)
     (neuron 'backend-2 'a 'c 'd)
     (neuron 'alert-that-d-is-ready 'd 'the-side-effect-has-happened)
     (neuron 'alert-that-a-is-processed 'processed-a 'the-side-effect-has-happened)
     (neuron 'process-a 'a 'processed-a)
     (neuron 'some-process 'c 'd 'e)))

  (pipeline-circuit 'a :a 'b :b)
  "We cannot merge side effects
    either we return nothing or we create a specific data structure so that
    you can merge information into it"

  (def implemented-pipeline-1
    (protocol/mechanism
     pipeline-circuit
     {'backend-1
      (fn [& args] (into [:backend-1] args))
      'backend-2
      (fn [& args] (into [:backend-2] args))
      'alert-that-d-is-ready
      (fn [& args] (println "d is ready!" args) protocol/nothing)
      'alert-that-a-is-processed
      (fn [& args] (println "a is processed!" args) protocol/nothing)
      'process-a (fn [& args] (into [:process-a] args))
      'some-process (fn [& args] (into [:some-process] args))}))

  (implemented-pipeline-1 'a :a 'b :b)

  (def faulty-pipeline-1
    (protocol/mechanism
     pipeline-circuit
     {'backend-1
      (fn [& args] (into [:backend-1] args))
      'backend-2
      (fn [& args] (into [:backend-2] args))
      'alert-that-d-is-ready
      (fn [& args] (println "d is ready!" args) protocol/nothing)
      'alert-that-a-is-processed
      (fn [& args]
        (println "a is processed!" args) protocol/nothing)
      'process-a (fn [& args] (println "Process a will throw") (/ 1 0))
      'some-process (fn [& args] (into [:some-process] args))}))

  #_(try
      (faulty-pipeline-1 'a :a 'b :b)
      (catch Exception e (ex-data e)))


  (def faulty-pipeline-2
    (protocol/mechanism
     pipeline-circuit
     {'backend-1
      (fn [& args] (into [:backend-1] args))
      'backend-2
      (fn [& args] (println "Backend 2 will throw") (/ 1 0))
      'alert-that-d-is-ready
      (fn [& args] (println "d is ready!" args) protocol/nothing)
      'alert-that-a-is-processed
      (fn [& args]
        (println "a is processed!" args) protocol/nothing)
      'process-a (fn [& args] (into [:process-a] args))
      'some-process (fn [& args] (into [:some-process] args))}))

  #_(try

      (faulty-pipeline-2 'a :a 'b :b)
      (catch Exception e (ex-data e))))


#_(def tree
  {:variable 'a,
   :true   {:variable 'b,
            :true {:outcome true, :value ['fn-a 'fn-b 'fn-c]},
            :false {:variable 'd, :true {:outcome true, :value ['fn-c]}, :false {:outcome true, :value []}}},
   :false
   {:variable 'b,
    :true {:variable 'c, :true {:outcome true, :value ['fn-b]}, :false {:outcome true, :value []}},
    :false {:outcome true, :value []}}})


