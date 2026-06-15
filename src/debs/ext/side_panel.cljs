(ns debs.ext.side-panel
  (:require
   [debs.ext.messaging :as messaging :refer [send-message]]
   [debs.ext.side-panel.ui :as ui]
   [debs.ext.side-panel.message-handlers :as message-handlers]
   [reagent.core :as r]
   [reagent.dom.client :as rdc]))

(defonce app-root
  (rdc/create-root (.getElementById js/document "app")))

(def state (r/atom {:last-selected-post nil
                    :posts {}}))

(defn ^:dev/after-load start-or-reload!
  []
  (messaging/start-listening! (partial message-handlers/handle state))
  (rdc/render app-root [(partial ui/present state send-message)]))

(defn ^:dev/before-load stop!
  []
  (println ">>> stopping side-panel app"))

(defn init
  []
  (start-or-reload!)
  (println ">>> debs.ext.side-panel/init was run"))
