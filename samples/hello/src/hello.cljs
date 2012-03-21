(ns hello
  (:use
    [text.util :only (upcase)]))

(defn ^{:export greet} greet [n]
  (upcase (str "hello " n)))
