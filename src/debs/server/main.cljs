(ns debs.server.main
  (:require
   ["http" :as http]
   ["https" :as https]
   ["url" :as url]
   ["buffer" :as buffer]))

(def allowed-origin "dev.mad.is")

(defn allowed-origin? [req]
  (let [origin (-> req (js->clj) (get "headers") (get "origin"))
        host (-> req (js->clj) (get "headers") (get "host"))]
    (or (= origin (str "https://" allowed-origin))
        (= host allowed-origin))))

(defn write-json [res data]
  (.write res (.stringify js/JSON (clj->js data)))
  (.end res))

(defn health-handler [req res]
  (doto res
    (.setHeader "Content-Type" "application/json"))
  (write-json res {:status :ok}))

(defn tweet-info-handler [req res]
  (if-not (allowed-origin? req)
    (do
      (.writeHead res 403 #js {:Content-Type "application/json"})
      (.end res (.stringify js/JSON (clj->js {:error "Forbidden"}))))
    (let [parsed-url (url/parse (.-url req) true)
          query-params (.-query parsed-url)
          tweet-id (or (.-tweetId query-params) (.-id query-params) "")]
      (-> (https/get #js {:hostname "api.twitterapi.io"
                          :path (str "/twitter/tweets?id=" tweet-id)
                          :headers #js {"Content-Type" "application/json"
                                        "X-API-Key" "abc-123"}})
          (.on "error" (fn [err]
                         (.writeHead res 502 #js {:Content-Type "application/json"})
                         (.end res (.stringify js/JSON (clj->js {:error "Proxy error" :message (.-message err)})))))
          (.on "response" (fn [proxy-res]
                            (let [chunks (atom [])]
                              (-> proxy-res
                                  (.on "data" (fn [chunk] (swap! chunks conj chunk)))
                                  (.on "end" (fn []
                                               (.writeHead res 200 #js {:Content-Type "application/json"})
                                               (.end res (.concat buffer (into-array @chunks)))))))))))))

(defn router [req res]
  (let [pathname (.-pathname (url/parse (.-url req)))]
    (cond
      (= pathname "/health") (health-handler req res)
      (= pathname "/tweet-info") (tweet-info-handler req res)
      :else
      (do
        (.writeHead res 404 #js {:Content-Type "application/json"})
        (.end res (.stringify js/JSON (clj->js {:error "Not found"})))))))

(defn init
  []
  (-> http
      (.createServer router)
      (.listen 3000 (fn [] (println "Server running on port 3000")))))
