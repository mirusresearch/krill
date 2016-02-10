(ns krill.owner-store.core
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [krill.utils.jdbc :as jdbc-utils]
            [taoensso.timbre :as tlog]))

(defn- test-conn [db]
  (jdbc/query (:pool db) ["SELECT NOW();"]))

(defn- create-database! [store db-name]
  (jdbc/execute! (:pool store) [(format "CREATE DATABASE %s" db-name)] :multi? false :transaction? false))


(defrecord FieldDef [pg-def])

(def ^:const event-fields
  {:id (->FieldDef "VARCHAR(100) NOT NULL PRIMARY KEY")
   :created (->FieldDef "TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()")
   :timestamp (->FieldDef "TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()")
   :category (->FieldDef "VARCHAR(255) NOT NULL")
   :document (->FieldDef "JSONB NOT NULL")})

(defn- build-event-table-jdbc-def []
  (into [:event] (map (fn [[k v]] [k (:pg-def v)]) event-fields)))

(defn- create-main-event-tbl! [db-pool]
  (jdbc/db-do-commands db-pool
                       (apply jdbc/create-table-ddl (build-event-table-jdbc-def))))

(def ^:private owner-pools (atom {}))

(defn- add-owner-pool! [owner-pool owner-name]
  (swap! owner-pools assoc (keyword owner-name) owner-pool))

(defn- owner-name->db-name [app-name]
  (clojure.string/replace app-name #"-" "_"))

(defrecord OwnerStore [])

(defprotocol POwnerStore
  (add-owner! [this owner-name])
  (owner-exists? [this owner-name])
  (get-or-create-owner-pool! [this owner-name]))

(extend-type OwnerStore
  component/Lifecycle
  (start [this]
    (assoc this :pool (jdbc-utils/create-pool "postgres")))
  (stop [this]
    (doseq [[owner-name-kw pool] @owner-pools]
      (println (str "close pool for " owner-name-kw))
      (.close (-> pool :datasource))
      ;; below is because c3p0 isn't really releasing the connections until the jvm is stopped.  That's
      ;; fine when running the tests outside of the repl, but not with the repl.
      (jdbc/query (-> this :pool) ["select pg_terminate_backend(pid) from pg_stat_activity where datname = ?" (owner-name->db-name (name owner-name-kw))]))
    (.close (-> this :pool :datasource))
    
    (assoc this :pool nil))
  POwnerStore
  (add-owner! [store owner-name]
    (jdbc-utils/wrap-jdbc-for-error
     (fn []
       (let [db-name (owner-name->db-name owner-name)]
         (create-database! store db-name)
         (let [new-pool (jdbc-utils/create-pool db-name)]
           (add-owner-pool! new-pool owner-name)
           (create-main-event-tbl! new-pool))))))
  (owner-exists? [store owner-name]
    (let [corrected-owner-name (owner-name->db-name owner-name)]
      (jdbc-utils/database-exists? (:pool store) corrected-owner-name)))
  (get-or-create-owner-pool! [store owner-name]
    (let [db-name (owner-name->db-name owner-name)]
      (when (not (contains? (set (keys @owner-pools)) (keyword owner-name)))
        (do
          (tlog/info (format "creating owner pool for owner '%s'" owner-name))
          (-> db-name jdbc-utils/create-pool (add-owner-pool! owner-name)))))
    ((keyword owner-name) @owner-pools)))

(defrecord TestOwnerStore [set-of-owners])

(defn create-test-owner-store []
  (map->TestOwnerStore {:set-of-owners (atom #{})}))

(extend-type TestOwnerStore
  POwnerStore
  (add-owner! [store owner-name]
    (swap! (:set-of-owners store) conj owner-name))
  (owner-exists? [store owner-name]
    (contains? @(:set-of-owners store) owner-name)))

(defn clear-test-owner-store [owner-store]
  (println "clearing owner store!!")
  (reset! (:set-of-owners owner-store) #{}))
