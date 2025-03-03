(ns client.core
  (:require
   [client.engine :as client-engine]))

(defn register-events [event-dispatch]
  (run!
   (fn [[k v]] (js/document.addEventListener k v))
   event-dispatch))

(defn app []
  (js/document.addEventListener
   "DOMContentLoaded"
   (fn [x]
     (js/window.mermaid.initialize
      #js{:startOnLoad false
          :maxTextSize 9000000
          :maxEdges 10000
          :flowchart #js{:useMaxWidth true, :htmlLabels true, :defaultRenderer "elk"}})))
  (register-events client-engine/event-dispatch))


;; start is called by init and after code reloading finishes
(defn ^:dev/after-load start []
  (js/console.log "start")
  (app))

(defn init []
  ;; init is called ONCE when the page loads
  ;; this is called in the index.html and must be exported
  ;; so it is available even in :advanced release builds
  (js/console.log "init")
  (start))

;; this is called before any code is reloaded
(defn ^:dev/before-load stop []
  (js/console.log "stop"))

