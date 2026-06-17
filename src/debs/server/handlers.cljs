(ns debs.server.handlers
  "HTTP route handlers. Each handler is a linear pipeline: read input,
   call one API client, write response. Cross-cutting concerns (CORS,
   body parsing, outgoing fetch calls) live in debs.server.http."
  (:require
   ["url" :as url]
   [clojure.string :as str]
   [debs.server.db :as db]
   [debs.server.http :as http]
   [debs.server.openrouter :as openrouter]
   [debs.server.twitter :as twitter]))

(defn health-handler [_req ^js res]
  (http/send-json res 200 {:status :ok}))

(defn options-handler [^js res]
  (.writeHead res 204 (http/cors-headers))
  (.end res))

(defn not-found-handler [^js res]
  (http/send-error res 404 {:error "Not found"}))

;; --- /generator ------------------------------------------------------------

(defn- build-prompt
  "Combine instructions + tweet into the OpenRouter prompt."
  [original-text instructions]
  (str "<instructions>\n" instructions "</instructions>\n" "<tweet>\n" original-text "\n</tweet>"))

(defn- generator-input
  "Pull the two required fields from the parsed body. Returns the prompt
   string, or nil when the body is missing one of the fields."
  [body]
  (let [original-text (get body "original_text")
        instructions   (get body "instructions")]
    (when (and original-text instructions)
      (build-prompt original-text instructions))))

(defn generator-handler [req ^js res]
  (-> (http/read-body req)
      (.then (fn [body]
               (println ">>> generating for:" (str (subs (get body "original_text") 0 30) "..."))
               (if-let [prompt (generator-input body)]
                 (-> (openrouter/chat-completion prompt)
                     (.then #(http/send-json res 200 {:response %}))
                     (.catch (fn [err]
                               (js/console.error "openrouter error" err)
                               (http/send-error res 502 {:error "Generation failed"}))))
                 (http/send-error res 400 {:error "Missing parameters"}))))
      (.catch (fn [err]
                (js/console.error "generator body error" err)
                (http/send-error res 400 "Invalid request body")))))

;; --- /tweet-info -----------------------------------------------------------

(defn- tweet-id-from
  "Extract the tweet id from the request query string. Accepts either
   `tweetId` or `id`."
  [req]
  (let [params (.-query (url/parse (.-url req) true))]
    (or (.-tweetId params) (.-id params) "")))

(defn- send-cached
  [tweet-id res cached]
  (println ">>> cache hit for" tweet-id)
  (http/send-json res 200 cached))

(defn- fetch-and-cache
  "Hit twitterapi.io, persist the result, and reply. Errors become 502."
  [tweet-id res]
  (println ">>> cache miss, fetching from API for" tweet-id)
  (-> (twitter/fetch-tweet tweet-id)
      (.then (fn [data]
               (when data (db/save-tweet! tweet-id data))
               (http/send-json res 200 data)))
      (.catch (fn [err]
                (js/console.error "twitter error" err)
                (http/send-error res 502 {:error   "Proxy error"
                                          :message (.-message err)
                                          :code    (or (.-code err)
                                                       (.-statusCode err))})))))

(defn tweet-info-handler [req ^js res]
  (let [tweet-id (tweet-id-from req)]
    (println ">>> tweet-id:" tweet-id)
    (if-let [cached (db/get-tweet tweet-id)]
      (send-cached tweet-id res cached)
      (fetch-and-cache tweet-id res))))

;; --- router ----------------------------------------------------------------

(def ^:private routes
  [[:options :any         (fn [_req res] (options-handler res))]
   [:get     "/health"     health-handler]
   [:get     "/tweet-info" tweet-info-handler]
   [:post    "/generator"  generator-handler]])

(defn- match-route [method pathname]
  (some (fn [[m p h]]
          (when (and (= (name m) (str/lower-case method))
                     (or (= p :any) (= p pathname)))
            h))
        routes))

(defn router [req ^js res]
  (let [pathname (.-pathname (url/parse (.-url req)))]
    (if-let [handler (match-route (.-method req) pathname)]
      (handler req res)
      (not-found-handler res))))
