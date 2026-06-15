(ns debs.ext.messaging
  (:require
   [applied-science.js-interop :as j]))

(defn preprocess-before-dispatch
  [handler message sender send-response]
  (-> message
      (js->clj ,,, :keywordize-keys true)
      (update ,,, :type keyword)
      (handler ,,,)))

(defn start-listening!
  [handler]
  (j/call js/chrome.runtime.onMessage :addListener (partial preprocess-before-dispatch handler)))

(defn send-to-active-tab
  [message & [tab-id]]
  (.then (j/call js/chrome.tabs :query #js {"active" true, "lastFocusedWindow" true})
         (fn [tabs]
           (js/console.log ">> send-to-tab got tabs" tab-id tabs)
           (let [tab (first tabs)
                 tab-id (or (j/get tab :id) tab-id)]
             (if tab-id
               (do
                 (js/console.log "SENDING MESSAGE" tab-id message)
                 (j/call-in js/chrome [:tabs :sendMessage] tab-id (clj->js message)))
               (js/console.log "NO ACTIVE TAB" tab))))))

(defn send-message
  [message]
  (js/console.log "messaging/send-message" message)
  (js/chrome.runtime.sendMessage (clj->js message)))
