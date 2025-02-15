(ns server.logic
  (:require [server.editor :as editor]
            [circuit.core :as core]
            [server.mermaid :as mermaid]
            [hiccup2.core :as hiccup]))

(defn ->html [form] (str (hiccup/html form)))

(def backend (atom {:network nil :boolean nil}))

(def neuron core/make-neuron)
(def circuit core/symbolic-neural-network)

(defn handler:compile-neural-network [request]
  (let [network (get (-> request :params) "value")
        instance (try (read-string network) (catch Exception e (println e)))
        instance-fn (try (eval (concat (list 'fn ['circuit 'neuron]) (list instance)))
                         (catch Exception e (println e)))
        instance-eval (instance-fn circuit neuron)]
    (if instance-eval
      (do
        (println (mermaid/draw-circuit instance-eval))
        (reset! backend  {:network instance-eval :boolean nil})
        {:status 200
         :body (->html (editor/mermaid-diagram 
                        (mermaid/draw-circuit instance-eval)))})
      {:status 500 :body "Server error."})))