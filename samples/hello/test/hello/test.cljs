(ns hello.test
  (:require
    [random :as r]))

(defn ^:export test-in-rhino []
  (binding [*print-fn* #(. java.lang.System/out print %)]
    (let [n (js/randomInt 0 2)]
      (println (str "Passed: " (- 2 n) ". Failed: " n "."))
      n)))
