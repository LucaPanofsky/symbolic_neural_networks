(ns client.template
  (:require [clojure.string :as string]))

(defn neo-brutal-button
  ([attr & contents]
   [:button.neo-brutal-btn
    attr
    contents]))

(defn neo-brutal-text-area [attr]
  [:textarea.neo-textarea
   (merge
    {:placeholder "f(x, y) -> z"}
    attr)])

(defn neo-brutal-switch [attr]
  [:div.range-container
   [:label [:div.text (get attr :id)]
    [:input
     {:type "range"
      :_ "on change me.setAttribute('switch', event.target.value) 
         then send solveCircuit(value: #signals as Values) to document"
      :id (get attr :id),
      :name (get attr :id)
      :min "0",
      :max "1",
      :step "1",
      :value "0"}]
    [:select
     {:name (str "content__" (get attr :id))
      :_ "on change send solveCircuit(value: #signals as Values) to document"}
     [:option {:value (get attr :id)} (get attr :id)]
     [:option {:value "true"} "true"]
     [:option {:value "false"} "false"]]]])

(defn signals [& switches]
  [:div
   {:class "neo-box-content signals"
    :id "signals"}
   switches])

(defn footer []
  [:footer.footer
   [:div.title-2 "About Organism"]
   [:p.intro-p "work in progress " [:a {:href "https://github.com/LucaPanofsky/symbolic_neural_networks"} "GitHub"]]
   [:div.title-3 "Language"]
   [:pre.example
    (string/join "\n"
                 ["neuron-1(arg_1, ..., arg_n) -> output"
                  "\n. . .\n"
                  "neuron-m(arg_1, ..., arg_l) -> output"])]
   [:p.explain-p "Each neuron must produce exactly one output."]
   [:br]
   [:div.title-3 "switch, inverter"]
   [:pre.example
    (string/join
     "\n"
     ["inverter(p-is-true) -> p-is-false"
      "switch(p-is-true, t-label) -> rule"
      "switch(p-is-false, f-label) -> rule"
      "inverter(p-is-false) -> p-is-true"])]
   [:p.explain-p "The switch neuron activates if and only if inputs are sufficiently informative and we know that the first value is true."]])

(defn page []
  [:html
   [:meta {:charset "UTF-8"}]
   [:meta {:http-equiv "X-UA-Compatible", :content "IE=edge"}]
   [:meta
    {:name "viewport", :content "width=device-width, initial-scale=1.0"}]
   [:link {:rel "stylesheet", :href "css/main.css"}]
   [:link {:rel "stylesheet", :href "css/reset.css"}]
   [:title "Browser Starter"]
   [:header [:h1.title "Organisms: symbolic neural networks"]]
   [:main
    [:section
     {:class "sidebar"}
     [:div
      {:class "box box1"}
      [:div
       {:class "button-group"}
       (neo-brutal-button
        {:_ "on click send resetCircuit to document"}
        "reset")
       (neo-brutal-button
        {:_ "on click get the next <textarea/> 
             send compileCircuit(value: result.value) to document 
             end"}
        "draw circuit")]
      (neo-brutal-text-area {})]
     [:div
      {:class "box box2"}
      [:p "Signals"]
      [:div
       {:class "neo-box-content signals"
        :id "signals"}]]]
    [:section.content-column
     [:div.column-buttons
      [:div (neo-brutal-button
             {:_ "on click send compileBooleanFormula to document end"}
             "Decision Diagram")]
      [:div (neo-brutal-button
             {:_ "on click send exportCircuit to document end"}
             "Export")]]
     [:section {:class "content"}
      [:div.diagram-container
       [:pre {:id "diagram"}]]]]]
   (footer)
   [:script {:src "https://cdn.jsdelivr.net/npm/mermaid@11.4.1/dist/mermaid.min.js"}]
   [:script {:src "https://unpkg.com/hyperscript.org@0.9.14"}]
   [:script {:src "https://unpkg.com/idiomorph@0.7.1"}]
   [:script {:src "js/main.js"}]])