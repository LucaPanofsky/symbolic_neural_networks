(ns server.server
  "Toy server for rendering symbolic neural networks"
  (:require
   [aleph.http :as aleph]
   [reitit.ring :as ring]
   [reitit.http :as http]
   [reitit.http.coercion :as coercion]
   [reitit.dev.pretty :as pretty]
   [ring.middleware.reload :refer [wrap-reload]]
   [reitit.interceptor.sieppari :as sieppari]
   [reitit.http.interceptors.parameters :as parameters]
   [reitit.http.interceptors.muuntaja :as muuntaja]
   [reitit.http.interceptors.multipart :as multipart]
   [muuntaja.core :as m]
   [sieppari.async.manifold]
   [ring.util.response :as response]
   [server.editor :as editor]
   [server.logic :as server-logic]))

(def app
  (http/ring-handler
   (http/router
    [["/favicon.ico"
      {:get {:no-doc true
             :handler (constantly (response/redirect "/public/favicon.png"))}}]

     ["/status"
      {:get {:handler (fn [_request]
                        {:status 200
                         :body "Status ok."})}}]
     ["/public/*" (ring/create-resource-handler)]
     ["/symbolic-neural-networks"
      (fn [request]
        {:status 200 :body (editor/render-editor-page)})]
     ["/symbolic-neural-networks"
      ["/compile-neural-network" server-logic/handler:compile-neural-network]
      ["/solve-neural-network"
       (fn [request]
         {:status 200 :body "solve neural network"})]
      ["/compile-classifier"
       (fn [request]
         {:status 200 :body "compile classifier"})]]]

    {;:reitit.interceptor/transform dev/print-context-diffs ;; pretty context diffs
     ;;:validate spec/validate ;; enable spec validation for route data
     ;;:reitit.spec/wrap spell/closed ;; strict top-level validation
     :exception pretty/exception
     :data {:muuntaja m/instance
            :interceptors [;; query-params & form-params
                           (parameters/parameters-interceptor)
                           ;; content-negotiation
                           (muuntaja/format-negotiate-interceptor)
                           ;; encoding response body
                           (muuntaja/format-response-interceptor)
                           ;; decoding request body
                           (muuntaja/format-request-interceptor)
                           ;; coercing response bodys
                           (coercion/coerce-response-interceptor)
                           ;; coercing request parameters
                           (coercion/coerce-request-interceptor)
                           ;; multipart
                           (multipart/multipart-interceptor)]}})
   {:executor sieppari/executor}))


(def dev-app
  (-> (var app)
      wrap-reload))

(def production-app
  (-> (var app)))

(defn start []
  (let [port  3001]
    (aleph/start-server
     (aleph/wrap-ring-async-handler #'dev-app)
     {:port 3001})
    (println (format "Symbolic Neural Network Editor running at port %s" port))
    (println (format "http://localhost:%s/" port))))



(comment
  (start))