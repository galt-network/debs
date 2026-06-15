(ns debs.ext.side-panel.message-handlers)

(defn default-handler
  [message]
  (js/console.log "Handled by side-panel default-handler" message))

(defn handle-select-post
  [state message]
  (reset! state {:last-selected-post (:meta message)
                 :posts (assoc (:posts @state) (get-in message [:meta :tweet-id]) (:meta message))})
  (js/console.log "Handled by side-panel handle-select-post" message))

(defn update-post
  [state post-id attrs]
  (swap! state update-in [post-id] merge attrs))

(defn handle-answer-ready
  [state message]
  (update-post state (get-in message [:meta :tweet-id]) {:answer (:data message)})
  (js/console.log "Handled by side-panel handle-answer-ready" message))

(defn handle-answer-error
  [message]
  (js/console.log "Handled by side-panel handle-answer-error" message))

(defn handle
  [state message]
  (case (:type message)
    :select-post (handle-select-post state message)
    :answer-ready (handle-answer-ready state message)
    :answer-error (handle-answer-error message)

    (default-handler message)))
