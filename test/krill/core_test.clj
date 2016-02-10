(ns krill.core-test
  (:require [clojure.test :refer :all]
            [krill.core :refer :all]
            [com.stuartsierra.component :as component]
            [krill.owner-store.core :as owner-store]
            [byte-streams]
            [clojure.data.json :as json]
            [taoensso.timbre :as tlog]
            [clj-time.core :as time]
            [krill.events :as events]
            [clojure.pprint :as pprint]
            [krill.utils.unittest :as unit-utils]
            [krill.utils.core :as utils])
  (:import [java.io ByteArrayInputStream]))

(use-fixtures :once unit-utils/unittest-fixture-once)

(deftest test-register-owner
  (owner-store/clear-test-owner-store (-> unit-utils/test-sys :owner-store))
  (let [resp (unit-utils/make-request (-> unit-utils/test-sys :web-app) :post "/register/test-app/" nil)]
    ;; (println (str "resp = \n" (utils/pprint resp)))
    (is (= (:status resp) 200))
    (is (-> resp :body (json/read-str :key-fn keyword) :success))))

(deftest test-register-existing-owner
  (let [store (:owner-store unit-utils/test-sys)]
    (owner-store/add-owner! store "existing-owner"))
  (is (= (unit-utils/make-request (-> unit-utils/test-sys :web-app) :post "/register/existing-owner/" nil)
         {:status 400
          :headers {}
          :body "owner existing-owner already exists"})))

(deftest test-new-event-success
  (let [req-body {:events [{:id "1"
                            :timestamp (time/date-time 2015 8 11)
                            :document {:its-a-doc true}
                            :category "unittest"}]}
        event-store (:event-store unit-utils/test-sys)
        owner-store (:owner-store unit-utils/test-sys)]
    (owner-store/add-owner! owner-store "test-owner")
    (let [resp (unit-utils/make-request (-> unit-utils/test-sys :web-app) :post "/test-owner/" req-body)]
      (is (= (:status resp) 200))
      ;; (clojure.pprint/pprint resp)
      )
    (let [added-event (events/get-event-from-test-event-store event-store "test-owner" "1")]
      ;; (println (str "added event = " (with-out-str (pprint/pprint added-event))))
      (is (not (nil? added-event)))
      (is (= (:document added-event) {:its-a-doc true})))))
