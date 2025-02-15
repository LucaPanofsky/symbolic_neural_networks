(ns client.template)

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

(defn page []
  [:html
   [:meta {:charset "UTF-8"}]
   [:meta {:http-equiv "X-UA-Compatible", :content "IE=edge"}]
   [:meta
    {:name "viewport", :content "width=device-width, initial-scale=1.0"}]
   [:link {:rel "stylesheet", :href "/css/main.css"}]
   [:title "Browser Starter"]
   [:header [:h1 "Header"]]
   [:main
    [:section
     {:class "sidebar"}
     [:div
      {:class "box box1"}
      [:div
       {:class "button-group"}
       (neo-brutal-button {} "reset")
       (neo-brutal-button {} "compile")]
      (neo-brutal-text-area {})]
     [:div
      {:class "box box2"}
      [:div
       {:class "button-group"}
       [:button {:class "neo-brutal-btn"} "Button 1"]
       [:button {:class "neo-brutal-btn"} "Button 2"]]
      [:div
       {:class "neo-box-content signals"}
       [:div
        {:class "range-container"}
        [:label {:for "range1"} "Range 1"]
        [:input
         {:type "range",
          :id "range1",
          :min "0",
          :max "1",
          :step "1",
          :value "0"}]]
       [:div
        {:class "range-container"}
        [:label {:for "range2"} "Range 2"]
        [:input
         {:type "range",
          :id "range2",
          :min "0",
          :max "1",
          :step "1",
          :value "0"}]]
       [:div
        {:class "range-container"}
        [:label {:for "range3"} "Range 3"]
        [:input
         {:type "range",
          :id "range3",
          :min "0",
          :max "1",
          :step "1",
          :value "0"}]]
       [:div
        {:class "range-container"}
        [:label {:for "range4"} "Range 4"]
        [:input
         {:type "range",
          :id "range4",
          :min "0",
          :max "1",
          :step "1",
          :value "0"}]]]]
     [:div
      {:class "box box3"}
      [:button {:class "neo-brutal-btn"} "Boolean Formula"]]]
    [:section {:class "content"} [:p "Main content here"]]]
   [:footer [:p "Footer"]]
   [:script {:src "/js/main.js"}]])