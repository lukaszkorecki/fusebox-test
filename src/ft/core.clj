(ns ft.core
  (:require [com.potetm.fusebox.retry :as retry]))

(defn request
  "Fake HTTP request fn, depending on the input
  it will return a HTTP response map, and given count `n`
  it can simulate the request working after number of retries"
  [{:keys [resp n exc-class]
    :or {n 1}}]
  (printf "r=%s n=%s\n" resp n)
  (cond
    (= resp :ok) {:status 200 :body {:msg "ok"}}
    (= resp :not-found) {:status 404 :body {:msg "gone"}}
    (and (= resp :srv-errr) (< n 3)) {:status 500 :body {:msg "oh no"}}
    (and (= resp :srv-errr) (= n 3)) {:status 200 :body {:msg "finally working"}}
    (= resp :exc) (throw exc-class)))

(def retry-opts
  (retry/init {::retry/retry? (fn [n _duration-ms _exc]
                                (< n 4))
               ::retry/success? (fn [{:keys [status]}]
                                  (not (= status 500)))
               ::retry/delay (fn [n _ms _ex]
                               (retry/delay-linear 100 n))}))

(defn req-w-retry [{:keys [req track-fn]
                    :or {track-fn (constantly :noop)}}]
  (try
    (retry/with-retry [retry-count _duration-ms] retry-opts
      (track-fn retry-count)
      (printf "rc=%s dms=%s\n" retry-count _duration-ms)
      (request (assoc req :n retry-count)))

    (catch clojure.lang.ExceptionInfo err
      (ex-data err))))
