(defproject traffic "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2173"]
                 [org.clojure/core.async "0.1.280.0-ca148e-alpha"]
                 [om "0.5.0"]]

  :plugins [[lein-cljsbuild "1.0.2"]]

  :source-paths ["src"]

  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src"]
              :compiler {
                         :output-to "traffic.js"
                         :output-dir "out"
                         :optimizations :none
                         :source-map true}}
             {:id "release"
              :source-paths ["src"]
              :compiler {
                         :output-to "traffic-release.js"
                         :optimizations :advanced
                         :pretty-print false
                         :preamble  ["react/react.min.js"]
                         :externs  ["react/externs/react.js"]
                         }}]})
