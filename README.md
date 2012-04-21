# lein-cst

Tools for clojurescript projects. Diverged from lein-clojurescript.

Command line arguments:

~~~~
watch  monitor sources and recompile when they change.
test   compile with test namespaces and run tests after each compile using
       rhino or an external command.
fresh  remove output files before doing anything else.
clean  remove output files and do nothing else (ignores other args).
repl   run a clojurescript repl using rhino
brepl  run a clojurescript repl listener
sbrepl run a clojurescript repl listener and a http server
~~~~

The first argument prefixed with a colon begins a clojure key-value option map
which is read using `read-string`. Its values override those from the :cst
map in the project file.

Examples:

~~~~
compiling
  lein cst

  arguments
    :build the key to a build description

  lein cst watch
  lein cst fresh :build :dev

testing
  lein cst test

  arguments
    :runner the key to a test runner description
    :suites a vector of test suites
    :opts   a map of options for the clojurescript runner function
    :http   http port (if using cst.server/serve-cljs)

  lein cst fresh test
  lein cst test :suites '[cst.tests/suite1 cst.tests/suite2]'
  lein cst watch test :runner :browser-console
  lein cst test :build :deploy

rhino repl
  lein trampoline cst repl

browser repl
  lein trampoline cst brepl

  arguments
    :port repl port (default 9000)

  lein trampoline cst brepl :port 9876
  lein trampoline cst brepl :build :dev

browser repl and server
  lein trampoline cst sbrepl

  arguments
    :port   repl port (default 9000)
    :http   http port (default 8000)
    :server the key to a server description

  lein trampoline cst sbrepl :http 8080 :server :test
~~~~

The default project :cst value...

~~~~
{:src-dir "src"
 :test-dir "test"
 :build-defaults {:externs []
                  :libs [] 
                  :foreign-libs []}
 :builds
 {:dev {:output-dir ".cst-out/dev"
        :optimizations nil
        :pretty-print true}
  :single {:output-dir ".cst-out/single"
           :optimizations :whitespace
           :pretty-print true}
  :small {:output-dir ".cst-out/small"
          :optimizations :simple
          :pretty-print true}
  :tiny {:output-dir ".cst-out/tiny"
         :optimizations :advanced
         :pretty-print false}
  :deploy {:output-dir ".cst-out/deploy"
           :output-to "main.js"
           :optimizations :advanced
           :pretty-print false}}
 :build :dev
 :suites []
 :opts nil
 :runners
 {:console-rhino {:cljs menodora.runner.console/run-suites-rhino
                  :proc :rhino}
  :console-v8 {:cljs menodora.runner.console/run-suites-v8
               :proc ["d8"]
               :build :single}
  :console-browser {:cljs menodora.runner.console/run-suites-browser
                    :proc cst.server/serve-cljs}}
 :runner :console-rhino
 :servers
 {:default cst.server/serve-brepl}
 :server :default
 :repl-dir ".cst-repl"
 :port 9000
 :http 8000})
~~~~

:cljs values inside the :runners map must be symbols that resolve to a
function with the prototype, `[suites & opts]`. When `lein cst test` is run,
the arguments are supplied from the :suites and :opts values in the :cst map,
though both can be overriden on the command line.

The the values of the :servers map must be symbols that resolve to a function
which take a single argument, the :cst map from the project file, with the keys
:build, :runner, and :server changed to point to their corresponding values in
:builds, :runners, and :servers. At the very least, the function should serve a
web page that runs the repl client on the :http port. `cst.server/serve-sbrepl`
does this and can be extended with a ring handler to do more.

The :proc value must be one of three things:

 * a vector of strings, to be run as a subprocess with the :output-to file
   appended to the command;

 * the keyword :rhino (tests will be run in an in-process rhino interpreter);

 * a symbol that resolves to a server function which takes the same argument as
   the functions in the :servers map, but should serve test code rather than
   repl code; `cst.server/serve-cljs` does this and can also be extended with a
   ring handler.

See <http://lukevanderhart.com/2011/09/30/using-javascript-and-clojurescript.html>
for more information about `:externs`, `:libs`, and `:foreign-libs`.

## Authors
   * fhd (Felix H. Dahlke)
   * rplevy (Robert Levy)
   * mmwaikar (Manoj Waikar)
   * bartonj (Justin Barton)
   * jedahu (Jeremy Hughes)

## License
Copyright (C) 2012 Jeremy Hughes
Copyright (C) 2011 Justin Barton
Distributed under the Eclipse Public License, the same as Clojure.
