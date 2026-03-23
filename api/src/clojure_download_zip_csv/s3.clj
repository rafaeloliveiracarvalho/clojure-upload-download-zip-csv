(ns clojure-download-zip-csv.s3
  (:require [clojure.java.io :as io])
  (:import [software.amazon.awssdk.services.s3 S3Client S3Configuration]
           [software.amazon.awssdk.services.s3.model GetObjectRequest CreateBucketRequest HeadBucketRequest PutObjectRequest]
           [software.amazon.awssdk.auth.credentials StaticCredentialsProvider AwsBasicCredentials]
           [software.amazon.awssdk.regions Region]
           [software.amazon.awssdk.core.sync RequestBody]
           [java.net URI]
           [java.util.function Consumer]))

(def bucket "csv-bucket")
(def s3-endpoint (URI/create (or (System/getenv "S3_ENDPOINT") "http://127.0.0.1:4566")))

(def s3-client
  (-> (S3Client/builder)
      (.endpointOverride s3-endpoint)
      (.region Region/US_EAST_1)
      (.serviceConfiguration (reify Consumer
                               (accept [this builder]
                                 (.pathStyleAccessEnabled builder true))))
      (.credentialsProvider
       (StaticCredentialsProvider/create
        (AwsBasicCredentials/create "testing" "testing")))
      (.build)))

(defn bucket-exists? []
  (try
    (.headBucket s3-client (-> (HeadBucketRequest/builder) (.bucket bucket) (.build)))
    true
    (catch Exception e
      false)))

(defn setup-s3! []
  (try
    (if (bucket-exists?)
      (println "S3: Bucket" bucket "já existe.")
      (do
        (println "S3: Criando bucket" bucket)
        (.createBucket s3-client (-> (CreateBucketRequest/builder) (.bucket bucket) (.build)))))
    (catch Exception e
      (println "S3 Setup Exception:" (.getMessage e)))))

(defn upload-file! [key temp-file]
  (.putObject s3-client 
              (-> (PutObjectRequest/builder) (.bucket bucket) (.key key) (.build))
              (RequestBody/fromFile temp-file)))

(defn get-file-stream [key]
  (.getObject s3-client (-> (GetObjectRequest/builder) (.bucket bucket) (.key key) (.build))))
