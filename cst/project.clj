(defproject
  cst "0.3.1"
  :description "clojurescript tools"

  :dependencies
  [[org.clojure/clojure "1.4.0"]
   [org.clojure/clojurescript "0.0-1236"]
   [watcher "0.1.0"]
   [clj-stacktrace "0.2.4"]
   [fs "1.1.2"]
   [conch "0.2.0"]
   [ring/ring-core "1.1.0"]
   [ring/ring-jetty-adapter "1.1.0"]
   [ring/ring-devel "1.1.0"]]

  :exclusions
  [org.apache.ant/ant])
