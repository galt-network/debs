(ns debs.server.openrouter
  "OpenRouter chat-completion client."
  (:require
   [applied-science.js-interop :as j]
   [debs.server.http :as http])
  (:require-macros [debs.server.macros :refer [inline-resource]]))

(def default-model "nvidia/nemotron-3-ultra-550b-a55b:free")

(def ^:private api-key  (j/get js/process.env "OPENROUTER_API_KEY"))
(def ^:private model    (j/get js/process.env "OPENROUTER_MODEL"))
(def ^:private endpoint (j/get js/process.env "OPENROUTER_API_ENDPOINT"))

(def system-prompt (inline-resource "prompts/system.md"))

(defn- request-body [prompt]
  {:model    (or model default-model)
   :messages [{:role "user" :content prompt}
              {:role "system" :content system-prompt}]
   ; :max_tokens 500
   })

(defn chat-completion
  "Send `prompt` to OpenRouter and resolve to the assistant message string
   (the `choices[0].message.content` of the response). Resolves to nil if the
   response shape is unexpected."
  [prompt]
  (-> (http/fetch-json
       {:url     endpoint
        :method  "POST"
        :headers {"Content-Type"       "application/json"
                  "Authorization"      (str "Bearer " api-key)
                  "HTTP-Referer"       "http://localhost:3000"
                  "X-OpenRouter-Title" "Debs"}
        :body    (request-body prompt)
        :timeout 30000})
      (.then (fn [body]
               (some-> body (get-in ["choices" 0 "message" "content"]))))))
