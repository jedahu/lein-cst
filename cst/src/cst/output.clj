(ns cst.output)

(def ^:dynamic *verbosity* 0)

(defn vprintln
  [n & args]
  (when (<= n *verbosity*)
    (apply println args)))
