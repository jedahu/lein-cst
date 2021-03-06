(ns cst.sbrepl
  (:require
    [cljs.repl :as repl]
    [cljs.repl.browser :as browser]
    [cljs.closure :as cc]))

(defn start-brepl
  [{:keys [repl-dir port]}]
  (repl/repl
    (browser/repl-env :port port
                      :working-dir repl-dir)))

(defn build-browser-js
  [{:keys [repl-dir port]}]
  (cc/build
    `[(~'ns cst.repl.browser.ns
          (:use [clojure.browser.repl :only (~'connect)]))
      (~'connect (str "http://localhost:" ~port "/repl"))]
    {:optimizations nil
     :pretty-print false 
     :output-dir repl-dir
     :output-to (str repl-dir "/main.js")}))

(defn start-browser
  [browser tmp-html]
  (.exec
    (Runtime/getRuntime)
    (into-array
      [browser (.getAbsolutePath tmp-html)])))

(defn start-sbrepl
  [{:keys [cst] :as project}]
  (build-browser-js cst)
  (future ((resolve (:server cst)) project))
  (start-brepl cst)
  (System/exit 0))
