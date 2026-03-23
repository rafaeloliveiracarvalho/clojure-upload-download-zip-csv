(ns clojure-download-zip-csv.core
  (:require [ring.adapter.jetty :as jetty]
            [clojure-download-zip-csv.db :as db]
            [clojure-download-zip-csv.s3 :as s3]
            [clojure-download-zip-csv.routes :as routes])
  (:gen-class))

(defn -main []
  (db/init-db)
  (s3/setup-s3!)
  (println "🚀 Servidor MVC rodando em http://localhost:3000")
  (jetty/run-jetty routes/app-routes {:port 3000 :join? false}))
