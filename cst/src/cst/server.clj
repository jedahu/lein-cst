(ns cst.server
  (:use
    cst.output
    [ring.adapter.jetty :only (run-jetty)]
    [ring.middleware.reload :only (wrap-reload)]
    [ring.middleware.content-type :only (wrap-content-type)])
  (:import
    [java.io File]))

(defn test-body
  [{:keys  [output-dir output-to]} {:keys [head body]}]
  (let [base (str output-dir "/goog/base.js")
        multiple? (.exists (File. base))]
    (str "<html><head><meta charset='UTF-8'/>" head
         (when multiple? (str "<script src='/" base "'></script>"))
         "</head><body>" body
         "<script src='/" output-to "'></script>"
         (when multiple? "<script>goog.require('cst.build.test.ns');</script>")
         "</body></html>")))

(defn- serve-cljs-
  [{:keys [cst] :as proj-opts} &
   {:keys [test-uri handler html-fn] :or {test-uri "/"} :as opts}]
  (vprintln 1 "    running jetty")
  (vprintln 1 (str "    test url: http://localhost:" (:http cst) test-uri))
  (run-jetty
    (->
      (fn [req]
        (cond
          (= test-uri (:uri req))
          {:status 200
           :headers {"Content-Type" "text/html"}
           :body ((or html-fn test-body) (:build cst) opts)}

          (.exists (File. (str "." (:uri req))))
          {:status 200
           :body (slurp (str "." (:uri req)))}

          handler
          (handler req)

          :else
          {:status 404}))
      wrap-content-type
      (wrap-reload :dirs [(:source-path proj-opts)
                          (:test-path proj-opts)]))
    {:port (:http cst) :join? false}))

(defn serve-cljs*
  [project & opts]
  (binding [*verbosity* (or (-> project :cst :verbosity) 0)]
    (apply serve-cljs- project opts)))

(def serve-cljs (memoize serve-cljs*))

(defn repl-body
  [{:keys [repl-dir]} {:keys [head body]}]
  (str "<html><head><meta charset='UTF-8'/>" head
       "<script src='/" repl-dir "/goog/base.js'></script>"
       "<script src='/" repl-dir "/main.js'></script></head><body>"
       body
       "<script>goog.require('cst.repl.browser.ns');</script>"
       "</body></html>"))

(defn serve-brepl*
  [proj-opts & opts]
  (apply serve-cljs*
         proj-opts
         :body (repl-body (:cst proj-opts))
         opts))

(def serve-brepl (memoize serve-brepl*))
