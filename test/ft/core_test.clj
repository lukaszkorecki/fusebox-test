(ns ft.core-test
  (:require [ft.core :as ft]
            [clojure.test :refer [deftest testing is use-fixtures]]))

(def attempts (atom 0))

(defn update-attempt-count [c]
  (reset! attempts (inc c)))

(use-fixtures :each (fn [test]
                      (reset! attempts [])
                      (test)))

(deftest no-retries-just-works-test
  (is (= {:status 200 :body {:msg "ok"}}
         (ft/req-w-retry {:req {:resp :ok}
                          :track-fn update-attempt-count})))
  (is (= 1 @attempts)))

(deftest no-retries-on-not-found-test
  (is (= {:status 404 :body {:msg "gone"}}
         (ft/req-w-retry {:req {:resp :not-found}
                          :track-fn update-attempt-count})))
  (is (= 1 @attempts)))

(deftest error-retries-test
  (is (= {:status 200 :body {:msg "finally working"}}
         (ft/req-w-retry {:req {:resp :srv-err}
                          :track-fn update-attempt-count})))

  (is (= 3 @attempts)))

(deftest exc-retries-test
  (is (= {:status 200 :body {:msg "finally working"}}
         (ft/req-w-retry {:req {:resp :exc
                                :exc-class (java.net.UnknownHostException.)}
                          :track-fn update-attempt-count})))

  (is (= 4 @attempts)))
