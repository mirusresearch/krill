(ns user
  "From http://thinkrelevance.com/blog/2013/06/04/clojure-workflow-reloaded
  and https://github.com/stuartsierra/component
  "
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :refer (pprint)]
            [clojure.repl :refer :all]
            [clojure.test :as test]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [com.stuartsierra.component :as component]
            [krill.core :as krill-core]))

(def system nil)

(defn init []
  (alter-var-root #'system
    (constantly (krill-core/krill-system))))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system
    (fn [s] (when s (component/stop s)))))

(defn go []
  (init)
  (start))

(defn reset []
  (stop)
  (refresh :after 'user/go))
