(ns clojure-download-zip-csv.service
  (:require [clojure-download-zip-csv.db :as db]
            [clojure-download-zip-csv.s3 :as s3]
            [clojure.java.io :as io])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]
           [java.util UUID]
           [java.io ByteArrayOutputStream ByteArrayInputStream]
           [java.util.zip ZipInputStream ZipEntry]))

(defn create-zip-stream [filename content]
  (let [bos (ByteArrayOutputStream.)
        zip-out (java.util.zip.ZipOutputStream. bos)
        entry (ZipEntry. filename)]
    (.putNextEntry zip-out entry)
    (.write zip-out (.getBytes content))
    (.closeEntry zip-out)
    (.close zip-out)
    bos))

(defn extract-csv-from-zip [zip-stream]
  (let [zis (ZipInputStream. zip-stream)
        entry (.getNextEntry zis)]
    (when entry
      (let [bos (ByteArrayOutputStream.)
            buffer (byte-array 4096)]
        (loop []
          (let [len (.read zis buffer 0 (alength buffer))]
            (when (> len 0)
              (.write bos buffer 0 len)
              (recur))))
        (.closeEntry zis)
        (.close zis)
        (String. (.toByteArray bos) "UTF-8")))))

(defn process-upload! [file]
  (if (and file (.endsWith (.toLowerCase (:filename file)) ".csv"))
    (let [id (UUID/randomUUID)
          filename (:filename file)
          temp-file (:tempfile file)
          csv-content (slurp temp-file)
          zip-stream (create-zip-stream filename csv-content)
          zip-bytes (.toByteArray zip-stream)
          zip-input-stream (ByteArrayInputStream. zip-bytes)
          s3-key (str id "-" filename ".zip")
          now (LocalDateTime/now)
          date-str (.format now (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss"))
          timestamp (System/currentTimeMillis)]
      
      (s3/upload-stream! s3-key zip-input-stream (alength zip-bytes))
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
          (let [zip-stream (s3/get-file-stream s3-key)
                csv-content (extract-csv-from-zip zip-stream)]
            {:status :success 
             :stream (ByteArrayInputStream. (.getBytes csv-content "UTF-8")) 
             :filename original-name})
          (catch Exception _
            {:status :error :message "Erro ao buscar arquivo no S3"})))
      {:status :error :message "Arquivo não encontrado"})))
