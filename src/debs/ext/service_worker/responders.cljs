(ns debs.ext.service-worker.responders
  (:require
    [lambdaisland.fetch :as fetch]))

(defn generate-response
  [{:keys [text responder-type on-success on-failure]}]
  (let [options {:accept :json
                 :content-type :json
                 :body {:query text
                        :mode "bypass"}}]
    (-> (fetch/post "http://localhost:9621/query" options)
        (.then (fn [res]
                 (js/console.log "FETCH success" res)
                 (on-success (-> res :body :response))))
        (.catch (fn [err]
                  (js/console.log "FETCH error" err)
                  (on-failure err))))
    ))
