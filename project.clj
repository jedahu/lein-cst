(defproject
  lein-cst "0.3.1"
  :description "leiningen clojurescript tools"

  :dependencies
  [[org.clojure/clojure "1.4.0"]
   [fs "1.1.2"]]

  :plugins
  [[lein-sub "0.2.0"]]

  :exclusions
  [org.apache.ant/ant]

  :eval-in-leiningen true

  :sub ["cst"]

  :min-lein-version "2.0.0")
