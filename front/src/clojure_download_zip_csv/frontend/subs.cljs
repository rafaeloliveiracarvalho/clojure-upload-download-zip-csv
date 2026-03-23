(ns clojure-download-zip-csv.frontend.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub
 :active-page
 (fn [db _]
   (:active-page db)))

(rf/reg-sub
 :files
 (fn [db _]
   (:files db)))

(rf/reg-sub
 :upload-status
 (fn [db _]
   (:upload-status db)))
