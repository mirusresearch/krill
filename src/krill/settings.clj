(ns krill.settings
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn- get-local-settings []
  (let [local-settings-f (io/as-file "local-settings.edn")]
    (if (.exists local-settings-f)
      (edn/read-string (slurp local-settings-f))
      {})))

(defn get-settings []
  (let [default-settings {:log-path "./krill-clj.log"
                          :is-local-vm false}]
    (into default-settings (get-local-settings))))
