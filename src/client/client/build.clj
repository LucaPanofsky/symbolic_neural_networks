(ns client.build
  (:require
   [client.template :as template]
   [hiccup2.core :as hiccup]
   [hiccup.page :as page]))

(defn render-page []
  (spit "docs/index_dev.html" (page/html5 (template/page))))

(comment 
  (render-page))
