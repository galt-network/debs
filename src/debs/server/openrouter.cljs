(ns debs.server.openrouter
  "OpenRouter chat-completion client."
  (:require
   [clojure.string :as str]
   [applied-science.js-interop :as j]
   [debs.server.http :as http])
  (:require-macros [debs.server.macros :refer [inline-resource]]))

(def default-model "nvidia/nemotron-3-ultra-550b-a55b:free")

(def ^:private api-key  (j/get js/process.env "OPENROUTER_API_KEY"))
(def ^:private model    (j/get js/process.env "OPENROUTER_MODEL"))
(def ^:private endpoint (j/get js/process.env "OPENROUTER_API_ENDPOINT"))
(def ^:private model-timeout-limit (* 1000 (js/parseInt (j/get js/process.env "MODEL_TIMEOUT_LIMIT" "30"))))

(def system-prompt (inline-resource "prompts/system.md"))

(defn- request-body [prompt system-prompt-extras]
  {:model    (or model default-model)
   :messages [{:role "user" :content prompt}
              {:role "system" :content (str system-prompt "\n" system-prompt-extras "\n")}]
   ; :max_tokens 500
   })

(defn remove-duplicate-part
  "Given a string that may contain a duplicated consecutive block at the end,
   removes the trailing duplicate if it represents at least 25% of the words
   in the original string (non-overlapping with its first occurrence).
   Keeps the first occurrence. Returns the cleaned string (or original if
   no qualifying duplicate is found).

   Motivation: The MiniMax M3 model response has the response twice in it.

   Example:
   (remove-duplicate-part \"foo bar baz foo bar baz\")
   ;; => \"foo bar baz\""
  [s]
  (if (or (nil? s) (empty? s) (< (count s) 4))
    s
    (let [n          (count s)
          words      (str/split s #"\s+")
          total-w    (count words)
          ;; ceil(total-w * 0.25) using integer arithmetic (exact 1/4)
          min-w      (quot (+ total-w 3) 4)
          max-l      (quot n 2)]
      (loop [l max-l]
        (if (< l 1)
          s
          (let [start2 (- n l)
                prefix (subs s 0 start2)
                suffix (subs s start2)]
            (if (str/includes? prefix suffix)
              ;; Found a qualifying non-overlapping repeated suffix block
              (let [dup-w (count (str/split suffix #"\s+"))]
                (if (>= dup-w min-w)
                  (subs s 0 start2)   ; remove the duplicate suffix
                  s))                 ; too small → keep original
              (recur (dec l)))))))))

(defn chat-completion
  "Send `prompt` to OpenRouter and resolve to the assistant message string
   (the `choices[0].message.content` of the response). Resolves to nil if the
   response shape is unexpected."
  [prompt system-prompt-extras]
  (-> (http/fetch-json
       {:url     endpoint
        :method  "POST"
        :headers {"Content-Type"       "application/json"
                  "Authorization"      (str "Bearer " api-key)
                  "HTTP-Referer"       "http://localhost:3000"
                  "X-OpenRouter-Title" "Debs"}
        :body    (request-body prompt system-prompt-extras)
        :timeout model-timeout-limit})
      (.then (fn [body]
               (remove-duplicate-part (some-> body (get-in ["choices" 0 "message" "content"])))))))
