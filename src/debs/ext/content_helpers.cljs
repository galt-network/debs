(ns debs.ext.content-helpers
  "Content script: injects a Reply Helper button into X/Twitter tweets.
   Compile with shadow-cljs (browser target) and load as a content script."
  (:require
   [clojure.string]
   [applied-science.js-interop :as j]
   [debs.ext.messaging :as messaging]
   [oops.core :refer [oset!]]
   [goog.array :as garray]))

(def tweet-selector
  "article[data-testid=\"tweet\"], article[role=\"article\"]")

(def processed-attr "data-rh-processed")

;; --------------------------------------------------
;; Helper functions (implement / customize these)
;; --------------------------------------------------

(defn get-tweet-text [tweet]
  ;; Basic implementation — replace with your real logic if needed
  ;; (e.g. handling links, mentions, or multiple child spans)
  (or (some-> tweet
              (.querySelector "[data-testid=\"tweetText\"]")
              .-textContent
              clojure.string/trim)
      ""))

(defn get-tweet-data [tweet]
  (let [user-name-el (.querySelector tweet "[data-testid=\"User-Name\"]")
        time-el      (.querySelector tweet "time")
        reply-btn    (.querySelector tweet "[data-testid=\"reply\"]")
        repost-btn   (.querySelector tweet "[data-testid=\"retweet\"]")
        permalink-el (some-> time-el (.closest "a[href*=\"/status/\"]"))]
    {:display-name   (some-> user-name-el
                             (.querySelector "span:first-child")
                             .-textContent
                             .trim)
     :username       (some-> user-name-el
                             (.querySelector "a")
                             .-href
                             (.split "/")
                             (aget 3))
     :date-iso       (some-> time-el (.getAttribute "datetime"))
     :date-human     (some-> time-el .-textContent .trim)
     :permalink      (some-> permalink-el .-href)
     :tweet-id       (some-> permalink-el
                             .-href
                             (.split "/status/")
                             (aget 1)
                             (.split "?")
                             (aget 0))
     :replies-count  (some-> reply-btn .-textContent .trim)
     ; :reply-btn      reply-btn
     ; :repost-btn     repost-btn
     :text           (some-> (.querySelector tweet "[data-testid=\"tweetText\"]")
                             .-textContent
                             .trim)
     :likes-count    (some-> (.querySelector tweet "[data-testid=\"like\"]")
                             .-textContent .trim)
     :reposts-count  (some-> (.querySelector tweet "[data-testid=\"retweet\"]")
                             .-textContent .trim)}))

(defn show-reply-helper-panel [text meta tweet-el]
  (messaging/send-message {:type :select-post :content text :meta meta})
  (js/console.log "Reply Helper opened for tweet:" tweet-el))

;; --------------------------------------------------
;; Core injection logic
;; --------------------------------------------------

(defn inject-reply-button [tweet]
  (when-not (.hasAttribute tweet processed-attr)
    (when (.querySelector tweet "[data-testid=\"tweetText\"]")
      (.setAttribute tweet processed-attr "true")

      (when-let [action-group (.querySelector tweet "div[role=\"group\"]")]
        (let [btn (js/document.createElement "button")]
          (set! (.-innerHTML btn) "✍️")
          (set! (.-title btn) "Reply Helper")
          (.setAttribute btn "aria-label" "Open Reply Helper")
          (set! (.-className btn) "rh-action-btn")

          (.addEventListener btn "click"
            (fn [e]
              (.stopImmediatePropagation e)
              (.preventDefault e)
              (let [text (get-tweet-text tweet)
                    meta (get-tweet-data tweet)]
                (show-reply-helper-panel text meta tweet))))

          ;; Insert right after the native Reply button when possible
          (if-let [reply-btn (.querySelector action-group "[data-testid=\"reply\"]")]
            (if-let [parent (.-parentElement reply-btn)]
              (.insertBefore parent btn (.-nextSibling reply-btn))
              (.prepend action-group btn))
            (.prepend action-group btn)))))))

;; --------------------------------------------------
;; Initial scan + live observer (infinite scroll, navigation, etc.)
;; --------------------------------------------------

(defn init-reply-helper! []
  ;; 1. Process tweets that already exist on the page
  (garray/forEach (.querySelectorAll js/document tweet-selector)
                  inject-reply-button)

  ;; 2. Watch for new tweets being added (MutationObserver)
  (let [observer (js/MutationObserver.
                   (fn [mutations]
                     (garray/forEach mutations
                       (fn [mutation]
                         (garray/forEach (.-addedNodes mutation)
                           (fn [node]
                             (when (= (.-nodeType node) (.-ELEMENT_NODE js/Node))
                               ;; Direct match
                               (when (and (.-matches node)
                                          (.matches node tweet-selector))
                                 (inject-reply-button node))
                               ;; Nested tweets (common with React re-renders)
                               (garray/forEach (.querySelectorAll node tweet-selector)
                                               inject-reply-button))))))))]
    (.observe observer js/document.body #js {:childList true :subtree true})
    ;; Return the observer so you can disconnect it later if needed
    ;; (e.g. when the extension is disabled/unloaded)
    observer))

;; --------------------------------------------------
;; Boot the script
;; --------------------------------------------------

;; Call this once when your content script loads.
;; Using defonce prevents double-initialization during Shadow-CLJS hot reloads in development.
(comment
  (defonce ^:private reply-observer
    (init-reply-helper!)))

;; If you prefer to control startup manually, comment out the defonce above
;; and call (init-reply-helper!) from your entry point instead.
