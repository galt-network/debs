(ns debs.shared.time-helpers
  (:require
    [clojure.string :as str]))

(defn relative-time-str
  "Returns strings like '1 minute ago', '6 hours ago', 'just now', 'in 2 days'.
   Works for both past and future timestamps."
  ([timestamp]
   (relative-time-str timestamp (js/Date.)))
  ([timestamp now]
   (let [diff-ms   (- (.getTime now) (.getTime timestamp))
         diff-sec  (js/Math.round (/ diff-ms 1000))
         abs-sec   (js/Math.abs diff-sec)
         locale    (or js/navigator.language "en")
         rtf       (js/Intl.RelativeTimeFormat. locale
                     #js {:numeric "auto" :style "long"})]
     (cond
       (< abs-sec 10)          "just now"
       (< abs-sec 45)          (.format rtf diff-sec "second")
       (< abs-sec 90)          (.format rtf (js/Math.round (/ diff-sec 60)) "minute")
       (< abs-sec 2700)        (.format rtf (js/Math.round (/ diff-sec 60)) "minute")
       (< abs-sec 5400)        (.format rtf (js/Math.round (/ diff-sec 3600)) "hour")
       (< abs-sec 86400)       (.format rtf (js/Math.round (/ diff-sec 3600)) "hour")
       (< abs-sec (* 86400 2)) (.format rtf (js/Math.round (/ diff-sec 86400)) "day")
       :else                   (.format rtf (js/Math.round (/ diff-sec 86400)) "day")))))
