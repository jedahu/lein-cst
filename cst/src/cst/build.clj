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
    [clojure.java.io :as io]
    [fs.core :as fs]))

(defn run-tests-
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
              1))))))
  (vprintln 2))

(defn run-tests
  [{{:keys [runner] :as cst} :cst :as project}]
  (run-tests- project)
  (when-let [browser (:browser runner)]
    (cond
      (= :phantom browser)
      (do
        (spit ".cst-phantom-test.js" (slurp (io/resource "phantom-test.js")))
        (let [p (conch/proc "phantomjs" ".cst-phantom-test.js" (str "http://localhost:" (:http cst) "/"))]
          (future (conch/stream-to-out p :out))
          (future (conch/stream-to-out p :err))
          (conch/exit-code p))))))

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
  [{:keys [build runner] :as cst} fresh?]
  (when fresh?
    (vprintln 1 "Cleaning..")
    (vprintln 2 (str "removing '" (:output-dir build)
                     "' and '" (:output-to build) "'"))
    (vprintln 2)
    (fs/delete (:output-to build))
    (fs/delete (:output-dir build)))
  (vprintln 1 "Compiling..")
  (vprintln 2 "    :output-dir" (:output-dir build))
  (vprintln 2 "    :output-to " (:output-to build))
  (vprintln 2)
  (when runner
    (write-test-file cst))
  (if runner
    (cc/build ".cst-test.cljs" build)
    (cc/build (:src-dir cst) build)))

(defn build-once
  [{:keys [cst] :as project} fresh?]
  (try
    (run-build cst fresh?)
    (System/exit (if (:runner cst) (run-tests project) 0))
    (catch Throwable e
      (st/pst+ e)
      (System/exit 1))))

(defn build-loop
  [{{:keys [build runner] :as cst} :cst :as project} fresh?]
  (let [events? (atom true)
        dirs [(:src-dir cst) (:test-dir cst)]]
    (future
      (w/with-watch-paths dirs
        (fn [ignored] (reset! events? true))
        :recursive))
    (while true
      (if @events?
        (do
          (try
            (run-build cst fresh?)
            (when runner (run-tests project))
            (catch Throwable e
              (st/pst+ e)))
          (vprintln 1 "Watching..")
          (Thread/sleep 500)
          (reset! events? false))
        (Thread/sleep 100)))))

(defn build-cljs
  [project test? watch? fresh?]
  (binding [*verbosity* (-> project :cst :verbosity)]
    (if watch?
      (build-loop project fresh?)
      (build-once project fresh?))))
