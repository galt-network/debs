(ns debs.ext.content-script.message-handlers)

(defn default-handler
  [message]
  (js/console.log "Handled by content-script default-handler" message))

(defn handle-begin-reply
  [message]
  (js/console.log "Handled by content-script begin-reply" message))

(defn handle
  [message]
  (case (:type message)
    :begin-reply (handle-begin-reply message)

    (default-handler message)))
