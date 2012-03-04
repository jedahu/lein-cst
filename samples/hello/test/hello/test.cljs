(ns hello.test
  (:require
    [random :as r]))

(defn ^:export test-in-rhino []
  (binding [*print-fn* #(. java.lang.System/out print %)]
    (let [n (js/randomInt 0 3)]
      (println (str "Passed: " (- 3 n) ". Failed: " n "."))
      n)))
