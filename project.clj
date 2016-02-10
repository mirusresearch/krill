(defproject krill "0.1.0-SNAPSHOT"
  :description "Event Service"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 ;; [ring/ring-defaults "0.1.5"]
                 [ring/ring-core "1.4.0"]
                 [compojure "1.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [com.stuartsierra/component "0.2.3"]
                 [com.taoensso/timbre "4.0.2" :exclusions [org.clojure/tools.reader]]
                 [org.clojure/java.jdbc "0.4.1"]
                 [org.postgresql/postgresql "9.4-1200-jdbc41"]
                 [com.mchange/c3p0 "0.9.5.1"]
                 [byte-streams "0.2.0"]
                 [org.clojure/data.json "0.2.6"]
                 [clj-time "0.10.0"]
                 [prismatic/schema "0.4.3"]
                 [com.mindscapehq/core "2.0.0"]
                 [org.clojure/tools.nrepl "0.2.10"]
                 ]
  ;; :plugins [[lein-ring "0.9.6"]]
  ;; :ring {:handler krill.core/routes}
  :main ^:skip-aot krill.core
  ;; :aot [krill.core]
  :profiles {:dev {:source-paths ["src-dev"]
                   :repl-options {:init-ns user}
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [javax.servlet/servlet-api "2.5"]]}
             :uberjar {:uberjar-name "krill-standalone.jar.temp"
                       :aot :all}}
  :test-selectors {:default (complement :integration)
                   :integration :integration}
  )
