(ns debs.server.twitter
  "Client for the twitterapi.io tweet lookup API."
  (:require
   [applied-science.js-interop :as j]
   [debs.server.http :as http]))

(def ^:private api-key (j/get js/process.env "TWITTER_API_KEY"))

(def ^:private endpoint "https://api.twitterapi.io/twitter/tweets")

(defn fetch-tweet
  "Fetch raw tweet data for `tweet-id`. Resolves to the parsed JSON body
   (a Clojure map) or nil if the response is empty. Retries up to twice on
   transient transport errors and 5xx upstream responses, since the upstream
   is known to drop keep-alive sockets (ECONNRESET) under load."
  [tweet-id]
  (http/fetch-json
   {:url          (str endpoint "?tweet_ids=" tweet-id)
    :method       "GET"
    :headers      {"Content-Type" "application/json"
                   "X-API-Key"    api-key}
    :timeout      10000
    :max-retries  2}))
