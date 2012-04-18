(ns cst.repl
  (:require
    [cljs.repl :as repl]
    [cljs.repl.browser :as browser]
    [cljs.closure :as cc]
    [clojure.java.io :as io]
    [ring.adapter.jetty :as jetty]))

(defn serve-html
  [out-dir out-file]
  (jetty/run-jetty
    (fn [req]
      (if (= "/" (:uri req))
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body
         (str "<html><head><meta charset='UTF-8'/></head>"
              "<body><script src='/out/goog/base.js'></script>"
              "<script src='/" out-file "'></script>"
              "<script>goog.require('cst.browser.repl');</script></body></html>")}
        {:status 200
         :body (slurp (str "." (:uri req)))}))
    {:port 8080 :join? false}))

(defn start-repl
  [port out-dir]
  (repl/repl
    (browser/repl-env :port port
                      :working-dir out-dir)))

(defn build-browser-js
  [port out-dir out-file]
  (cc/build
    `[(~'ns cst.browser.repl
          (:use [clojure.browser.repl :only (~'connect)]))
      (~'connect (str "http://localhost:" ~port "/repl"))]
    {:optimizations nil
     :pretty-print true
     :output-dir out-dir
     :output-to out-file}))

(defn start-browser
  [browser tmp-html]
  (.exec
    (Runtime/getRuntime)
    (into-array
      [browser (.getAbsolutePath tmp-html)])))

(defn start-browser-repl
  [port out-dir out-file]
  (build-browser-js port out-dir out-file)
  (let [jet (serve-html out-dir out-file)]
    (start-repl port out-dir)
    (.stop jet)))
