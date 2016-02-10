(ns krill.events
  (:require [krill.owner-store.core :as owner-store]
            [clojure.java.jdbc :as jdbc]
            [clojure.data.json :as json]
            [clojure.pprint :as pprint]
            [taoensso.timbre :as tlog]
            [schema.core :as schema]
            [clj-time.format :as time-fmt]
            [clj-time.coerce]
            [krill.utils.core :as utils]
            )
  (:import [org.postgresql.util PGobject]))

(def Event
  {:id schema/Str
   (schema/optional-key :timestamp) org.joda.time.DateTime
   :category schema/Str
   :document clojure.lang.IPersistentMap})

(defprotocol PEventStore
  (add-event! [this owner-name event])
  (add-event-impl! [this owner-name event]))

(defn- prep-and-validate-event [event]
  (let [prepped-event (-> event
                          (#(cond
                              (nil? (:id %))
                              (assoc % :id (str (java.util.UUID/randomUUID)))
                              :else
                              %)))]
    (try
      (schema/validate Event prepped-event)
      (catch clojure.lang.ExceptionInfo e
        (if (= :schema.core/error (-> e ex-data :type))
          (throw (ex-info (str "Invalid event data. Error: " (.getMessage e))
                          {:type :events-exception}))
          (throw e))))))

(def abstract-event-store-impl
  {:add-event! (fn [this owner-name event]
                  (add-event-impl! this owner-name (prep-and-validate-event event)))})

(defrecord TestEventStore [owner-store event-map])

(defn create-test-event-store []
  (map->TestEventStore {:event-map (atom {})}))

(defn get-event-from-test-event-store [test-event-store owner-name id]
  (get-in @(:event-map test-event-store) [owner-name id]))

(extend TestEventStore
  PEventStore
  (assoc abstract-event-store-impl
         :add-event-impl! (fn [this owner-name event]
                            (swap! (:event-map this) assoc-in [owner-name (:id event)] event)
                            event
                             ;; (let [all-new-evts (into {} (map (fn [x] [(:id x) x]) events))
                             ;;       merged-with-owner (merge (-> this :event-map (get owner-name)) all-new-evts)]
                             ;;   (swap! (:event-map this) merge {owner-name merged-with-owner}))
                             )))

(defrecord EventStore [owner-store])

(extend-protocol jdbc/ISQLValue
  clojure.lang.IPersistentMap
  (sql-value [value]
    (doto (PGobject.)
      (.setType "jsonb")
      (.setValue (json/write-str value)))))

(extend-protocol jdbc/ISQLParameter
  org.joda.time.DateTime
  (set-parameter [val stmt idx]
    (.setObject stmt idx (clj-time.coerce/to-sql-time val))))

(extend-protocol jdbc/IResultSetReadColumn
  PGobject
  (result-set-read-column [pgobj metadata idx]
    (let [type (.getType pgobj)
          value (.getValue pgobj)]
      (case type
        "jsonb" (json/read-str value :key-fn keyword)
        :else value))))

(extend EventStore
  PEventStore
  (assoc abstract-event-store-impl
         :add-event-impl! (fn [this owner-name event]
                            (let [owner-store (:owner-store this)
                                  owner-pool (owner-store/get-or-create-owner-pool! owner-store owner-name)]
                              (if (nil? owner-pool)
                                (throw (Exception. (format "owner pool is null for owner '%s'" owner-name)))
                                (first (jdbc/insert! owner-pool :event event)))))))

(defn event-json-reader [key val]
  (cond
    (= key :timestamp)
    (time-fmt/parse (time-fmt/formatters :date-time) val)
    :else
    val))
