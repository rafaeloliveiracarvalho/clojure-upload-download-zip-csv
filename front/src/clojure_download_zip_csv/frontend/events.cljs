(ns clojure-download-zip-csv.frontend.events
  (:require [re-frame.core :as rf]
            [ajax.core :as ajax]
            [day8.re-frame.http-fx]))

(rf/reg-event-db
 :initialize-db
 (fn [_ _]
   {:active-page :upload
    :files []
    :upload-status nil}))

(rf/reg-event-db
 :navigate
 (fn [db [_ page]]
   (assoc db :active-page page)))

(rf/reg-event-fx
 :fetch-files
 (fn [{:keys [db]} _]
   {:http-xhrio {:method          :get
                 :uri             "http://localhost:3000/api/files"
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [:fetch-files-success]
                 :on-failure      [:api-error]}}))

(rf/reg-event-db
 :fetch-files-success
 (fn [db [_ files]]
   (assoc db :files files)))

(rf/reg-event-fx
 :upload-file
 (fn [{:keys [db]} [_ file]]
   (let [form-data (js/FormData.)]
     (.append form-data "file" file)
     {:http-xhrio {:method          :post
                   :uri             "http://localhost:3000/api/upload"
                   :body            form-data
                   :response-format (ajax/json-response-format {:keywords? true})
                   :on-success      [:upload-success]
                   :on-failure      [:upload-failure]}})))

(rf/reg-event-db
 :upload-success
 (fn [db [_ _]]
   (assoc db :upload-status "Arquivo enviado com sucesso!")))

(rf/reg-event-db
 :upload-failure
 (fn [db [_ error]]
   (assoc db :upload-status (str "Erro no upload: " (get-in error [:response :error] "Erro desconhecido")))))

(rf/reg-event-fx
 :download-file
 (fn [_ [_ id]]
   (let [url (str "http://localhost:3000/api/download/" id)]
     (.open js/window url "_blank")
     {})))

(rf/reg-event-db
 :api-error
 (fn [db [_ error]]
   (println "API Error:" error)
   db))
