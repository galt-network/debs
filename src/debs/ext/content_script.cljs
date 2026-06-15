(ns debs.ext.content-script
  (:require
   [applied-science.js-interop :as j]
   [debs.ext.content-helpers :as ch]
   [debs.ext.messaging :as messaging]
   [debs.ext.content-script.message-handlers :as message-handlers]))

(defonce reply-button-observer (atom nil))

(defn init
  []
  (println ">>> debs.ext.content-script/init was run")
  (reset! reply-button-observer (ch/init-reply-helper!))
  (messaging/start-listening! message-handlers/handle))
