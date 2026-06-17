(ns debs.shared.validations
  (:require
    [clojure.string :as str]))

(def ^:private tweet-url-full-re
  #"^https?://(?:www\.)?(?:mobile\.)?(?:twitter|x)\.com/(?:#!/)?([A-Za-z0-9_]+)/status(?:es)?/(\d+)(?:/[^?\s#]*)?(?:\?.*)?(?:#.*)?$")

(defn tweet-id-from-url
  "Returns the tweet ID (string) if `s` is exactly one well-formed tweet URL.
   Returns nil otherwise (random text, multiple URLs, extra text, etc.)."
  [s]
  (when (and (string? s) (seq s))
    (let [lower (str/lower-case (str/trim s))]
      (when-let [[_ _ id] (re-find tweet-url-full-re lower)]
        id))))

(comment
  (tweet-id-from-url "xhttps://x.com/MadisIT/status/1726415665228141028"))
