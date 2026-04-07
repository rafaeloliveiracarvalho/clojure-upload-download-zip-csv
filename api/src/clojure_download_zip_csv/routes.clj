(ns clojure-download-zip-csv.routes
  (:require [reitit.ring :as ring]
            [reitit.ring.middleware.parameters :as parameters]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [muuntaja.core :as m]
            [ring.middleware.multipart-params :as multipart]
            [ring.middleware.cors :refer [wrap-cors]]
            [clojure-download-zip-csv.handler :as handler]))

(def app-routes
  (ring/ring-handler
   (ring/router
    ["/api"
     ["/upload" {:post handler/upload-handler}]
     ["/files" {:get handler/list-files-handler}]
     ["/files/report" {:get handler/report-handler}]
     ["/download/:id" {:get handler/download-handler}]]
    {:data {:muuntaja m/instance
            :middleware [parameters/parameters-middleware
                         muuntaja/format-middleware
                         multipart/wrap-multipart-params
                         [wrap-cors
                          :access-control-allow-origin [#".*"]
                          :access-control-allow-methods [:get :put :post :delete :options]]]}})
   (ring/create-default-handler)))
