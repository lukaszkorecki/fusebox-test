(ns ft.core
  (:require [com.potetm.fusebox.retry :as retry]))

(defn request
  "Fake HTTP request fn, depending on the input
  it will return a HTTP response map, and given count `n`
  it can simulate the request working after number of retries"
  [{:keys [resp n exc-class]
    :or {n 1}
    :as args}]
  #_(tap> {:request resp :n n})
  (cond
    (= resp :ok)
    {:status 200 :body {:msg "ok"}}

    (= resp :not-found)
    {:status 404 :body {:msg "gone"}}

    ;; simulate failing request...
    (and (= resp :srv-errr) (< n 2))
    {:status 500 :body {:msg "oh no"}}
    ;; and make it work on 3rd try
    (and (= resp :srv-errr) (= n 2))
    {:status 200 :body {:msg "finally working"}}

    (= resp :exc)
    (throw exc-class)

    :else
    (throw (ex-info "unknown response type" args))))

(def retry-opts
  (retry/init {::retry/retry? (fn [n _duration-ms _exc]
                                #_(tap> {:n n})
                                (<= n 5))

               ;; this is not called at all
               ::retry/success? (fn [{:keys [status] :as res}]
                                  (tap> {::res res}) ;; why is this nil?
                                  (not (= status 500)))

               ::retry/delay (constantly 10)}))

(defn req-w-retry [{:keys [req track-fn]
                    :or {track-fn (constantly :noop)}}]
  (try
    #_{:clj-kondo/ignore [:unresolved-symbol]}
    (retry/with-retry [retry-count _duration-ms] retry-opts
      (track-fn retry-count)
      #_(tap> {:rc retry-count :dur _duration-ms})
      (request (assoc req :n retry-count)))

    (catch clojure.lang.ExceptionInfo err
      (ex-data err))))
