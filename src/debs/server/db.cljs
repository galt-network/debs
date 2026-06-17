(ns debs.server.db
  "SQLite database for tweet caching."
  (:require
   ["better-sqlite3" :as sqlite]
   [applied-science.js-interop :as j]))

(defonce db (atom nil))

(defn init-db!
  "Initialize the SQLite database and create the tweets table if it doesn't exist."
  [db-path]
  (let [db-instance (sqlite db-path)]
    ;; Create tweets table if it doesn't exist
    (.exec db-instance "CREATE TABLE IF NOT EXISTS tweets (id TEXT PRIMARY KEY, data TEXT)")
    (reset! db db-instance)))

(defn get-tweet
  "Get tweet data by ID from the database. Returns the parsed data or nil if not found."
  [id]
  (when-let [db @db]
    (let [stmt (j/call db :prepare "SELECT data FROM tweets WHERE id = ?")
          row (j/call stmt :get id)]
      (when row
        (let [data (j/get row "data")]
          (some-> data js/JSON.parse clj->js))))))

(defn save-tweet!
  "Save tweet data to the database. Overwrites if already exists."
  [id data]
  (when-let [db @db]
    (let [stmt (j/call db :prepare "INSERT OR REPLACE INTO tweets (id, data) VALUES (?, ?)")]
      (j/call stmt :run id (js/JSON.stringify (clj->js data))))))