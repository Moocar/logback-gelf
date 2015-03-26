(defproject me.moocar/logback-gelf "0.13-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :javac-options ["-target" "1.6" "-source" "1.6"]
  :java-source-paths ["src/main/java"]
  :test-paths ["test" "src/test/clojure"]
  :pom-location "target/"

  :dependencies [[ch.qos.logback/logback-classic "1.1.2"]
                 [com.google.code.gson/gson "2.2.4"]
                 [org.slf4j/slf4j-api "1.7.10"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.7.0-alpha5"]
                                  [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                                  [org.clojure/data.json "0.2.6"]
                                  [org.clojure/data.xml "0.0.8"]
                                  [org.clojure/test.check "0.7.0"]
                                  [org.powermock/powermock-api-mockito "1.5.4"]
                                  [com.stuartsierra/component "0.2.3"]
                                  [me.moocar/socket-encoder-appender "0.1-SNAPSHOT"]]
                   :resource-paths ["src/test/resources"]}})
