(ns clojure-download-zip-csv.service
  (:require [clojure-download-zip-csv.db :as db]
            [clojure-download-zip-csv.s3 :as s3])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]
           [java.util UUID]))

(defn process-upload! [file]
  (if (and file (.endsWith (.toLowerCase (:filename file)) ".csv"))
    (let [id (UUID/randomUUID)
          filename (:filename file)
          temp-file (:tempfile file)
          s3-key (str id "-" filename)
          now (LocalDateTime/now)
          date-str (.format now (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss"))
          timestamp (System/currentTimeMillis)]
      
      (s3/upload-file! s3-key temp-file)
      (db/save-file-metadata! {:id id 
                               :original_name filename 
                               :upload_date date-str 
                               :upload_timestamp timestamp 
                               :s3_key s3-key})
      {:status :success :id id})
    {:status :error :message "Apenas arquivos CSV são permitidos"}))

(defn list-files []
  (db/find-all-files))

(defn download-file [id-str]
  (let [id (try (UUID/fromString id-str) (catch Exception _ nil))
        file-record (when id (db/find-file-by-id id))]
    (if file-record
      (let [s3-key (:files/s3_key file-record)
            original-name (:files/original_name file-record)]
        (try
          {:status :success 
           :stream (s3/get-file-stream s3-key) 
           :filename original-name}
          (catch Exception _
            {:status :error :message "Erro ao buscar arquivo no S3"})))
      {:status :error :message "Arquivo não encontrado"})))
