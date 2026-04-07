(ns clojure-download-zip-csv.service
  (:require [clojure-download-zip-csv.db :as db]
            [clojure-download-zip-csv.s3 :as s3]
            [clojure.java.io :as io]
            [taoensso.timbre :as timbre])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]
           [java.util UUID]
           [java.io File BufferedWriter OutputStreamWriter PipedInputStream PipedOutputStream]
           [java.util.zip ZipInputStream ZipOutputStream ZipEntry]))

(defn process-upload! [file]
  (if (and file (.endsWith (.toLowerCase (:filename file)) ".csv"))
    (let [id (UUID/randomUUID)
          filename (:filename file)
          temp-file (:tempfile file)
          s3-key (str id "-" filename ".zip")
          zip-file (io/file (str "/tmp/" s3-key))
          now (LocalDateTime/now)
          date-str (.format now (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss"))
          timestamp (System/currentTimeMillis)]
      
      (try
        (with-open [zip-out (ZipOutputStream. (io/output-stream zip-file))]
          (.putNextEntry zip-out (ZipEntry. filename))
          (io/copy temp-file zip-out)
          (.closeEntry zip-out))
        
        (let [zip-size (.length zip-file)
              zip-stream (io/input-stream zip-file)]
          (s3/upload-stream! s3-key zip-stream zip-size)
          (.close zip-stream))
        
        (db/save-file-metadata! {:id id 
                                :original_name filename 
                                :upload_date date-str 
                                :upload_timestamp timestamp 
                                :s3_key s3-key})
        
        (io/delete-file zip-file)
        {:status :success :id id}
        
        (catch Exception e
          (io/delete-file zip-file)
          {:status :error :message (.getMessage e)})))
    {:status :error :message "Apenas arquivos CSV são permitidos"}))

(defn list-files []
  (db/find-all-files))

(defn download-file [id-str]
  (let [id (try (UUID/fromString id-str) (catch Exception _ nil))
        file-record (when id (db/find-file-by-id id))]
    (if file-record
      (let [s3-key (:files/s3_key file-record)
            original-name (:files/original_name file-record)
            filename (clojure.string/replace original-name #"\.zip$" ".csv")]
        (try
          (let [s3-stream (s3/get-file-stream s3-key)
                zis (ZipInputStream. s3-stream)
                entry (.getNextEntry zis)]
            (if entry
              (let [piped-out (PipedOutputStream.)
                    piped-in (PipedInputStream. piped-out 65536)]
                (future
                  (try
                    (let [buffer (byte-array 8192)]
                      (loop []
                        (let [len (.read zis buffer 0 (alength buffer))]
                          (when (> len 0)
                            (.write piped-out buffer 0 len)
                            (recur))))
                      (.closeEntry zis))
                    (catch Exception e
                      (timbre/error "Download pipeline error:" e))
                    (finally
                      (try (.close zis) (catch Exception _))
                      (try (.close piped-out) (catch Exception _)))))
                {:status :success 
                 :stream piped-in 
                 :filename filename})
              (do
                (.close zis)
                {:status :error :message "Arquivo ZIP inválido ou vazio"})))
          (catch Exception e
            {:status :error :message "Erro ao buscar arquivo no S3"})))
      {:status :error :message "Arquivo não encontrado"})))

(defn- row-to-csv-line [row]
  (let [id (or (get row :files/id) (get row :id) "")
        original-name (or (get row :files/original_name) (get row :original_name) "")
        upload-date (or (get row :files/upload_date) (get row :upload_date) "")
        upload-timestamp (or (get row :files/upload_timestamp) (get row :upload_timestamp) "")
        s3-key (or (get row :files/s3_key) (get row :s3_key) "")
        file-type (or (get row :files/file_type) (get row :file_type) "upload")]
    (str id "," original-name "," upload-date "," upload-timestamp "," s3-key "," file-type)))

(defn- write-csv-header [writer]
  (.write writer "id,original_name,upload_date,upload_timestamp,s3_key,file_type\n"))

(defn- write-csv-row [writer row]
  (.write writer (row-to-csv-line row))
  (.newLine writer))

(defn generate-report! []
  (let [files-data (db/find-all-files-full)
        id (UUID/randomUUID)
        timestamp (System/currentTimeMillis)
        filename (str timestamp "_relatorio.csv")
        zip-filename (str filename ".zip")
        s3-key (str id "-" zip-filename)
        zip-file (io/file (str "/tmp/" zip-filename))
        csv-file (io/file (str "/tmp/" filename))
        now (LocalDateTime/now)
        date-str (.format now (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss"))]
    
    (try
      (with-open [writer (BufferedWriter. (OutputStreamWriter. (io/output-stream csv-file) "UTF-8"))]
        (write-csv-header writer)
        (doseq [row files-data]
          (write-csv-row writer row)))
      
      (with-open [zip-out (ZipOutputStream. (io/output-stream zip-file))]
        (.putNextEntry zip-out (ZipEntry. filename))
        (io/copy csv-file zip-out)
        (.closeEntry zip-out))
      
      (let [zip-size (.length zip-file)
            zip-stream (io/input-stream zip-file)]
        (s3/upload-stream! s3-key zip-stream zip-size)
        (.close zip-stream))
      
      (db/save-file-metadata! {:id id
                               :original_name (str timestamp "_relatorio.zip")
                               :upload_date date-str
                               :upload_timestamp timestamp
                               :s3_key s3-key
                               :file_type "relatorio"})
      
      (io/delete-file csv-file)
      (io/delete-file zip-file)
      
      {:status :success :id id :filename (str timestamp "_relatorio.zip")}
      
      (catch Exception e
        (io/delete-file csv-file)
        (io/delete-file zip-file)
        {:status :error :message (.getMessage e)}))))
