(ns cst.server
  (:use
    cst.output
    [ring.adapter.jetty :only (run-jetty)]
    [ring.middleware.reload :only (wrap-reload)]
    [ring.middleware.content-type :only (wrap-content-type)])
  (:import
    [java.io File]))

(defn test-body
  [project {:keys [head body]}]
  (let [base (str (-> project :cst :build :output-dir)
                  "/goog/base.js")
        multiple? (.exists (File. base))]
    (str "<html><head><meta charset='UTF-8'/>" head
         (when multiple? (str "<script src='/" base "'></script>"))
         "</head><body>" body
         "<script src='/"
         (-> project :cst :build :output-to)
         "'></script>"
         (when multiple? "<script>goog.require('cst.build.test.ns');</script>")
         "</body></html>")))

(defn cljs-handler
  [project & [{:keys [uri-regex handler html-fn]
               :or {uri-regex #"^/$"}
               :as opts}]]
  (vprintln 1 (str "    test url: http://localhost:"
                   (-> project :cst :http)
                   uri-regex))
  (->
    (fn [req]
      (cond
        (re-seq uri-regex (:uri req))
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body ((or html-fn test-body)
                  project
                  opts)}

        (.exists (File. (str "." (:uri req))))
        {:status 200
         :body (slurp (str "." (:uri req)))}

        handler
        (handler req)

        :else
        {:status 404}))
    wrap-content-type
    (wrap-reload :dirs [(:source-path project)
                        (:test-path project)])))

(defn- serve-cljs-
  [project & [opts]]
  (vprintln 1 "    running jetty")
  (run-jetty (cljs-handler project opts)
             {:port (-> project :cst :http)
              :join? false}))

(defn serve-cljs*
  [project & [opts]]
  (binding [*verbosity* (or (-> project :cst :verbosity) 0)]
    (serve-cljs- project opts)))

(def serve-cljs (memoize serve-cljs*))

(defn repl-body
  [project & [{:keys [head body]}]]
  (let [repl-dir (-> project :cst :repl-dir)]
    (str "<html><head><meta charset='UTF-8'/>" head
         "<script src='/" repl-dir "/goog/base.js'></script>"
         "<script src='/" repl-dir "/main.js'></script></head><body>"
         body
         "<script>goog.require('cst.repl.browser.ns');</script>"
         "</body></html>")))

(defn serve-brepl*
  [project & [opts]]
  (serve-cljs* project
               (assoc opts
                      :html-fn repl-body)))

(def serve-brepl (memoize serve-brepl*))
