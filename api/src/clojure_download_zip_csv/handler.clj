(ns clojure-download-zip-csv.handler
  (:require [clojure-download-zip-csv.service :as service]
            [ring.util.response :as response]))

(defn upload-handler [req]
  (let [params (:multipart-params req)
        file (get params "file")
        result (service/process-upload! file)]
    (if (= (:status result) :success)
      (-> (response/response {:message "Upload realizado com sucesso" :id (:id result)})
          (response/status 201))
      (-> (response/response {:error (:message result)})
          (response/status 400)))))

(defn list-files-handler [_]
  (response/response (service/list-files)))

(defn download-handler [req]
  (let [id-str (get-in req [:path-params :id])
        result (service/download-file id-str)]
    (if (= (:status result) :success)
      (-> (response/response (:stream result))
          (response/content-type "text/csv")
          (response/header "Content-Disposition" (str "attachment; filename=\"" (:filename result) "\"")))
      (-> (response/response (:message result))
          (response/status 404)))))
