(ns krill.owner-store-test-integration
  (:require [clojure.test :refer :all]
            [krill.owner-store.core :refer :all]
            [com.stuartsierra.component :as component]
            [krill.core]
            [krill.utils.jdbc :as jdbc-utils]
            [clojure.java.jdbc :as jdbc]
            [krill.utils.unittest :as unit-utils]))

(use-fixtures :once unit-utils/integration-test-fixture-once)

(deftest ^:integration test-add-owner
  (let [owner-name "unittest-owner"
        db-name "unittest_owner"
        own-st (-> unit-utils/int-test-sys :owner-store)]
    (unit-utils/remove-owner! (-> own-st :pool) db-name)
    (add-owner! own-st owner-name)
    (is (jdbc-utils/database-exists? (-> own-st :pool) db-name))))
