(ns krill.core-test-new-event-integration
  (:require [krill.owner-store.core :as owner-store]
            [krill.events :as events]
            [clojure.pprint :as pprint]
            [krill.utils.unittest :as unit-utils]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [clojure.data.json :as json]
            [krill.utils.core :as utils]))

(use-fixtures :once unit-utils/integration-test-fixture-once)
(use-fixtures :each unit-utils/integration-test-fixture-each)

(def ^:private ^:const new-event-uri (format "/%s/" unit-utils/owner-name))

(deftest ^:integration test-new-event-success
  (let [req-body {:events [{:document {:what "nothing"}
                            :category "testing"}]}
        own-st (-> unit-utils/int-test-sys :owner-store)
        evt-st (-> unit-utils/int-test-sys :event-store)
        owner-pool (owner-store/get-or-create-owner-pool! own-st unit-utils/owner-name)
        resp (unit-utils/make-request (-> unit-utils/int-test-sys :web-app) :post new-event-uri req-body)]
    ;; (println (str "resp = \n" (utils/pprint resp)))
    (is (= (:status resp) 200))
    (when (= (:status resp) 200)
      (let [resp-body (json/read-str (:body resp)
                                     :key-fn keyword
                                     :value-fn events/event-json-reader)]
        ;; (is (:success resp-body))
        (is (= (:error-count resp-body) 0))
        (is (-> resp-body :results first string?))))
    (let [db-res (jdbc/query owner-pool ["select * from event"])]
      ;; (println (str "res = " (with-out-str (pprint/pprint db-res))))
      (is (= (count db-res) 1))
      (let [the-doc (-> db-res first :document)]
        (is (= the-doc {:what "nothing"}))))))

;; (deftest ^:integration test-new-event-no-document
;;   (let [new-event {}]
;;     (unit-utils/make-request (-> unit-utils/int-test-sys :web-app) :post (format "/%s/" unit-utils/owner-name) new-event)))

(deftest ^:integration test-new-event-an-error
  (let [req-body {:events [{:document {:everthing "'s gonna be alright"}
                            :category "should-pass"}
                           {:category "should-fail-no-doc"}]}
        resp (unit-utils/make-request (-> unit-utils/int-test-sys :web-app) :post new-event-uri req-body)]
    ;; (println (str "resp = " (utils/pprint resp)))
    (is (= (:status resp) 200))
    (when (= (:status resp) 200)
      (let [resp-body (json/read-str (:body resp)
                                     :key-fn keyword
                                     :value-fn events/event-json-reader)
            results (:results resp-body)]
        (is (= (:error-count resp-body) 1))
        (is (-> results (nth 0) string?))
        (let [err-res (nth results 1)]
          (is (-> err-res map?))
          (is (-> err-res :error string?))
          (is (-> err-res :error (.startsWith "Invalid event data."))))))))
