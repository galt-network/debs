(ns debs.ext.content-helpers
  "Content script: injects a Reply Helper button into X/Twitter tweets.
   Compile with shadow-cljs (browser target) and load as a content script."
  (:require
   [clojure.string :as str]))

(def BUTTON-CLASS "ai-reply-btn")
(def BUTTON-CLASSES "button is-small is-info ml-1") ; Bulma + tiny margin

(defn extract-tweet-data [article]
  (let [user-name-el   (.querySelector article "div[data-testid='User-Name']")
        first-a        (.querySelector user-name-el "a[role='link']")
        all-as         (array-seq (.querySelectorAll user-name-el "a[role='link']"))
        display-name   (if first-a (str/trim (.-textContent first-a)) "")
        username       (if (> (count all-as) 1)
                         (str/replace (.-textContent (second all-as)) #"^@" "")
                         "")
        time-el        (.querySelector article "time[datetime]")
        iso-date       (if time-el (.getAttribute time-el "datetime") "")
        status-link    (if time-el (.-parentElement time-el) nil)
        tweet-link     (if status-link (.-href status-link) "")
        tweet-id       (if-let [m (re-find #"/status/(\d+)" tweet-link)]
                         (second m)
                         "")
        tweet-text-el  (.querySelector article "div[data-testid='tweetText']")
        tweet-text     (if tweet-text-el (str/trim (.-textContent tweet-text-el)) "")
        action-group   (.querySelector article "div[role='group'][aria-label*='replies']")
        aria-label     (if action-group (.getAttribute action-group "aria-label") "")
        replies-count  (if-let [m (re-find #"(\d+)\s+replies?" aria-label)]
                         (js/parseInt (second m) 10)
                         0)]
    {:display-name  display-name
     :username      username
     :tweet-link    tweet-link
     :iso-date      iso-date
     :tweet-id      tweet-id
     :replies-count replies-count
     :tweet-text    tweet-text}))

(defn create-reply-button [article on-tweet-selected]
  (let [btn (js/document.createElement "button")]
    (set! (.-className btn) (str BUTTON-CLASS " " BUTTON-CLASSES))
    (set! (.-type btn) "button")
    (set! (.-innerHTML btn) "✍️")
    (set! (.-title btn) "Select tweet for reply assistance")

    (.addEventListener
      btn "click"
      (fn [e]
        (.preventDefault e)
        (.stopPropagation e)
        (let [data (extract-tweet-data article)]
          (on-tweet-selected data))))
    btn))

(defn add-reply-button-if-needed! [article on-tweet-selected]
  (when (and article
             (not (.querySelector article (str "." BUTTON-CLASS))))
    (when-let [reply-btn (.querySelector article "button[data-testid='reply']")]
      (let [wrapper (.-parentElement reply-btn)]
        (.appendChild wrapper (create-reply-button article on-tweet-selected))))))

(defn process-existing-tweets! [{:keys [on-tweet-selected]}]
  (doseq [article (array-seq (js/document.querySelectorAll "article[data-testid='tweet']"))]
    (add-reply-button-if-needed! article on-tweet-selected)))

(defn process-mutations
  [{:keys [on-tweet-selected]} mutations]
  (doseq [mutation (array-seq mutations)]
    (when (= (.-type mutation) "childList")
      (doseq [node (array-seq (.-addedNodes mutation))]
        (when (and node (= (.-nodeType node) 1))
          ;; Direct article
          (when (and (= (.-tagName node) "ARTICLE")
                     (= (.getAttribute node "data-testid") "tweet"))
            (add-reply-button-if-needed! node on-tweet-selected))
          ;; Articles inside newly added containers (cellInnerDiv, etc.)
          (doseq [art (array-seq (.querySelectorAll node "article[data-testid='tweet']"))]
            (add-reply-button-if-needed! art on-tweet-selected)))))))

(defn setup-mutation-observer!
  [{:keys [on-tweet-selected]}]
  (let [observer (js/MutationObserver. (partial process-mutations {:on-tweet-selected on-tweet-selected}))]
    ;; Observe the whole document (necessary for X's virtualized timeline)
    (.observe observer js/document.body #js {:childList true :subtree true})
    observer))
