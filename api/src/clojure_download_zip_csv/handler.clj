(ns clojure-download-zip-csv.handler
  (:require [clojure-download-zip-csv.service :as service]
            [ring.util.response :as response]
            [taoensso.timbre :as timbre]))

(defn upload-handler [req]
  (let [params (:multipart-params req)
        file (get params "file")
        result (service/process-upload! file)]
    (if (= (:status result) :success)
      (do
        (timbre/info "[UPLOAD SUCCESS] ID:" (:id result) "- Arquivo compactado em ZIP e enviado para S3")
        (-> (response/response {:message "Upload realizado com sucesso" :id (:id result)})
            (response/status 201)))
      (do
        (timbre/error "[UPLOAD FAILURE] Erro:" (:message result))
        (-> (response/response {:error (:message result)})
            (response/status 400))))))

(defn list-files-handler [_]
  (response/response (service/list-files)))

(defn download-handler [req]
  (let [id-str (get-in req [:path-params :id])
        result (service/download-file id-str)]
    (if (= (:status result) :success)
      (do
        (timbre/info "[DOWNLOAD SUCCESS] ID:" id-str "- Arquivo descompactado do ZIP e baixado")
        (-> (response/response (:stream result))
            (response/content-type "text/csv")
            (response/header "Content-Disposition" (str "attachment; filename=\"" (:filename result) "\""))))
      (do
        (timbre/error "[DOWNLOAD FAILURE] ID:" id-str "- Erro:" (:message result))
        (-> (response/response (:message result))
            (response/status 404))))))

(defn report-handler [_]
  (let [result (service/generate-report!)]
    (if (= (:status result) :success)
      (do
        (timbre/info "[REPORT SUCCESS] ID:" (:id result) "- Relatório gerado com sucesso")
        (-> (response/response {:message "Relatório gerado com sucesso" :id (:id result)})
            (response/status 201)))
      (do
        (timbre/error "[REPORT FAILURE] Erro:" (:message result))
        (-> (response/response {:error (:message result)})
            (response/status 500))))))
