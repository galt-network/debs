(ns debs.ext.side-panel
  (:require
   [re-frame.core :as rf]
   [debs.ext.messaging :as messaging :refer [send-message]]
   [debs.shared.ui.events :as shared.events]
   [debs.ext.side-panel.message-handlers :as message-handlers]
   [debs.ext.side-panel.ui :as ui]
   [debs.pwa.storage]
   [reagent.dom.client :as rdc]))

(defonce app-root
  (rdc/create-root (.getElementById js/document "app")))

(defn ^:dev/after-load start-or-reload!
  []
  (messaging/start-listening! message-handlers/handle)
  (rf/clear-subscription-cache!)
  (rf/dispatch-sync [::shared.events/initialize])
  (rdc/render app-root [(partial ui/present {})]))

(defn ^:dev/before-load stop!
  []
  (println ">>> stopping side-panel app"))

(defn init
  []
  (start-or-reload!)
  (println ">>> debs.ext.side-panel/init was run"))
