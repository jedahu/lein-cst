(defproject
  cst "0.1.0-SNAPSHOT"
  :description "clojurescript tools"

  :dependencies
  [[org.clojure/clojure "1.3.0"]
   [org.clojure/clojurescript "0.0-1011"]
   [watcher "0.1.0"]
   [clj-stacktrace "0.2.4"]
   [fs "1.1.2"]
   [conch "0.2.0"]
   [ring/ring-jetty-adapter "1.1.0-RC1"]]
  
  :exclusions
  [org.apache.ant/ant])
