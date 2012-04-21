(ns cst.server
  (:use
    cst.output
    [ring.adapter.jetty :only (run-jetty)]
    [ring.middleware.reload :only (wrap-reload)])
  (:import
    [java.io File]))

(defn test-body
  [{:keys  [output-dir output-to]}]
  (let [base (str output-dir "/goog/base.js")
        multiple? (.exists (File. base))]
    (str "<html><head><meta charset='UTF-8'/></head><body>"
         (when multiple? (str "<script src='/" base "'></script>"))
         "<script src='/" output-to "'></script>"
         (when multiple? "<script>goog.require('cst.build.test.ns');</script>")
         "</body></html>")))

(defn- serve-cljs-
  [{:keys [cst] :as proj-opts} & {:keys [test-uri handler body] :or {test-uri "/"}}]
  (vprintln 1 "    running jetty")
  (vprintln 1 (str "    test url: http://localhost:" (:http cst) test-uri))
  (run-jetty
    (->
      (fn [req]
        (cond
          (= test-uri (:uri req))
          {:status 200
           :headers {"Content-Type" "text/html"}
           :body (or body (test-body (:build cst)))}

          (.exists (File. (str "." (:uri req))))
          {:status 200
           :body (slurp (str "." (:uri req)))}

          handler
          (handler req)

          :else
          {:status 404}))
      (wrap-reload :dirs [(:source-path proj-opts) (:test-path proj-opts)])) 
    {:port (:http cst) :join? false}))

(defn serve-cljs*
  [project & opts]
  (binding [*verbosity* (or (-> project :cst :verbosity) 0)]
    (apply serve-cljs- project opts))) 

(def serve-cljs (memoize serve-cljs*))

(defn repl-body
  [repl-dir]
  (str "<html><head><meta charset='UTF-8'/></head>"
       "<body><script src='/" repl-dir "/goog/base.js'></script>"
       "<script src='/" repl-dir "/main.js'></script>"
       "<script>goog.require('cst.repl.browser.ns');</script></body></html>"))

(defn serve-brepl*
  [proj-opts & opts]
  (apply serve-cljs*
         proj-opts
         :body (repl-body (:repl-dir (:cst proj-opts)))
         opts))

(def serve-brepl (memoize serve-brepl*))
