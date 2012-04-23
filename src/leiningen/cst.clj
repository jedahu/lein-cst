(ns leiningen.cst
  (:require
    [clojure.string :as string]
    [clojure.java.io :as io]
    [leiningen.compile :as lc]
    [fs.core :as fs]
    [clojure.pprint :as pp])
  (:import
    [java.util Date]
    [java.io File]))

(defn add-cst-dep
  [project]
  (if (some #(= 'cst (first %)) (:dependencies project))
    project
    (update-in project [:dependencies] conj ['cst "0.2.2-SNAPSHOT"])))

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

(defn- build-src [project test? watch?]
  (binding [lc/*skip-auto-compile* true]
    (lc/eval-in-project
      (-> project add-cst-dep (cp-add-test-dir test?))
      `(cst.build/build-cljs '~project ~test? ~watch?)
      nil
      nil
      (list* 'require ''cst.build
            (when-let [p (and test? (-> project :cst :runner :proc))]
              (when (symbol? p)
                [`(quote ~(symbol (namespace p)))]))))))

(defn- run-sbrepl
  [project]
  (lc/eval-in-project
    (-> project add-cst-dep (cp-add-test-dir true))
    `(cst.sbrepl/start-sbrepl '~project)
    nil nil
    `(require 'cst.sbrepl (quote ~(symbol (namespace (-> project :cst :server)))))))

(defn- run-brepl
  [project]
  (lc/eval-in-project
    (cp-add-test-dir project true)
    `(cljs.repl/repl
       (cljs.repl.browser/repl-env
         :port ~(:port (:cst project))
         :working-dir ~(:repl-dir (:cst project))))
    nil nil
    '(require 'cljs.repl 'cljs.repl.browser)))

(defn- run-repl
  [project]
  (lc/eval-in-project
    (cp-add-test-dir project true)
    `(cljs.repl/repl (assoc (cljs.repl.rhino/repl-env)
                            :working-dir ~(-> project :cst :repl-dir)))
    nil nil
    '(require 'cljs.repl 'cljs.repl.rhino)))

(def default-opts
  '{:src-dir "src"
    :test-dir "test"
    :build-defaults {:externs []
                     :libs [] 
                     :foreign-libs []}
    :builds
    {:dev {:output-dir ".cst-out/dev"
           :optimizations nil
           :pretty-print true}
     :single {:output-dir ".cst-out/single"
              :optimizations :whitespace
              :pretty-print true}
     :small {:output-dir ".cst-out/small"
             :optimizations :simple
             :pretty-print true}
     :tiny {:output-dir ".cst-out/tiny"
            :optimizations :advanced
            :pretty-print false}
     :deploy {:output-dir ".cst-out/deploy"
              :output-to "main.js"
              :optimizations :advanced
              :pretty-print false}}
    :build :dev
    :suites []
    :opts nil
    :runners
    {:console-rhino {:cljs menodora.runner.console/run-suites-rhino
                     :proc :rhino
                     :build :single}
     :console-v8 {:cljs menodora.runner.console/run-suites-v8
                  :proc ["d8"]
                  :build :single}
     :console-browser {:cljs menodora.runner.console/run-suites-browser
                       :proc cst.server/serve-cljs}
     :console-phantom {:cljs menodora.runner.console/run-suites-browser
                       :proc cst.server/serve-cljs
                       :browser :phantom}}
    :runner :console-rhino
    :servers
    {:default cst.server/serve-brepl}
    :server :default
    :repl-dir ".cst-repl"
    :port 9000
    :http 8000})

(defn merge-cst-maps
  [& maps]
  (assoc (apply merge maps)
         :builds (apply merge (map :builds maps))
         :runners (apply merge (map :runners maps))
         :servers (apply merge (map :servers maps))))

(defn build-conf
  [{:keys [src-dir test-dir builds build-defaults]} build-kw test?]
  (let [build (merge build-defaults (get builds build-kw))]
    (assoc build
           :output-to (if test?
                        (str (File. (:output-dir build) "test.js"))
                        (or (:output-to build)
                            (str (.getParent
                                   (File. (:output-dir build) "main.js"))))))))

(defn runner-conf
  [cst-opts runner-kw]
  (get (:runners cst-opts) runner-kw))

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
    :http   http port (if using cst.server/serve-cljs)

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
  (let [[args opts-str] (split-with #(not= \: (first %)) args)
        arg-set (set args)
        cmd-opts (assoc
                   (read-string (str "{" (string/join " " opts-str) "}"))
                   :verbosity (cond
                                (arg-set "-vv") 2
                                (some arg-set #{"-v" "watch"}) 1
                                :else 0))
        proj-opts (merge-cst-maps default-opts (:cst project) cmd-opts)
        test? (arg-set "test")
        watch? (arg-set "watch")
        vprintln #(when (<= %1 (:verbosity proj-opts))
                    (apply println %&))
        runner-kw (:runner proj-opts)
        runner (cond
                 (not (keyword? runner-kw)) runner-kw
                 test? (runner-conf proj-opts runner-kw))
        build-kw (or (:build cmd-opts)
                     (:build runner)
                     (:build proj-opts))
        build (if (keyword? build-kw)
                (build-conf proj-opts build-kw test?)
                build-kw)
        server-kw (:server proj-opts)
        server (if (keyword? server-kw)
                 (get (:servers proj-opts) server-kw)
                 server-kw)
        opts (dissoc
               (assoc proj-opts
                      :build build
                      :runner runner
                      :server server)
               :build-defaults :builds :runners :servers)
        project* (assoc project :cst opts)
        starttime (.getTime (Date.))]
    (vprintln 1 "Using")
    (vprintln 1 "    :build " build-kw)
    (vprintln 1 "    :runner" runner-kw)
    (vprintln 1 "    :server" server-kw)
    (vprintln 1)
    (vprintln 2 "Config")
    (vprintln
      2
      (string/replace
        (with-out-str (pp/pprint opts))
        #"^|\n"
        "$0    "))
    (when (some #{"clean" "fresh"} args)
      (println (str "Removing '" (:output-dir build)
                    "' and '" (:output-to build) "'.."))
      (println)
      (fs/delete (:output-to build))
      (fs/delete-dir (:output-dir build)))
    (when-not (arg-set "clean")
      (when (some arg-set #{"repl" "brepl" "sbrepl"})
        (fs/delete-dir (:repl-dir opts)))
      (cond
        (arg-set "repl")
        (run-repl project*)

        (arg-set "brepl")
        (run-brepl project*)

        (arg-set "sbrepl")
        (run-sbrepl project*)

        :else
        (let [ret (build-src project* test? watch?)]
          (when-let [[x y] (and (:optimizations opts)
                                (:wrap-output opts))]
            (spit (:output-to opts)
                  (str x (slurp (:output-to opts)) y)))
          ret)))))
