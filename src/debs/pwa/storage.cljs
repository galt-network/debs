(ns debs.pwa.storage
  (:require
   [cljs.reader :as reader]
   [re-frame.core :as rf]))

(def ^:private storage-key "debs-app-db")

(defn save-db! [db]
  (try
    (aset js/localStorage storage-key (pr-str db))
    (catch js/Error e
      (js/console.error "Failed to save to localStorage" e))))

(defn load-db []
  (when-let [item (.getItem js/localStorage storage-key)]
    (try
      (reader/read-string item)
      (catch js/Error e
        (js/console.warn "Failed to parse localStorage, using defaults" e)
        nil))))

(rf/reg-cofx
  ::local-storage-db
  (fn [cofx _]
    (assoc cofx :local-storage-db (load-db))))

(rf/reg-fx
  ::persist-db
  (fn [db]
    (save-db! db)))
