(defproject
  hello "0.1.0-SNAPSHOT"
  :description "helloworld clojurescript"
  :plugins [[lein-cst "0.2.1"]]
  ; :dependencies [[org.clojure/clojure "1.3.0"]]
  ; :dev-dependencies [[cljs-tools "0.1.0-SNAPSHOT"]]
  :extra-classpath-dirs ["src"]
  :cst
  {:output-to "resources/public/js/hello.js"
   :output-dir "resources/public/js/out"
   :optimizations :advanced

   ;; `java.lang.System.out/print` is bound to `*print-fn*` in
   ;; `test/hello/test.cljs`.
   :externs ["externs.js"]

   ;; The `upcase` function from `text_util.js` is used in `src/hello.cljs`.
   :libs ["js-libs/text_util.js"]

   ;; The `randomInt` function from `random.js` is used in
   ;; `test/hello/test.cljs`.
   :foreign-libs [{:file "js-foreign/random.js"
                   :provides ["random"]}]

   ;; Note that in test/hello/test.cljs this function is exported, so it is
   ;; still callable with advanced optimizations.
   :test-cmd "hello.test.test_in_rhino()"})
