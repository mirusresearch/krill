;; (ns krill.core-test-bulk-event-add-integration
;;   (:require [clojure.test :refer :all]
;;             [krill.utils.unittest :as unit-utils]
;;             [clojure.data.json :as json]))

;; (use-fixtures :once unit-utils/integration-test-fixture-once)
;; (use-fixtures :each unit-utils/integration-test-fixture-each)
;; ;; (use-fixtures :each unit-utils/test-test-fixture-each)

;; (deftest ^:integration test-success
;;   (let [events {:bulk [{:category "bulk-ut" :document {:nothing "to see here"}}
;;                        {:category "bulk-ut" :document {:everything "is awesome"}}]}
;;         resp (unit-utils/make-request (-> unit-utils/int-test-sys :web-app) :post (format "/bulk/%s/" unit-utils/owner-name) events)]
;;     ;; (println (str "!!!!!! - resp = \n" (with-out-str (clojure.pprint/pprint resp))))
;;     (is (= (:status resp 200)))
;;     (let [actual-events (-> resp :body (json/read-str :key-fn keyword) :events)]
;;       (is (= (count actual-events) 2)))))

