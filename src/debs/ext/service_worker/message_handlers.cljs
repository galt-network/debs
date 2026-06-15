(ns debs.ext.service-worker.message-handlers
  (:require
   [clojure.string :as str]
   [debs.ext.messaging :as messaging]
   [debs.ext.service-worker.responders :as responders]))

(defn default-handler
  [message]
  (js/console.log "Handled by service-worker default-handler" message))

(def instructions
  ["You are a libertarian  and respond to the ideas and claims expressed in the following <tweet>."
   "Decide if there's socialist, collectivist, state intervention suggesting or other similar anty-libertarian values and actions promoting ideas."
   "When it is, write a reply tweet in a way that refutes, ridiculizes, criticizes or in some other way shows its intellectual incoherence and immorality."
   "Also consider if it's about politicians or state favouring person's or organization's attempt to free themselves of responsibility or giving false representation of the events or actions."
   "You response should be from an angle that incites people to think about the real character of the state according to the anarcho-capitalists like Murray Rothbard and Hans-Hermann Hoppe and others."
   "If you don't find the tweet to fit into any of these topics or cualifications, just respond with 'Prompt not applicable'"
   "Otherwise write a response up to 280 characters"])

(defn prepare-prompt
  [text]
  (let []
    (str/join "\n" (conj instructions (str "<tweet>\n" text "\n</tweet>"))))
  )

(defn handle-select-post
  [message]
  (responders/generate-response {:text (prepare-prompt (:content message))
                                 :responder-type :rothbard-brain
                                 :on-success (fn [r]
                                               (js/console.log "Got the response" r)
                                               (messaging/send-message {:type :answer-ready
                                                                        :meta (:meta message)
                                                                        :data r}))
                                 :on-failure (fn [e]
                                               (js/console.error "Got error" e)
                                               (messaging/send-message {:type :answer-error :data e}))})
  (js/console.log "Handled by service-worker handle-select-post" message))

(defn handle
  [message]
  (case (:type message)
    :select-post (handle-select-post message)
    (default-handler message)))
