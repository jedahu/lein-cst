(ns cljs-tools.repl
  (:require
    [cljs.repl :as repl]
    [cljs.repl.browser :as browser]
    [cljs.repl.rhino :as rhino]
    [cljs.closure :as cc]
    [clojure.java.io :as io]))

(defn start-repl
  [port out-dir]
  (repl/repl
    (browser/repl-env :port port
                      :working-dir out-dir)))

(defn create-html-file
  [out-dir out-file]
  (let [tmp-html (io/file "repljs.html")]
    (spit
      tmp-html
      (str "<?xml version='1.0' encoding='UTF-8'?>"
           "<html><head><meta charset='UTF-8'/></head>"
           "<body><script src='" out-dir "/goog/base.js'></script>"
           "<script src='" out-file "'></script>"
           "<script>goog.require('leiningen.repljs.browser');</script></body></html>"
           ))
    tmp-html))

(defn build-browser-js
  [port out-dir out-file]
  (cc/build
    `[(~'ns leiningen.cljs-tools.browser-repl
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
  [browser port out-dir out-file]
  (build-browser-js port out-dir out-file)
  (future
    (Thread/sleep 1000)
    (start-browser browser (create-html-file out-dir out-file)))
  (start-repl port out-dir))

(defn start-rhino-repl
  []
  (repl/repl (rhino/repl-env)))
