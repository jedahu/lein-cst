(ns leiningen.cst
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [leiningen.compile :as lc]
            [fs.core :as fs])
  (:import
    [java.util Date]
    [java.io File]))

(defn add-cst-dep
  [project]
  (if (some #(= 'cst (first %)) (:dependencies project))
    project
    (update-in project [:dependencies] conj ['cst "0.1.0-SNAPSHOT"])))

(defn cp-add-test-dir
  [project test?]
  (if test?
    (update-in project
               [:extra-classpath-dirs]
               conj
               (-> project :cst :test-dir))
    project))

(defn- clojurescript-file? [filename]
  (.endsWith (string/lower-case filename) ".cljs"))

(def getName (memfn getName))

(defn- build [project opts test? watch?]
  (binding [lc/*skip-auto-compile* true]
    (lc/eval-in-project
      (-> project add-cst-dep (cp-add-test-dir test?))
      `(cst.build/build-cljs '~opts ~test? ~watch?)
      nil
      nil
      (list* 'require ''cst.build
            (when-let [p (and test? (:proc (:runner opts)))]
              (when (symbol? p)
                [`(quote ~(symbol (namespace p)))]))))))

(defn- run-sbrepl
  [project opts]
  (lc/eval-in-project
    (-> project add-cst-dep (cp-add-test-dir true))
    `(cst.repl/start-browser-repl
       ~(:port opts)
       ~(:output-dir opts)
       ~(str (:output-dir opts) "/cst-repl.js"))
    nil nil
    '(require 'cst.repl)))

(defn- run-brepl
  [project opts]
  (lc/eval-in-project
    (cp-add-test-dir project true)
    `(cljs.repl/repl
       (cljs.repl.browser/repl-env
         :port ~(:port opts)
         :working-dir ~(:output-dir opts)))
    nil nil
    '(require 'cljs.repl 'cljs.repl.browser)))

(defn- run-repl
  [project opts]
  (lc/eval-in-project
    (cp-add-test-dir project true)
    '(cljs.repl/repl (cljs.repl.rhino/repl-env))
    nil nil
    '(require 'cljs.repl 'cljs.repl.rhino)))

(def default-opts
  '{:src-dir "cljs"
    :test-dir "test"
    :builds
    {:default {:output-to ".cst-out/default/main.js"
               :output-dir ".cst-out/default"
               :optimizations nil
               :pretty-print true
               :src-dir nil}}
    :build :default
    :suites []
    :runners
    {:default {:cljs menodora.runner.console/run-suites-rhino
               :proc :rhino}}
    :runner :default
    :servers
    {:default menodora.server/serve-cljs}
    :server :default
    :port 9000
    :http 8000})

(defn build-opts
  [opts test?]
  (let [bopts ((:build opts) (:builds opts))
        src-dir (or (:src-dir bopts) (:src-dir opts))
        test-dir (:test-dir opts)
        main-dir (if test? test-dir src-dir)
        bopts1 (assoc bopts
                      :src-dir src-dir
                      :test-dir test-dir
                      :main-dir main-dir)]
    (if-let [path (:output-to bopts1)]
      (if test?
        (assoc bopts1
               :output-to (str (.getParent (File. path))
                               "/test.js"))
        bopts1)
      (assoc bopts1
             :output-to (str
                          (File.
                            (:output-dir bopts1)
                            (if test? "/test.js" "/main.js")))))))

(defn test-opts
  [opts]
  ((:runner opts) (:runners opts)))

(defn cst
  "Tools for clojurescript projects. Default action is to compile.

Command line arguments:

watch  monitor sources and recompile when they change.
test   compile with test namespaces and run tests after each compile using
       rhino or an external command.
fresh  remove output files before doing anything else.
clean  remove output files and do nothing else (ignores other args).
repl   run a clojurescript repl using rhino
brepl  run a clojurescript repl listener
sbrepl run a clojurescript repl listener and a http server

The first argument prefixed with a colon begins a clojure key-value option map
which is read using `read-string`. Its values override those from the :cst
map in the project file.

Examples:

compiling
  lein cst

  arguments
    :build the key to a build description

  lein cst watch
  lein cst fresh :build :dev

testing
  lein cst test

  arguments
    :runner the key to a test runner description
    :suites a vector of test suites
    :opts   a map of options for the clojurescript runner function

  lein cst fresh test
  lein cst test :suites '[cst.tests/suite1 cst.tests/suite2]'
  lein cst watch test :runner :browser-console
  lein cst test :build :deploy

rhino repl
  lein trampoline cst repl

browser repl
  lein trampoline cst brepl

  arguments
    :port repl port (default 9000)

  lein trampoline cst brepl :port 9876
  lein trampoline cst brepl :build :dev

browser repl and server
  lein trampoline cst sbrepl

  arguments
    :port   repl port (default 9000)
    :http   http port (default 8000)
    :server the key to a server description

  lein trampoline cst sbrepl :http 8080 :server :test"
  [project & args]
  (let [outputfile (str (or (:name project) (:group project)) ".js")
        [args opts] (split-with #(not= \: (first %)) args)
        opts- (apply merge
                     default-opts
                     (:cst project)
                     (read-string (str "{" (string/join " " opts) "}")))
        arg-set (set args)
        test? (arg-set "test")
        watch? (arg-set "watch")
        bopts (build-opts opts- test?)
        topts (test-opts opts-)
        opts (assoc opts- :build bopts :runner topts)
        starttime (.getTime (Date.))]
    (when (some #{"clean" "fresh"} args)
      (println (str "Removing '" (:output-dir bopts)
                  "' and '" (:output-to bopts) "'.."))
      (fs/delete (:output-to bopts))
      (fs/delete-dir (:output-dir bopts)))
    (when-not (some #{"clean"} args)
      (cond
        (some #{"repl"} args)
        (run-repl project opts)

        (some #{"brepl"} args)
        (run-brepl project opts)

        (some #{"sbrepl"} args)
        (run-sbrepl project opts)

        :else
        (let [ret (build project opts test? watch?)]
          (when-let [[x y] (and (:optimizations opts)
                                (:wrap-output opts))]
            (spit (:output-to opts)
                  (str x (slurp (:output-to opts)) y)))
          ret)))))
