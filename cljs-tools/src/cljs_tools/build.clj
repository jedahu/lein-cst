(ns cljs-tools.build
  (:require
    [cljs.closure :as cc]
    [clj-stacktrace.repl :as st]
    [conch.core :as conch]
    [cljs.repl.rhino :as rhino]
    [clojure.string :as string]
    [watcher :as w]
    [clojure.java.io :as io]))

(defn maybe-test
  [opts args]
  (when-let [cmd (and (some #{"test"} args)
                      (:test-cmd opts))]
    (if (string? cmd)
      (let [env (rhino/repl-env)
            path (:output-to opts)]
        (with-open [r (io/reader path)]
          (rhino/rhino-eval env path 1 r))
        (let [res (rhino/rhino-eval env nil 1 cmd)]
          (if (= :success (:status res))
            (int (Double/parseDouble (:value res)))
            (do
              (println "Unhandled JS exception:")
              (println (:value res))
              (println (:stacktrace res))
              1))))
      (do
        (println (str "Running `" (string/join " " cmd) "`."))
        (let [proc (apply conch/proc cmd)]
          (future (conch/stream-to-out proc :out))
          (future (conch/stream-to-out proc :err))
          (conch/exit-code proc))))))

(defn build-once
  [source-dir opts args]
  (try
    (println "Compiling...")
    (cc/build source-dir opts)
    (System/exit (or (maybe-test opts args) 0))
    (catch Throwable e
      (st/pst+ e)
      (System/exit 1))))

(defn build-loop
  [source-dirs opts args]
  (let [events? (atom true)]
    (future
      (w/with-watch-paths source-dirs
        (fn [ignored] (reset! events? true))
        :recursive))
    (while true
      (if @events?
        (do
          (println "Compiling...")
          (try
            (cc/build (first source-dirs) opts)
            (maybe-test opts args)
            (catch Throwable e
              (st/pst+ e)))
          (println "Watching...")
          (Thread/sleep 500)
          (reset! events? false))
        (Thread/sleep 100)))))

(defn build
  [source-dirs opts args]
  (println "Building..")
  (if (some #{"watch"} args)
    (build-loop source-dirs opts args)
    (build-once (first source-dirs) opts args)))
