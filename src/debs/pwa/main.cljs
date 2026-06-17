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

(defn get-share-params []
  (let [search (.. js/window -location -search)
        params (js/URLSearchParams. search)]
    {:title (.get params "title")
     :text  (.get params "text")
     :url   (.get params "url")}))

(defn handle-incoming-share! []
  (let [{:keys [title text url] :as share} (get-share-params)]
    (when (or title text url)
      (rf/dispatch [:debs.pwa.ui.events/set-tweet-url (or title text url) share])
      ;; Clean the URL so the share params don't stay in the address bar
      (.replaceState js/history nil "" js/window.location.pathname))))

(defn when-dom-ready
  [callback]
  (js/document.addEventListener "DOMContentLoaded" callback))

(defn init
  []
  (when-dom-ready
    (fn [_]
      (reset! app-root (rdc/create-root (js/document.getElementById "app")))
      (rf/dispatch-sync [:initialize])
      (handle-incoming-share!)
      (render! @app-root {:state ui-state}))))

(defn start!
  []
  (rf/clear-subscription-cache!)
  (render! @app-root {:state ui-state}))

(defn stop!
  []
  (log/info :message "Stopping"))
