(ns clojure-download-zip-csv.frontend.core
  (:require [reagent.dom :as rdom]
            [re-frame.core :as rf]
            [clojure-download-zip-csv.frontend.events]
            [clojure-download-zip-csv.frontend.subs]
            [clojure-download-zip-csv.frontend.views :as views]))

(defn mount-root []
  (rf/clear-subscription-cache!)
  (rdom/render [views/main-page] (.getElementById js/document "app")))

(defn init []
  (rf/dispatch-sync [:initialize-db])
  (mount-root))
