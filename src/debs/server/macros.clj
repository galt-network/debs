(ns debs.server.macros
  (:require [clojure.java.io :as io]))

(defmacro inline-resource
  "Reads a file from the classpath (e.g. resources/) and inlines it as a string at compile time."
  [resource-path]
  (slurp (io/resource resource-path)))
