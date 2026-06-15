(ns debs.ext.service-worker
  (:require
   [applied-science.js-interop :as j]
   [debs.ext.messaging :as messaging]
   [debs.ext.service-worker.message-handlers :as message-handlers]
   [oops.core :refer [ocall oget]]))

(def enabled-sites #{"https://x.com" "http://localhost:8000"})

(defn selectively-open-side-panel
  [tab-id info tab]
  (when-let [url (.-url tab)]
    (let [full-url (new js/URL url)]
      (println ">>> selectively-open-side-panel full-url" {:full-url full-url
                                                           :contains? (contains? enabled-sites (.-origin full-url))
                                                           :origin (.-origin full-url)
                                                           :type-origin (type (.-origin full-url))})
      (if (contains? enabled-sites (.-origin full-url))
        (j/call-in js/chrome [:sidePanel :setOptions] #js {:tabId tab-id :path "side-panel.html" :enabled true})
        (j/call-in js/chrome [:sidePanel :setOptions] #js {:tabId tab-id :enabled false})))))

(defn on-extension-installed
  "Register callback for when the extension is first installed.
  Docs: https://developer.chrome.com/docs/extensions/reference/api/runtime#event-onInstalled"
  [f]
  (ocall js/chrome "runtime.onInstalled.addListener" f))

(defn on-tabs-updated
  "Register callback when tab is updated.
    - (f {:tabId number, :changeInfo Object :tab Tab})
  Docs: https://developer.chrome.com/docs/extensions/reference/api/tabs#event-onUpdated"
  [f]
  (ocall js/chrome "tabs.onUpdated.addListener" f))

(defn configure-side-panel
  "Configures the extension's side panel behavior (upsert)
  Docs: https://developer.chrome.com/docs/extensions/reference/api/sidePanel#method-setPanelBehavior"
  []
  (ocall js/chrome "sidePanel.setOptions" (clj->js {:enabled false}))
  (ocall js/chrome "sidePanel.setPanelBehavior" (clj->js {:openPanelOnActionClick true})))

(defn supported-url?
  [supported-urls url]
  (contains? supported-urls (oget (new js/URL url) "origin")))

(defn open-panel-for-enabled-sites
  [enabled-sites tab-id change-info tab]
  (when (and (oget tab "url") (= (oget change-info "?status") "complete"))
    (let [supported? (supported-url? enabled-sites (oget tab "url"))]
      (if supported?
        (ocall js/chrome "sidePanel.setOptions" #js {:tabId tab-id :path "side-panel.html" :enabled true})
        (ocall js/chrome "sidePanel.setOptions" #js {:tabId tab-id :enabled false})))))

(defn init
  []
  (on-extension-installed configure-side-panel)
  (on-tabs-updated (partial open-panel-for-enabled-sites enabled-sites))
  (j/assoc! js/self :zeSendMessage messaging/send-to-active-tab)
  (j/assoc! js/self :zeAnswerReady #(messaging/send-message {:type :answer-ready :answer "42"}))
  (messaging/start-listening! message-handlers/handle))
