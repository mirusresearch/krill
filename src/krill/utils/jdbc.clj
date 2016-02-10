(ns krill.utils.jdbc
  (:require [clojure.java.jdbc :as jdbc]
            [krill.settings :as settings]
            [taoensso.timbre :as tlog]))

(defn database-exists? [jdbc-spec db-name]
  (let [jdbc-res (jdbc/query jdbc-spec ["select true as result from pg_database where datname = ?" db-name])]
    (and (= (count jdbc-res) 1) (-> jdbc-res first :result))))


(defn- build-pg-spec [{:keys [db-name]}]
  (let [db-settings (-> (settings/get-settings) :db)]
    {:classname "org.postgresql.Driver"
     :subprotocol "postgresql"
     :subname (str "//" (:hostname db-settings) ":5432/" db-name)
     :user (:user db-settings)
     :password (:password db-settings)}))

(defn- create-pool-help
  [spec]
  (let [cpds (doto (com.mchange.v2.c3p0.ComboPooledDataSource.)
               (.setDriverClass (:classname spec)) 
               (.setJdbcUrl (str "jdbc:" (:subprotocol spec) ":" (:subname spec)))
               (.setUser (:user spec))
               (.setPassword (:password spec))
               ;; expire excess connections after 30 minutes of inactivity:
               (.setMaxIdleTimeExcessConnections (* 30 60))
               ;; expire connections after 3 hours of inactivity:
               (.setMaxIdleTime (* 3 60 60)))] 
    {:datasource cpds}))

(defn create-pool [db-name]
  (let [pg-spec (-> {:db-name db-name} build-pg-spec)]
    ;; (tlog/debug (str "creating pool - pg spec = \n" (with-out-str (clojure.pprint/pprint pg-spec))))
    (create-pool-help pg-spec)))


(defn wrap-jdbc-for-error [f]
  (try
    (f)
    (catch java.sql.BatchUpdateException bue
      (println (str "bue: " bue))
      (let [cause (.getCause bue)
            next-ex (.getNextException bue)]
        (println (str "cause: " cause))
        (when (some? cause)
          (println cause)
          ;; (.printStackTrace cause)
          )
        (println (str "next-ex: " next-ex))
        (when (some? next-ex)
          (println next-ex)
          ;; (.printStackTrace next-ex)
          )
        (throw bue)))))

