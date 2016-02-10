(ns krill.utils.unittest
  (:require [com.stuartsierra.component :as component]
            [krill.owner-store.core :as owner-store]
            [krill.events :as events]
            [krill.core]
            [byte-streams]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as jdbc]
            [krill.utils.jdbc :as jdbc-utils]
            ))

(defn unittest-system []
  (component/system-map
   :owner-store (owner-store/create-test-owner-store)
   :event-store (component/using
                 (events/create-test-event-store)
                 [:owner-store])
   :web-app (component/using
             (krill.core/map->WebApp {})
             [:owner-store :event-store])))

(defn integration-test-system []
  (component/system-map
   :owner-store (owner-store/map->OwnerStore {})
   :event-store (component/using
                 (events/map->EventStore {})
                 [:owner-store])
   :web-app (component/using
             (krill.core/map->WebApp {})
             [:owner-store :event-store])
   ))

(defn- json-value-writer [key value]
  (cond
    (instance? org.joda.time.DateTime value)
    (str value)
    :else
    value))

(defn make-request [web-app-component req-method resource body & params]
  ;; (tlog/debug (str "body = " body "; type = " (type body) "; nil? = " (nil? body)))
  (let [prepped-body (-> body
                       (#(cond
                           (map? %)
                           (json/write-str % :value-fn json-value-writer)
                           (or (nil? %) (string? %))
                           %
                           :else
                           (throw (Exception. (str "Unable to convert body of type " (type %))))))
                       (#(cond
                          (nil? %)
                          %
                          (string? %)
                          (byte-streams/to-input-stream %)
                          :else
                          (throw (Exception. ("shouldn't be here"))))))]
    ((krill.core/make-handler
      web-app-component)
     {:request-method req-method
      :uri resource
      :body prepped-body
      :params (first params)})))

(defn remove-owner! [pool db-name]
  (jdbc/execute! pool [(format "drop database if exists %s" db-name)] :transaction? false))

(defn ensure-owner-exists! [owner-st owner-name]
  (when (not (owner-store/owner-exists? owner-st owner-name))
    (owner-store/add-owner! owner-st owner-name)))

(def ^:const owner-name "unittest-owner")

(def int-test-sys (integration-test-system))

(defn integration-test-fixture-once [f]
  ;; (println (str "before start of int-test-sys, int-test-sys = " (with-out-str (clojure.pprint/pprint int-test-sys))))
  (alter-var-root #'int-test-sys component/start)
  ;; (println (str "after start of int-test-sys, int-test-sys = " (with-out-str (clojure.pprint/pprint int-test-sys))))
  (ensure-owner-exists! (-> int-test-sys :owner-store) owner-name)
  (f)
  (alter-var-root #'int-test-sys component/stop)
  ;; (println (str "after stop of int-test-sys, int-test-sys = " (with-out-str (clojure.pprint/pprint int-test-sys))))
  )

(defn integration-test-fixture-each [f]
  (let [del-res (jdbc/delete! (owner-store/get-or-create-owner-pool! (-> int-test-sys :owner-store) owner-name) :event [])]
    ;; (println (str "!!!!!!!!!!!!!deleted all events - result = \n" (with-out-str (clojure.pprint/pprint del-res))))
    (f)))

(def test-sys (unittest-system))

(defn unittest-fixture-once [f]
  (alter-var-root #'test-sys component/start)
  (f)
  (alter-var-root #'test-sys component/stop))
