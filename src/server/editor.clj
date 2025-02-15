(ns server.editor
  (:require [hiccup2.core :as hiccup]
            [clojure.string :as string]
            [hiccup.page :as hiccup-page]
            [garden.core :as garden]))


(def scripts
  (list
   [:script {:src "https://unpkg.com/htmx.org@2.0.4"}]
   [:script {:src "https://unpkg.com/hyperscript.org@0.9.14"}]
   [:script
    {:type "module"}
    "import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs';
     mermaid.initialize({ startOnLoad: false });
     window.mermaid = mermaid
     window.drawDiagram = async function (element, text) {
       const { svg } = await mermaid.render('diagram', text);
       element.innerHTML = svg;
     };"]))

(def editor-page-style
  (garden/css
   ["body.grid-body"
    {:display "grid"
     :margin 0
     :grid-template-rows "10% 85% 5%"}]
   [:header 
    {:padding "5px"}]
   [:footer 
    {:padding "5px"
     :text-align "center"
     :padding-top "15px"
     :margin-inline "10px"
     :border-top "1px solid black"
     :font-size "0.85rem"}]
   ["#main"
    {:display "flex"
     :align-items "center"
     :justify-content "center"
     :padding "10px"}]
   [:.block
    {:padding "5px"}]
   ["section.container"
    {:display "grid"
     :grid-template-rows "auto auto"
     :width "100%"
     :margin 0
     :height "-webkit-fill-available"
     :gap "5px"}]))

(def head
  [:head
   [:style editor-page-style]
   [:link
    {:rel "stylesheet",
     :href
     "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.18/codemirror.min.css",
     :integrity
     "sha512-uf06llspW44/LZpHzHT6qBOIVODjWtv4MxCricRxkzvopAlSWnTf6hpZTFxuuZcuNE9CBQhqE0Seu1CoRk84nQ==",
     :crossorigin "anonymous",
     :referrerpolicy "no-referrer"}]])

(defn mermaid-diagram [mermaid-string]
  [:pre
   {:_ "on load log event then wait 1s mermaid.init()" #_(format "on load drawDiagram(me, '%s')" mermaid-string)
    :class "mermaid",
    :id "diagram"}
   mermaid-string])

(defn editor-page []
  (list
   head
   [:body.grid-body
    [:header
     [:h1 "Abstract Circuits: symbolic neural networks"]]
    [:main
     {:id "main"}
     [:section
      {:style
       {:width "100%"
        :height "100%"
        :display "grid"
        :grid-template-rows "0.5fr 1fr"}}
      [:section.block
       {:id "editor-row"}
       [:div
        {:style {:padding-block "10px"
                 :display "flex"
                 :gap "5px"}}
        [:button.button {:id "reset"} [:code "reset"]]
        [:button.button
         {:id "apply"
          :hx-post "/symbolic-neural-networks/compile-neural-network"
          :hx-vals "js:{value: editor.getValue()}"
          :hx-target "#diagram"}
         [:code "apply"]]
        [:button "Evaluate"]
        [:button "Boolean Function"]]
       [:textarea {:name "editor", :id "code", :rows 6 :style {:width "100%" :resize "vertical"}}]]
      [:section.block
       {:id "diagram-row"}
       [:pre {:class "mermaid", :id "diagram"}]]]]
    [:footer "Abstract circuits | Symbolic Neural Networks"]
    scripts]))

(defn render-editor-page []
  (hiccup-page/html5
   {:lang "en"}
   (editor-page)))