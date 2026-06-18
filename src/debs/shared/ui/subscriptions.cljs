(ns debs.shared.ui.subscriptions
  (:require
   [debs.shared.time-helpers :refer [relative-time-str]]
   [debs.shared.validations :refer [tweet-id-from-url]]
   [re-frame.core :as rf]))

(comment
  (require 're-frame.db)
  @re-frame.db/app-db)

(rf/reg-sub
  ::now
  (fn [db _]
    (:now db)))

(rf/reg-sub
  ::relative-time
  (fn [[_ timestamp] _]
    [(rf/subscribe [::now]) (atom timestamp)])
  (fn [[now timestamp] _]
    (relative-time-str now timestamp)))

(rf/reg-sub
  ::tweet-url
  (fn [db _]
    (get-in db [:tweet-url] "")))

(rf/reg-sub
  ::valid-tweet-url?
  (fn [db _]
    (not (nil? (tweet-id-from-url (get-in db [:tweet-url]))))))

(rf/reg-sub
  ::all-tweets
  (fn [db _]
    (map (fn [tweet-id] (get-in db [:tweets tweet-id])) (:tweet-ids db))))

(comment
  (require 're-frame.db)
  re-frame.db/app-db)
