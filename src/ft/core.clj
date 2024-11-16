(ns ft.core
  (:require [com.potetm.fusebox.retry :as retry]))

(defn request
  "Fake HTTP request fn, depending on the input
  it will return a HTTP response map, and given count `n`
  it can simulate the request working after number of retries"
  [{:keys [resp n exc]
    :or {n 1}
    :as args}]
  (cond
    ;; simple cases
    (= resp :ok)
    {:status 200 :body {:msg "ok"}}

    (= resp :not-found)
    {:status 404 :body {:msg "gone"}}

    ;; simulate a request that always fails
    (= resp :srv-hard-err)
    {:status 501 :body {:msg "oh dear"}}

    ;; simulate failing request...
    (and (= resp :srv-err) (< n 2))
    {:status 500 :body {:msg "oh no"}}
    ;; and make it work on 3rd try
    (and (= resp :srv-err) (>= n 2))
    {:status 200 :body {:msg "finally working"}}

    ;; simulate http client throwing
    (= resp :exc)
    (throw exc)

    :else
    (throw (ex-info "unknown response type" args))))

(def retry-opts
  (retry/init {::retry/retry? (fn [n _duration-ms _exc]
                                (<= n 5))

               ::retry/success? (fn [{:keys [status] :as _res}]
                                  (not (>= status 500)))

               ::retry/delay (constantly 10)}))

(defn req-w-retry [{:keys [req track-fn]
                    :or {track-fn (constantly :noop)}}]
  (try
    #_{:clj-kondo/ignore [:unresolved-symbol]}
    (retry/with-retry [retry-count _duration-ms] retry-opts
      (track-fn retry-count)
      (request (assoc req :n retry-count)))

    (catch clojure.lang.ExceptionInfo err
      {:data (ex-data err)
       :cause (ex-cause err)})))
