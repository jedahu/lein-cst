(ns cst.server 
  (:require
    [ring.adapter.jetty :as jetty])
  (:import
    [java.io File]))

(defn test-body
  [{:keys  [output-dir output-to]}]
  (str "<html><head><meta charset='UTF-8'/></head></body>"
       (when (.exists (File. (str output-dir "/goog/base.js")))
         "<script src='"
         (str output-dir "/goog/base.js")
         "'></script>")
       "<script src='/" output-to "'></script>"))

(defn serve-cljs*
  [proj-opts & {:keys [test-uri handler body] :or {test-uri "/"}}]
  (println "    running jetty")
  (println (str "    test url: http://localhost:" (:http proj-opts) test-uri))
  (jetty/run-jetty
    (fn [req]
      (cond
        (= test-uri (:uri req))
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (or body (test-body (:build proj-opts)))}

        (.exists (File. (str "." (:uri req))))
        {:status 200
         :body (slurp (str "." (:uri req)))}

        handler
        (handler req)
        
        :else
        {:status 404}))
    {:port (:http proj-opts) :join? false}))

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
         :body (repl-body (:repl-dir proj-opts))
         opts))

(def serve-brepl (memoize serve-brepl*))
