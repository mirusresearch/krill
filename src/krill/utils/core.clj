(ns krill.utils.core
  (:require [clj-time.format :as time-fmt]))

(defn json-value-writer [key value]
  (letfn [(clj-time-to-str [x]
            (time-fmt/unparse (time-fmt/formatters :date-time) x))]
    (cond
      (instance? java.sql.Timestamp value)
      (clj-time-to-str (clj-time.coerce/from-sql-time value))
      (instance? org.joda.time.DateTime value)
      (clj-time-to-str value)
      :else
      value)))

(defn pprint [val]
  (with-out-str (clojure.pprint/pprint val)))
