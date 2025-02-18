(ns client.engine
  (:require
   [clojure.walk]
   [clojure.string :as string]
   [circuit.interpreter :as interpreter]
   [circuit.core :as core]
   [circuit.equilibrium :as equilibrium]
   [client.mermaid :as mermaid-diagram]
   [client.template :as template]
   [circuit.protocol :as protocol]
   [crate.core :as crate]))

(defn flatten-boolean-network [tree]
  (let [id-   (atom 0)
        index (atom {})]
    (letfn [(outcome? [node] (and (map? node) (:outcome node)))
            (graph-node? [node] (and (map? node) (:true node) (:false node)))
            (->graph-node [thing]
              (cond
                (graph-node? thing)
                (assoc thing
                       :id (str (swap! id- inc) "-" (:variable thing))
                       :children [(:id (:true thing)) (:id (:false thing))])
                (outcome? thing)
                (assoc thing :id (str (swap! id- inc) "-outcome"))
                :else thing))]
      (clojure.walk/postwalk
       (fn [node]
         (let [x (->graph-node node)]
           (when (or (graph-node? x) (outcome? x))
             (let [index-thing (select-keys x [:id :variable :children :outcome :value])]
               (swap! index assoc (:id index-thing) index-thing)))
           x))

       tree)
      @index)))

