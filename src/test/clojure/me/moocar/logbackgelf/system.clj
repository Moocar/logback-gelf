(ns me.moocar.logbackgelf.system
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [me.moocar.logbackgelf.servers :as servers])
  (:import (org.slf4j LoggerFactory)))

(defn make-config
  []
  {:full-message-pattern "%rEx%m"
   :short-message-pattern "%rEx%m"
   :use-logger-name? true
   :use-marker? true
   :host "Test"
   :version "1.1"
   :debug? false
   :appender {:type :udp
              :port 12202}
   :static-additional-fields {"_facility" "logback-gelf-test"}
   :include-full-mdc? true})

(defn new-test-system
  [config]
  (component/system-map
   :server (servers/new-test-server config (async/chan 100))
   :config config))

(defmacro with-test-system
  "Starts a new system"
  [[binding-form system-map] & body]
  `(let [system# (component/start ~system-map)
         ~binding-form system#]
     (try ~@body
          (finally
            (component/stop system#)))))

(defmacro fixture [[binding-form config] & body]
  `(let [config# ~config]
     (with-test-system [system# (new-test-system config#)]
       (let [~binding-form system#]
         (try
           ~@body
           (finally
             (.stop (LoggerFactory/getILoggerFactory))))))))

