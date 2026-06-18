(ns debs.shared.data-helpers)

(defn deep-merge
  "Recursively merges maps. Later values overwrite earlier ones."
  [& maps]
  (apply merge-with
         (fn [v1 v2]
           (if (and (map? v1) (map? v2))
             (deep-merge v1 v2)
             v2))
         maps))