(defn compress-outcomes [index]
  (let [outcomes        (filter :outcome (vals index))
        outcomes-index  (-> (group-by :value outcomes)
                            (update-vals (fn [x] (str "outcome_" (hash (into #{} (map :id x)))))))]
    (reduce
     (fn [a v]
       (if (:outcome v)
         (let [id (get outcomes-index (:value v))]
           (assoc a id (assoc v :id id)))
         (assoc a
                (:id v)
                (update v :children
                        (fn [children]
                          (mapv
                           (fn [c]
                             (if (clojure.string/includes? c "-outcome")
                               (let [original (get index c)
                                     value    (:value original)
                                     new-id   (get outcomes-index value)]
                                 new-id)
                               c))
                           children))))))
     {}
     (vals index))))

(comment "Patch, the entire algorithm should be reviewed")
(defn compress-nodes [index]
  (let [identicals (filter
                    #(> (count %) 1)
                    (-> (group-by :children (remove :outcome (vals index)))
                        (dissoc nil)
                        vals))
        translate-identicals (reduce merge
                                     (map
                                      (fn [p]
                                        (let [new-id (string/join "-" (map :id p))]
                                          {(:id (first p))  new-id
                                           (:id (second p)) new-id}))
                                      identicals))
        to-remove (into #{} (mapcat #(map :id %) identicals))
        cleaned   (reduce
                   (fn [a v]
                     (if
                      (to-remove (:id v))
                       a
                       (assoc
                        a
                        (:id v)
                        (update
                         v
                         :children
                         (fn [children]
                           (mapv
                            (fn [c] (if-let [t (get translate-identicals c)]
                                      t
                                      c))
                            children))))))
                   {}
                   (vals index))]
    (reduce
     (fn [a v]
       (let [new-id (string/join "-" (map :id v))
             new-v  (-> (first v)
                        (assoc :id new-id))]
         (assoc
          a
          new-id
          new-v)))
     cleaned
     identicals)))

(defn iterate-compress [index]
  (let [result (compress-nodes index)]
    (if (= (count result) (count index))
      result
      (recur result))))

(defn to-decision-diagram [circuit]
  (-> circuit
      equilibrium/boolean-function
      flatten-boolean-network
      compress-outcomes
      iterate-compress))

(def state (atom {}))

(def neuron core/make-neuron)
(def circuit core/symbolic-neural-network)

(defn get-instance! [] (get (deref state) :instance))
(defn get-boolean! []  (get (deref state) :boolean))
(defn put-instance! [instance]
  (swap! state assoc :instance instance :boolean nil))
(defn put-boolean! [boolean]
  (swap! state assoc :boolean boolean)
  boolean)
(defn get-state! [] (deref state))

(defn signals [circuit]
  (let [cells (protocol/cells circuit)]
    (into (template/signals)
          (for [c cells]
            (template/neo-brutal-switch
             {:id (protocol/tag c)})))))

(defn render-diagram! [{:keys [instance new]}]
  (when instance
    (let [mermaid-string (mermaid-diagram/draw-circuit instance)
          elt            (js/document.getElementById "diagram")]
      (.removeAttribute ^js elt "data-processed")
      (set! ^js elt -innerHTML mermaid-string)
      (-> (.run js/window.mermaid  #js{:querySelector "#diagram"})
          (.then (fn [x]
                   (.removeAttribute elt "decision-diagram")
                   (when new
                     (let [signals-elt (crate/html (template/signals (signals instance)))
                           dom         (js/document.getElementById "signals")]
                       (js/Idiomorph.morph
                        dom
                        signals-elt)
                       (.processNode js/window._hyperscript (js/document.getElementById "signals"))
                       (put-instance! instance)))))))))

(defn render-decision-diagram! [{:keys [instance]}]
  (when instance
    (let [mermaid-string (mermaid-diagram/draw-decision-diagram instance)
          elt            (js/document.getElementById "diagram")]
      (.removeAttribute ^js elt "data-processed")
      (set! ^js elt -innerHTML mermaid-string)
      (-> (.run js/window.mermaid  #js{:querySelector "#diagram"})
          (.then (fn [_] (.setAttribute elt "decision-diagram" "true")))))))

(defn handler-compile-circuit [^js event]
  (let [circuit-string (string/trim (str (.. event -detail -value)))
        instance       (try
                         (interpreter/read-circuit circuit-string)
                         (catch js/Object e
                           (js/console.warn "Exception:" e)))]
    (js/console.log "Instance:\n" instance)
    (render-diagram! {:instance instance :new true})))

(defn handler-solve-circuit [^js event] 
  (let [values (js->clj (.. event -detail -value))
        model  (into {} (keep (fn [[k v]] (when (= v "1") [(symbol k) (symbol k)])) values))
        solution (equilibrium/common-knowledge (get-instance!) model)]
    (js/console.log "Model:" model)
    (js/console.log "Solution:" solution)
    (render-diagram! {:instance solution})))

(defn handler-compile-boolean-formula [^js event]
  (let [current-state (deref state)]
    (if (:boolean current-state)
      (do
        (js/console.log "Decision diagram" (:boolean current-state))
        (render-decision-diagram! {:instance (:boolean current-state)}))
      (if (:instance current-state)
        (let [dd (put-boolean! (to-decision-diagram (:instance current-state)))]
          (js/console.log "Decision diagram:" dd)
          (render-decision-diagram! {:instance dd}))
        (js/console.warn "No instance available.")))))

(defn handler-reset-editor [^js _event]
  (reset! state {})
  (set! (.. ^js (js/document.body.querySelector "textarea.neo-textarea") -value) "")
  (let [d (js/document.getElementById "diagram")]
    (.removeAttribute d "data-processed")
    (set! (.. ^js d -innerHTML) ""))
  (let [d (js/document.getElementById "signals")]
    (set! (.. ^js d -innerHTML) "")))

(defn download-file [filename content]
  (let [blob (js/Blob. #js [content] #js {:type "text/plain"})
        url (js/URL.createObjectURL blob)
        a   (js/document.createElement "a")]
    (set! (.-href a) url)
    (set! (.-download a) filename)
    (.appendChild js/document.body a)
    (.click a)
    (.remove a)
    (js/URL.revokeObjectURL url)))

(defn handler-expor-circuit [^js _event]
  (let [content (str (-> (get-state!)
                         (update :instance dissoc :_solution)))]
    (download-file "editor-circuit.edn" content)))

(def event-dispatch
  {"compileCircuit"        handler-compile-circuit
   "solveCircuit"          handler-solve-circuit
   "compileBooleanFormula" handler-compile-boolean-formula
   "resetCircuit"          handler-reset-editor
   "exportCircuit"         handler-expor-circuit})