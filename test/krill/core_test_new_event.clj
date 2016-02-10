(ns krill.core-test-new-event
  (:require [krill.utils.unittest :as unit-utils]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [krill.owner-store.core :as owner-store]
            [clj-time.core :as time]
            [clojure.data.json :as json]
            [krill.events :as events]
            [krill.utils.core :as utils]))

(def ^:private system (unit-utils/unittest-system))

(def ^:private ^:const owner-name "unittest-owner")

(def ^:private ^:const new-event-url (format "/%s/" owner-name))

(use-fixtures :once (fn [f]
                      (alter-var-root #'system component/start)
                      (owner-store/add-owner! (-> system :owner-store) owner-name)
                      (f)
                      (alter-var-root #'system component/stop)))

(deftest test-new-event-no-document
  (let [req-body {:events [{}]}
        resp (unit-utils/make-request (-> system :web-app) :post new-event-url req-body)]
    (is (= (:status resp) 200))
    (let [resp-body (json/read-str (:body resp)
                                   :key-fn keyword
                                   :value-fn events/event-json-reader)]
      (is (= (:error-count resp-body) 1))
      (is (-> resp-body :results first :error (.startsWith "Invalid event data."))))))

(deftest test-new-event-weird-key
  (let [new-event {:document {} :strange true}
        resp (unit-utils/make-request (-> system :web-app) :post new-event-url new-event)]
    ;; (clojure.pprint/pprint resp)
    (is (= (:status resp) 400))
    (is (= (.startsWith (:body resp) "Invalid event data.")))))

(deftest test-new-event-with-timestamp
  (let [the-timestamp (time/date-time 2015 8 14)
        req-body {:events [{:document {} :timestamp the-timestamp :category "unittest"}]}
        resp (unit-utils/make-request (-> system :web-app) :post new-event-url req-body)]
    ;; (println (str "resp = \n" (utils/pprint resp)))
    (is (= (:status resp) 200))
    (is (not (nil? (:body resp))))
    (when (= (:status resp) 200)
      (let [resp-body (json/read-str (:body resp)
                                     :key-fn keyword
                                     :value-fn events/event-json-reader)]
        ;; (println (str "body = \n" (utils/pprint resp-body)))
        (is (= (:error-count resp-body) 0))
        ;; (is (:success resp-body))
        (is (-> resp-body :results first string?))
        ;; (is (= (-> resp-body :events first :document) {}))
        ;; (is (= (-> resp-body :events first :timestamp) the-timestamp))
        ))))
