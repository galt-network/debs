(ns debs.pwa.main
  (:require
   [debs.pwa.ui :as ui]
   [lambdaisland.glogi :as log]
   [lambdaisland.glogi.console :as glogi-console]
   [re-frame.core :as rf]
   [reagent.core :as r]
   [reagent.dom.client :as rdc]))

(glogi-console/install!)

(log/set-levels
  {:glogi/root    :info
   'debs.pwa.main :trace
   'debs.pwa.ui   :error})

(defonce app-root
  (atom nil))

(defonce ui-state (r/atom {:tweet-url nil}))

(defn render!
  [container deps]
  (rdc/render container [(partial ui/present deps)]))

(defn when-dom-ready
  [callback]
  (js/document.addEventListener "DOMContentLoaded" callback))

(defn init
  []
  (when-dom-ready
    (fn [_]
      (reset! app-root (rdc/create-root (js/document.getElementById "app")))
      (rf/dispatch-sync [:initialize])
      (render! @app-root {:state ui-state}))))

(defn start!
  []
  (rf/clear-subscription-cache!)
  (render! @app-root {:state ui-state}))

(defn stop!
  []
  (log/info :message "Stopping"))
