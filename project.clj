(defproject
  lein-cljs-tools "0.1.0-SNAPSHOT"
  :description "leiningen plugin for clojurescript"

  :dependencies
  [[org.clojure/clojure "1.3.0"]
   [org.clojure/clojurescript "0.0-971"]
   [cljs-tools "0.1.0-SNAPSHOT"]
   [watcher "0.1.0"]
   [clj-stacktrace "0.2.4"]
   [fs "0.11.0"]
   [conch "0.2.0"]]

  :exclusions
  [org.apache.ant/ant]

  :eval-in-leiningen true)
