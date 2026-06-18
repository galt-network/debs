(ns debs.ext.content-script
  (:require
   [applied-science.js-interop :as j]
   [debs.ext.content-helpers :as ch]
   [debs.ext.messaging :as messaging]
   [debs.ext.content-script.message-handlers :as message-handlers]))

(defonce mutation-observer (atom nil))

(defn add-integration!
  [& _]
  (let [send-tweet-info (fn [tweet-info]
                          (messaging/send-message {:type :open-sidebar})
                          (messaging/send-message {:type :tweet-selected :data tweet-info}))]
    (ch/process-existing-tweets! {:on-tweet-selected send-tweet-info})
    (reset! mutation-observer (ch/setup-mutation-observer! {:on-tweet-selected send-tweet-info}))))

(defn init
  []
  (println ">>> debs.ext.content-script/init was run")
  (when (and js/document js/document.body)
    (if (= js/document.readyState "loading")
      (.addEventListener js/document "DOMContentLoaded" add-integration!)
      (add-integration!)))
  (messaging/start-listening! message-handlers/handle))
