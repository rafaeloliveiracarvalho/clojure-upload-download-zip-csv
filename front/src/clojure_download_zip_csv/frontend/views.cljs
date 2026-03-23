(ns clojure-download-zip-csv.frontend.views
  (:require [reagent.core :as r]
            [re-frame.core :as rf]))

(defn navbar []
  [:nav.navbar.is-primary
   [:div.navbar-brand
    [:a.navbar-item {:href "#" :on-click #(rf/dispatch [:navigate :upload])} "CSV Manager"]]
   [:div.navbar-menu.is-active
    [:div.navbar-start
     [:a.navbar-item {:href "#" :on-click #(rf/dispatch [:navigate :upload])} "Upload"]
     [:a.navbar-item {:href "#" :on-click #(do (rf/dispatch [:navigate :list]) (rf/dispatch [:fetch-files]))} "Listagem"]]]])

(defn upload-page []
  (let [file-atom (r/atom nil)
        status @(rf/subscribe [:upload-status])]
    (fn []
      [:section.section
       [:div.container
        [:h1.title "Upload de CSV"]
        [:div.field
         [:div.file.has-name.is-boxed
          [:label.file-label
           [:input.file-input {:type "file" 
                               :accept ".csv"
                               :on-change #(reset! file-atom (-> % .-target .-files (aget 0)))}]
           [:span.file-cta
            [:span.file-label "Escolha um arquivo..."]]
           (when @file-atom
             [:span.file-name (.-name @file-atom)])]]]
        [:div.field
         [:button.button.is-link 
          {:disabled (not @file-atom)
           :on-click #(rf/dispatch [:upload-file @file-atom])} 
          "Enviar Arquivo"]]
        (when status
          [:div.notification.is-info.mt-4 status])]])))

(defn list-page []
  (let [files @(rf/subscribe [:files])]
    [:section.section
     [:div.container
      [:h1.title "Arquivos Enviados"]
      [:table.table.is-fullwidth.is-striped
       [:thead
        [:tr
         [:th "ID"]
         [:th "Nome Original"]
         [:th "Data de Upload"]
         [:th "Timestamp"]
         [:th "Ações"]]]
       [:tbody
        (for [f files]
          (let [id (:files/id f)
                name (:files/original_name f)
                date (:files/upload_date f)
                ts (:files/upload_timestamp f)]
            [:tr {:key id}
             [:td [:a {:href "#" 
                       :on-click #(do (.preventDefault %) 
                                      (rf/dispatch [:download-file id]))} 
                   id]]
             [:td name]
             [:td date]
             [:td ts]
             [:td [:button.button.is-small.is-info 
                    {:on-click #(rf/dispatch [:download-file id])} 
                    "Download"]]]))]]]]))

(defn main-page []
  (let [active-page @(rf/subscribe [:active-page])]
    [:div
     [navbar]
     (case active-page
       :upload [upload-page]
       :list   [list-page]
       [:div "Página não encontrada"])]))
