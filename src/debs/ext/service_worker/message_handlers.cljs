(ns debs.ext.service-worker.message-handlers
  (:require
   [applied-science.js-interop :as j]))

(defn default-handler
  [message]
  (js/console.log "Handled by service-worker default-handler" message))

(defn handle-open-sidebar
  [{:keys [sender]}]
  (let [tab-id (j/get-in sender [:tab :id])]
    (js/console.log "service-worker handle-open-sidebar tab-id" tab-id)
    (j/call js/chrome.sidePanel :open #js {:tabId tab-id})
    (j/call js/chrome.sidePanel :setOptions #js {:tabId tab-id :path "side-panel.html" :enabled true})))

(defn handle
  [message]
  (case (:type message)
    :open-sidebar (handle-open-sidebar message)
    (default-handler message)))
