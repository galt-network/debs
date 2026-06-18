(ns debs.ext.side-panel.message-handlers
  (:require
   [re-frame.core :as rf]))

(defn default-handler
  [message]
  (js/console.log "Handled by side-panel default-handler" message))

(defn handle-tweet-selected
  [message]
  (rf/dispatch [:debs.shared.ui.events/set-tweet-url (get-in message [:data :tweet-link])]))

(defn handle
  [message]
  (case (:type message)
    :tweet-selected (handle-tweet-selected message)

    (default-handler message)))
