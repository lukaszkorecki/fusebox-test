(ns ft.core-test
  (:require [ft.core :as ft]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [com.potetm.fusebox.retry :as-alias retry]
            [com.potetm.fusebox :as-alias fb]))

(def attempts (atom 0))

(defn update-attempt-count [c]
  (reset! attempts (inc c)))

(use-fixtures :each (fn [test]
                      (reset! attempts [])
                      (test)))

(deftest sanity-checks-test
  (testing "simple cases"
    (is (= {:status 200 :body {:msg "ok"}}
           (ft/request {:resp :ok})))

    (is (= {:status 404 :body {:msg "gone"}}
           (ft/request {:resp :not-found})))

    (is (= {:status 501 :body {:msg "oh dear"}}
           (ft/request {:resp :srv-hard-err}))))

  (testing "exception"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"test"
                          (ft/request {:resp :exc
                                       :exc (ex-info "test" {})}))))

  (testing "'eventually' working"
    (is (= [{:body {:msg "oh no"} :status 500}
            {:body {:msg "oh no"} :status 500}
            {:body {:msg "finally working"} :status 200}
            {:body {:msg "finally working"} :status 200}
            {:body {:msg "finally working"} :status 200}]

           (->> (range 0 5)
                (mapv #(ft/request {:resp :srv-err :n %})))))))

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

(deftest always-failing-test
  ;; XXX: no way of getting this response map, I get only
  ;; {:com.potetm.fusebox/error :com.potetm.fusebox.error/retries-exhausted
  ;;  :com.potetm.fusebox/spec {}
  ;;  :com.potetm.fusebox.retry/exec-duration-ms 63
  ;;  :com.potetm.fusebox.retry/num-retries 5}
  (is (= {:status 501 :body {:msg "oh dear"}}
         (ft/req-w-retry {:req {:resp :srv-hard-err}
                          :track-fn update-attempt-count})))
  (is (= 6 @attempts)))

(deftest error-retries-test
  ;; Works as expected, since eventually it works
  (is (= {:status 200 :body {:msg "finally working"}}
         (ft/req-w-retry {:req {:resp :srv-err}
                          :track-fn update-attempt-count})))
  (is (= 3 @attempts)))

(deftest exc-retries-test
  (let [exc (java.net.UnknownHostException.)]
    (is (= {:data
            {:com.potetm.fusebox/error :com.potetm.fusebox.error/retries-exhausted
             :com.potetm.fusebox/spec {} ;; XXX: why is this empty?
             :com.potetm.fusebox.retry/num-retries 5}

            :cause exc}

           (-> (ft/req-w-retry {:req {:resp :exc
                                      :exc exc}
                                :track-fn update-attempt-count})
               (update :data #(dissoc % ::retry/exec-duration-ms)))))

    (is (= 6 @attempts))))

(deftest will-not-retry-on-certain-exceptions-test
  (let [exc (ex-info "ignore me" {:ignore? true})]
    (is (= exc

           (-> (ft/req-w-retry {:req {:resp :exc
                                      :exc exc}
                                :track-fn update-attempt-count})
               :cause)))

    (is (= 1 @attempts))))
