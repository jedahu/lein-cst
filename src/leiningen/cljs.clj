(ns leiningen.cljs
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [leiningen.compile :as lc]
            fs)
  (:import java.util.Date))

(defn- clojurescript-file? [filename]
  (.endsWith (string/lower-case filename) ".cljs"))

(def getName (memfn getName))

(defn- build [project source-dirs opts args]
  (binding [lc/*skip-auto-compile* true]
    (lc/eval-in-project
      project
      `(cljs-tools.build/build ~source-dirs ~opts '~args)
      nil
      nil
      '(require 'cljs-tools.build))))

(defn- run-repl
  [project opts]
  (lc/eval-in-project
    project
    (if-let [b (:repl-browser opts)]
      `(cljs-tools.repl/start-browser-repl
         ~b
         ~(:repl-port opts)
         ~(.getAbsolutePath (io/file (:output-dir opts)))
         ~(.getAbsolutePath (io/file (:output-dir opts) "cljs-tools-repl.js")))
      '(cljs-tools.repl/start-rhino-repl))
    nil nil
    '(require 'cljs-tools.repl)))

(defn cljs
  "Tools for clojurescript projects. Default action is to compile.

Command line arguments:

watch  monitor sources and recompile when they change.
test   compile with test namespaces and run tests after each compile using
       rhino or an external command.
fresh  remove output files before doing anything else.
clean  remove output files and do nothing else (ignores other args).
repl   run a clojurescript repl using rhino or a browser (ignores watch and
       test args).

The first argument prefixed with a colon begins a clojure key-value option map
which is read using `read-string`. Its values override those from the :cljs
map in the project file.

Examples:

compiling
  lein cljs
  lein cljs watch
  lein cljs fresh :output-dir '\"myout\"' \\
                  :optimizations :advanced

testing
  lein cljs test :test-cmd '\"run.test.in.rhino.func()\"'
  lein cljs test :test-cmd [\"phantomjs\" \"test.js\"]

repl
  lein cljs repl # rhino repl
  lein cljs repl :browser '\"google-chrome\"' \\
                 :port 9876"
  [project & args]
  (let [outputfile (str (or (:name project) (:group project)) ".js")
        [args opts] (split-with #(not= \: (first %)) args)
        opts (apply merge
                    {:output-dir "out"
                     :output-to (or (:name project) (:group project))
                     :optimizations :whitespace
                     :pretty-print true
                     :src-dir "src"
                     :test-dir "test"
                     :repl-browser nil
                     :repl-port 9000}
                    (:cljs project)
                    (read-string (str "{" (string/join " " opts) "}")))
        src-dir (:src-dir opts)
        test-dir (:test-dir opts)
        source-dirs (if (some #{"test"} args)
                      [test-dir src-dir]
                      [src-dir])
        starttime (.getTime (Date.))]
    (when (some #{"clean" "fresh"} args)
      (println (str "Removing '" (:output-dir opts)
                  "' and '" (:output-to opts) "' ..."))
      (fs/delete (:output-to opts))
      (fs/deltree (:output-dir opts)))
    (when-not (some #{"clean"} args)
      (cond
        (some #{"repl"} args) (run-repl project opts)

        :else
        (if (seq (filter (comp clojurescript-file? getName)
                         (apply concat (map #(file-seq (io/file %)) source-dirs))))
          (let [ret (build project source-dirs opts args)]
            (when-let [[x y] (and (:optimizations opts)
                                  (:wrap-output opts))]
              (spit (:output-to opts)
                    (str x (slurp (:output-to opts)) y)))
            ret)
          (do
            (println "No cljs files found.")
            1))))))
