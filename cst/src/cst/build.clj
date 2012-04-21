(ns cst.build
  (:use
    cst.output)
  (:require
    [cljs.closure :as cc]
    [clj-stacktrace.repl :as st]
    [conch.core :as conch]
    [cljs.repl.rhino :as rhino]
    [clojure.string :as string]
    [watcher :as w]
    [clojure.java.io :as io]))

(defn run-tests
  [{{:keys [build runner] :as cst} :cst :as project}]
  (vprintln 1 "Testing..")
  (let [proc (:proc runner)]
    (cond
      (vector? proc)
      (let [cmd (conj proc (:output-to build))]
        (vprintln 2 (str "    `" (string/join " " cmd) "`"))
        (let [p (apply conch/proc cmd)]
          (future (conch/stream-to-out p :out))
          (future (conch/stream-to-out p :err))
          (conch/exit-code p)))

      (symbol? proc)
      (do
        (vprintln 2 (str "    " proc))
        (let [res ((resolve proc) project)]
          (if (integer? res) res 1))) 

      (= :rhino proc)
      (let [env (rhino/repl-env)
            path (:output-to build)]
        (vprintln 2 "    rhino")
        (. (:cx env) setOptimizationLevel -1)
        ;; FIX so rhino can test more than single-files
        ;; (when-not (:optimizations build)
        ;;   (let [base (str (fs/file (:output-dir build) "goog" "base.js"))]
        ;;     (with-open [r (io/reader base)]
        ;;       (rhino/rhino-eval env base 1 r)))
        ;;   (let [deps (str (fs/file (:output-dir build) "goog" "deps.js"))]
        ;;     (with-open [r (io/reader deps)]
        ;;       (rhino/rhino-eval env deps 1 r))))
        (let [res (with-open [r (io/reader path)]
                    (rhino/rhino-eval env path 1 r))]
          (if (= :success (:status res))
            (int (Double/parseDouble (:value res)))
            (do
              (println "Unhandled JS exception:")
              (println (:value res))
              (println (:stacktrace res))
              1)))))))

(defn write-test-file
  [{:keys [build runner] :as cst}]
  (let [cljs-fn (:cljs runner)
        suites (:suites cst)
        runner-opts (:opts cst)]
    (spit
      ".cst-test.cljs"
      (pr-str
        (list 'ns 'cst.build.test.ns
              (list* :require
                     (for [sym (conj suites cljs-fn)
                           :let [ns-sym (symbol (namespace sym))]]
                       [ns-sym :as (gensym)])))
        (list* cljs-fn suites (apply concat runner-opts))))))


(defn run-build
  [{:keys [build runner] :as cst}]
  (when runner
    (write-test-file cst))
  (if runner
    (cc/build ".cst-test.cljs" build)
    (cc/build (:src-dir cst) build)))

(defn build-once
  [{:keys [cst] :as project}]
  (try
    (run-build cst)
    (System/exit (if (:runner cst) (run-tests project) 0))
    (catch Throwable e
      (st/pst+ e)
      (System/exit 1))))

(defn build-loop
  [{{:keys [build runner] :as cst} :cst :as project}]
  (let [events? (atom true)
        dirs [(:src-dir cst) (:test-dir cst)]]
    (future
      (w/with-watch-paths dirs
        (fn [ignored] (reset! events? true))
        :recursive))
    (while true
      (if @events?
        (do
          (vprintln 1 "Compiling..")
          (try
            (run-build cst)
            (when runner (run-tests project))
            (catch Throwable e
              (st/pst+ e)))
          (vprintln 1 "Watching..")
          (Thread/sleep 500)
          (reset! events? false))
        (Thread/sleep 100)))))

(defn build-cljs
  [project test? watch?]
  (binding [*verbosity* (-> project :cst :verbosity)]
    (vprintln 1 "Compiling..")
    (vprintln 1 "    :output-dir" (-> project :cst :build :output-dir))
    (vprintln 1 "    :output-to " (-> project :cst :build :output-to))
    (vprintln 1) 
    (if watch?
      (build-loop project)
      (build-once project))))
