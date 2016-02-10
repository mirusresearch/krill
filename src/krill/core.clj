(ns krill.core
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :as compojure]
            [compojure.route :as route]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as tlog]
            [taoensso.timbre.appenders.core :as timbre-appenders]
            [clojure.pprint :as pprint]
            [ring.util.response :as ring-resp]
            [krill.owner-store.core :as owner-store]
            [krill.events :as events]
            [clj-time.format :as time-fmt]
            [clj-time.coerce :as time-coerce]
            [clojure.data.json :as json]
            [schema.core :as schema]
            [krill.settings :as settings]
            [krill.utils.core :as utils]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.nrepl.server :as nrepl-server]
            )
  (:gen-class))

;; are we getting the right settings?
(tlog/debug (str "settings = \n" (with-out-str (pprint/pprint (settings/get-settings)))))

(let [log-path (-> (settings/get-settings) :log-path)]
  (when (nil? log-path)
    (throw (Exception.  "empty log path")))
  (tlog/merge-config!
   {:level :debug
    :appenders {:spit (timbre-appenders/spit-appender {:fname log-path})}
    :timestamp-opts {:timezone (java.util.TimeZone/getTimeZone "America/Chicago")}
    }))

(def ^:private new-event-hit-count (agent 0))
;; (def ^:private last-twenty-events (agent []))

(defn new-events [request]
  (send new-event-hit-count inc)
  ;; (send last-twenty-events conj request)
  ;; (tlog/info (format "new event. request = \n%s" (with-out-str (pprint/pprint request))))
  (let [owner-store (-> request ::web-app :owner-store)
        owner-name (-> request :params :owner-name)]
    ;; (tlog/info (str "request = " (utils/pprint request)))
    (if (not (owner-store/owner-exists? owner-store owner-name))
      {:status 400
       :body (str "owner '" owner-name "' does not exist.  Please register the owner first.")}
      (let [body (:body request)]
        (if (nil? body)
          {:status 400
           :body "Cannot add events. Request body is empty."}
          (let [events (-> body
                           slurp
                           (json/read-str
                            :value-fn events/event-json-reader
                            :key-fn keyword)
                           :events)
                event-store (-> request ::web-app :event-store)]
            (if (nil? events)
              {:status 400
               :body "No events."}
              (letfn [(add-event! [event]
                        (try
                          (let [add-evt-res (events/add-event! event-store owner-name event)]
                            (:id add-evt-res))
                          (catch java.sql.SQLException sqle
                            {:error (.getMessage sqle)})
                          (catch clojure.lang.ExceptionInfo ei
                            (if (= :events-exception (-> ei ex-data :type))
                              {:error (.getMessage ei)}
                              (throw ei)))))]
                (let [add-evts-res (map #(add-event! %) events)
                      add-evts-err-ct (count (filter #(and (map? %) (-> % :error string?)) add-evts-res))]
                  {:status 200
                   :body (json/write-str {:error-count add-evts-err-ct
                                          :results add-evts-res}
                                         :value-fn utils/json-value-writer)})))))))))

(def ^:private register-owner-hit-count (agent 0))

(defn register-owner [request]
  (send register-owner-hit-count inc)
  ;; (tlog/info (format "registering app. request = \n%s" (with-out-str (pprint/pprint request))))
  (let [owner-name (-> request :params :owner-name)
        owner-store (-> request ::web-app :owner-store)]
    ;; (tlog/info (str "request = " (utils/pprint request)))
    (if (owner-store/owner-exists? owner-store owner-name)
        {:status 400
         :body (str "owner " owner-name " already exists")}
        (do
          (owner-store/add-owner! owner-store owner-name)
          {:status 200
           :body (json/write-str {:success true
                                  :message (str "success adding owner " owner-name)})}))))

(defn pass-the-query [request]
  (let [owner-name (-> request :params :owner-name)
        body (:body request)]
    (if (nil? body)
      {:status 400
       :body "Can't run query.  Body is null."}
      (let [query (-> body slurp)
            owner-store (-> request ::web-app :owner-store)
            q-res (jdbc/query (owner-store/get-or-create-owner-pool! owner-store owner-name) [query])]
        ;; (tlog/debug (str "q-res = \n" (utils/pprint q-res)))
        {:status 200
         :body (json/write-str q-res :value-fn utils/json-value-writer)}))))

(compojure/defroutes routes
  (compojure/GET "/" [] (ring-resp/redirect "/docs"))
  (compojure/POST "/:owner-name/" req (new-events req))
  ;; (compojure/POST "/bulk/:owner-name/" req (bulk-event-add req))
  (compojure/GET "/test-route" [] "<h1>It works</h1>")
  (compojure/POST "/register/:owner-name/" req (register-owner req))
  (compojure/GET "/error-test/" req (throw (Exception. "testing")))
  (compojure/POST "/q/:owner-name/" req (pass-the-query req))
  (route/not-found "<h1>404 - Page not found</h1>"))

(defonce ^:private raygun-client (com.mindscapehq.raygun4java.core.RaygunClient. "7EHKSOB+ylyJ0o0PQK9vuA=="))

(defn make-handler [web-app]
  ;; (println (format "web-app = %s" (with-out-str (pprint/pprint web-app))))
  (letfn [(wrap-app-component [f web-app]
            (fn [req]
              (f (assoc req ::web-app web-app))))
          (wrap-err-handling [f]
            (fn [req]
              (try
                (f req)
                (catch Exception e
                  (do
                    (tlog/error e)
                    (when (not (:is-local-vm (settings/get-settings)))
                      (.Send raygun-client e [] (select-keys req [:server-port :server-name :remote-addr :uri :query-string :scheme :request-method :headers])))
                    (throw e))))))]
    (-> routes
        (wrap-app-component web-app)
        wrap-err-handling)))


(defrecord HttpServer [web-app port]
  component/Lifecycle
  (start [component]
    (tlog/info ";; Starting HTTP Server")
    (assoc component :server (jetty/run-jetty (make-handler web-app) {:port port :join? false})))
  (stop [component]
    (tlog/info ";; Stopping HTTP Server")
    (.stop (:server component))
    (dissoc component :server)))

(defrecord WebApp [owner-store])

(defrecord ReplServer []
  component/Lifecycle
  (start [this]
    ;; (tlog/debug "starting nrepl server")
    (assoc this :nrepl-server (nrepl-server/start-server :port 7888 :bind "0.0.0.0")))
  (stop [this]
    ;; (tlog/debug "stopping nrepl server")
    (nrepl-server/stop-server (:nrepl-server this))
    (dissoc this :nrepl-server)))

(defn krill-system [& {:keys [port]
                       :or {port 8080}}]
  (component/system-map
   :owner-store (owner-store/map->OwnerStore {})
   :event-store (component/using
                 (events/map->EventStore {})
                 [:owner-store])
   :repl-server (map->ReplServer {})
   :web-app (component/using
             (map->WebApp {})
             [:owner-store :event-store])
   :http-server (component/using
                 (map->HttpServer {:port port})
                 [:web-app])))

(defonce system (krill-system))

(defn -main [& args]
  (alter-var-root #'system component/start))
