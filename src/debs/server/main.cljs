(ns debs.server.main
  "Server entry point. Wires up the database and starts the HTTP listener."
  (:require
   ["http" :as http]
   [applied-science.js-interop :as j]
   [debs.server.db :as db]
   [debs.server.handlers :as handlers]))

(defn init
  "Initialise the database and start the HTTP server on port 3000."
  []
  (let [db-path (or (j/get js/process.env "DEBS_DB_PATH") "debs.db")]
    (println ">>> initializing database at" db-path)
    (db/init-db! db-path))
  (-> http
      (.createServer handlers/router)
      (.listen 3000 (fn [] (println "Server running on port 3000")))))
