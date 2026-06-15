(ns debs.shared.x-adapters
  (:require
    [clojure.string :as str]
    [lambdaisland.fetch :as fetch]))

(defn extract-tweet-text
  "Extracts clean tweet text from the oEmbed :html field.
   - Turns <br> into newlines
   - Removes all HTML tags and attribution
   - Returns plain text"
  [oembed-html]
  (when (and oembed-html (not (str/blank? oembed-html)))
    (let [temp-div (js/document.createElement "div")]
      (set! (.-innerHTML temp-div) oembed-html)
      (when-let [p (.querySelector temp-div "blockquote p")]
        (-> (.-innerText p)
            str/trim)))))

(defn fetch-tweet
  [{:keys [tweet-url on-success on-failure]}]
  (let [options {:accept :json
                 :content-type :json}
        request-url (str "https://publish.x.com/oembed?url=" tweet-url)]
    (-> (fetch/get request-url options)
        (.then (fn [res]
                 (js/console.log "fetch-tweet success" res)
                 (on-success (-> res :body))))
        (.catch (fn [err]
                  (js/console.log "fetch-tweet error" err)
                  (on-failure err))))))

(comment
  (def result (atom nil))
  (fetch-tweet {:tweet-url "https://x.com/MadisIT/status/1726415665228141028"
                :on-success #(reset! result %)
                :on-failure #(reset! result %)})
  (def last-result
    {:author_url "https://x.com/MadisIT",
     :width 550,
     :type "rich",
     :provider_name "X",
     :cache_age "3153600000",
     :url "https://x.com/MadisIT/status/1726415665228141028",
     :author_name "Madis Nõmme",
     :version "1.0",
     :provider_url "https://x.com",
     :height nil,
     :html "<blockquote class=\"twitter-tweet\"><p lang=\"es\" dir=\"ltr\">El liberalismo es el respeto irrestricto del proyecto de vida del prójimo, basado en el principio de no agresión y en defensa del derecho a la vida, a la libertad y a la propiedad.<br><br>Liberalism is the unconditional respect for the life project of the fellow man under the principle… <a href=\"https://t.co/ANvkCPR9Jx\">pic.twitter.com/ANvkCPR9Jx</a></p>&mdash; Madis Nõmme (@MadisIT) <a href=\"https://x.com/MadisIT/status/1726415665228141028?ref_src=twsrc%5Etfw\">November 20, 2023</a></blockquote>\n<script async src=\"https://platform.x.com/widgets.js\" charset=\"utf-8\"></script>\n\n"})
  (extract-tweet-text (:html last-result)))
