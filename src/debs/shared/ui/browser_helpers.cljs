(ns debs.shared.ui.browser-helpers
  (:require
    [applied-science.js-interop :as j]))

(defn copy-to-clipboard
  [text]
  (j/call js/navigator.clipboard :writeText text))

(defn paste-from-clipboard [callback]
  (-> (.readText js/navigator.clipboard)
      (.then (fn [text]
               (js/console.log "Pasted:" text)
               (callback text)))
      (.catch (fn [err]
                (js/console.error "Clipboard read failed:" err)))))

(defn build-url
  "Builds a full URL string from a base URL and a map of query parameters.
   Values are automatically URL-encoded.
   - base-url: string like \"https://example.com/path\" or \"https://example.com/path?existing=1\"
   - params: map (keyword or string keys) e.g. {:foo \"bar baz\" :id 42 :active true}"
  [base-url params]
  (let [url (js/URL. base-url)]                    ; parses existing query if present
    (doseq [[k v] params]
      (when (some? v)                              ; skip nil values (common pattern)
        (.set (.-searchParams url)
              (name k)
              (str v))))                           ; numbers/booleans become strings
    (str url)))
