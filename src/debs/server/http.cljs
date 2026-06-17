(ns debs.server.http
  "HTTP server primitives: CORS, response writing, body reading, and a
   `fetch-json` wrapper around `js/fetch` for outgoing calls.

   Node 18+ ships a native `fetch` global, so callers can compose with
   `.then`/`.catch` directly — no more nested Node-style callbacks."
  (:require
   [applied-science.js-interop :as j]
   [clojure.string :as str]))

(def allowed-domains
  "Comma-separated list from DEBS_API_ALLOWED_DOMAINS, or nil when unset."
  (when-let [v (j/get js/process.env "DEBS_API_ALLOWED_DOMAINS")]
    (->> (str/split v #",")
         (map str/trim)
         (filter seq)
         set)))

(defn cors-headers
  "CORS headers as a #js map. Exposed publicly so handlers that don't write
   a body (e.g. OPTIONS preflight) can use them too."
  []
  #js {:Access-Control-Allow-Origin (when (seq allowed-domains)
                                      (str/join ", " allowed-domains))
       :Access-Control-Allow-Methods "GET, POST, OPTIONS"
       :Access-Control-Allow-Headers "Content-Type, X-API-Key"
       :Access-Control-Max-Age "86400"})

(defn- merge-headers
  "Merge JS header maps into a new #js object."
  [& header-maps]
  (reduce (fn [acc h]
            (doseq [[k v] (js->clj h)]
              (j/assoc! acc (name k) v))
            acc)
          #js {}
          header-maps))

(defn send-json
  "Write a JSON response with the given HTTP status."
  [^js res status data]
  (.writeHead res status
              (merge-headers #js {:Content-Type "application/json"}
                             (cors-headers)))
  (.end res (.stringify js/JSON (clj->js data))))

(defn send-error
  "Write a JSON error response. `payload` is the body to send, typically
   `{:error \"...\"}` and optionally `:message`, `:details`, etc."
  [^js res status payload]
  (send-json res status payload))

(defn read-body
  "Collect the body of a Node.js request or response stream into a buffer and
   JSON-parse it. Resolves to a Clojure map, or nil if the body is empty /
   not valid JSON."
  [^js stream]
  (js/Promise.
   (fn [resolve reject]
     (let [chunks (atom [])]
       (.on stream "data"  (fn [chunk] (swap! chunks conj chunk)))
       (.on stream "error" reject)
       (.on stream "end"
            (fn []
              (let [buf (.concat js/Buffer (into-array @chunks))]
                (try
                  (resolve (some-> buf js/JSON.parse js->clj))
                  (catch :default _
                    (resolve nil))))))))))

;; --- outgoing requests ------------------------------------------------------

(def ^:private retriable-codes
  #js ["ECONNRESET" "ETIMEDOUT" "EAI_AGAIN" "ENOTFOUND" "ECONNREFUSED" "EPIPE"])

(def ^:private retriable-status
  #{408 425 429 500 502 503 504})

(defn- retriable-error?
  "True for transport errors and HTTP statuses that are safe to retry on an
   idempotent request. Node's fetch wraps OS errors in `err.cause`, so we
   look at both `err.code` and `(.-code err.cause)`."
  [err]
  (or (when (.-status err) (retriable-status (.-status err)))
      (when-let [c (or (.-code err) (j/get-in err [:cause :code]))]
        (.includes retriable-codes c))))

(defn- backoff-ms
  "Exponential backoff with a small cap. 200ms, 400ms, 800ms, capped at 2s."
  [attempt]
  (min 2000 (* 200 (bit-shift-left 1 (dec attempt)))))

(defn- aborted-error [timeout]
  (let [e (js/Error. (str "timeout after " timeout "ms"))]
    (set! (.-name e) "AbortError")
    (set! (.-code e) "ETIMEDOUT")
    e))

(defn- attempt-once
  "Single fetch attempt. Resolves to the parsed body on 2xx, throws an Error
   with `.-status` set for non-2xx, or re-throws the network/abort error."
  [{:keys [url method headers body timeout]}]
  (let [controller (js/AbortController.)
        timer      (js/setTimeout
                    (fn [] (.abort controller))
                    timeout)
        opts       (cond-> {:method  method
                            :headers (clj->js (or headers {}))
                            :signal  (.-signal controller)}
                     body (assoc :body (js/JSON.stringify (clj->js body))))]
    (-> (js/fetch url (clj->js opts))
        (.then (fn [^js response]
                 (let [status (.-status response)]
                   (if (<= 200 status 299)
                     (-> (.json response)
                         (.then (fn [data]
                                  (js/clearTimeout timer)
                          ;; String keys, matching the old https path.
                          (js->clj data))))
                     (-> (.text response)
                         (.then (fn [text]
                                  (js/clearTimeout timer)
                                  (let [e (js/Error. (str "HTTP " status " " text))]
                                    (set! (.-status e) status)
                                    (throw e)))))))))
        (.catch (fn [err]
                  (js/clearTimeout timer)
                  (cond
                    ;; AbortController timeout: surface as ETIMEDOUT so the
                    ;; retry path treats it like a transport blip.
                    (= "AbortError" (.-name err))
                    (throw (aborted-error timeout))

                    :else
                    (throw err)))))))

(defn fetch-json
  "Make an HTTP request via `js/fetch` and resolve to the parsed JSON body.

   Options:
     :url         full URL (required)
     :method      HTTP method (default \"GET\")
     :headers     map of header name → value
     :body        Clojure data, JSON-serialised (omit for GET / DELETE)
     :timeout     ms before the request is aborted (default 10000)
     :max-retries additional attempts on retriable transport errors or
                  408/425/429/5xx responses (default 0, i.e. no retry)

   Rejects with the last error after retries are exhausted."
  [{:keys [max-retries] :or {max-retries 0} :as options}]
  (letfn [(retry-loop [attempt]
            (-> (attempt-once options)
                (.catch (fn [err]
                          (if (and (retriable-error? err)
                                   (< attempt max-retries))
                            (do
                              (js/console.warn
                               (str "[http] retry "
                                    (inc attempt)
                                    "/" max-retries
                                    " after "
                                    (or (.-status err)
                                        (.-code err)
                                        (j/get-in err [:cause :code])
                                        (.-message err))))
                              (js/Promise.
                               (fn [resolve reject]
                                 (js/setTimeout
                                  (fn []
                                    (-> (retry-loop (inc attempt))
                                        (.then resolve reject)))
                                  (backoff-ms (inc attempt))))))
                            (throw err))))))]
    (retry-loop 0)))
