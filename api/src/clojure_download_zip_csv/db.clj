(ns clojure-download-zip-csv.db
  (:require [next.jdbc :as jdbc]))

(def db-config
  {:dbtype   "postgresql"
   :dbname   (or (System/getenv "DB_NAME") "files_db")
   :host     (or (System/getenv "DB_HOST") "localhost")
   :port     (or (System/getenv "DB_PORT") "5432")
   :user     (or (System/getenv "DB_USER") "postgres")
   :password (or (System/getenv "DB_PASSWORD") "postgres")})

(def ds (jdbc/get-datasource db-config))

(defn init-db []
  (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS files (
                      id UUID PRIMARY KEY,
                      original_name VARCHAR(255),
                      upload_date VARCHAR(20),
                      upload_timestamp BIGINT,
                      s3_key VARCHAR(255)
                    )"]))

(defn save-file-metadata! [metadata]
  (jdbc/execute! ds ["INSERT INTO files (id, original_name, upload_date, upload_timestamp, s3_key) 
                      VALUES (?, ?, ?, ?, ?)" 
                     (:id metadata) 
                     (:original_name metadata) 
                     (:upload_date metadata) 
                     (:upload_timestamp metadata) 
                     (:s3_key metadata)]))

(defn find-all-files []
  ;; PostgreSQL returns lowercase column names by default in next.jdbc
  (jdbc/execute! ds ["SELECT id, original_name, upload_date, upload_timestamp FROM files"]))

(defn find-file-by-id [id]
  (jdbc/execute-one! ds ["SELECT s3_key, original_name FROM files WHERE id = ?" id]))
