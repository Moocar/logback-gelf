(ns me.moocar.logbackgelf.end-to-end-test
  (:require [clojure.core.async :as async :refer [<!! >!!]]
            [clojure.data.xml :as xml]
            [clojure.pprint :refer [pprint]]
            [clojure.java.io :as jio]
            [clojure.set :refer [rename-keys]]
            [clojure.string :as string]
            [clojure.stacktrace :refer [print-stack-trace]]
            [clojure.test :refer [is deftest]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [com.stuartsierra.component :as component]
            [me.moocar.logbackgelf.servers :as servers])
  (:import (ch.qos.logback.classic.joran JoranConfigurator)
           (ch.qos.logback.classic Level)
           (ch.qos.logback.classic.spi LoggingEvent)
           (ch.qos.logback.classic.util LevelToSyslogSeverity)
           (java.io ByteArrayOutputStream)
           (org.slf4j LoggerFactory MDC MarkerFactory)
           (org.slf4j.spi LocationAwareLogger)))

(defn wait [ch]
  (async/alt!! ch
               ([v] v)
               (async/timeout 1000)
               ([_] (throw (ex-info "Timed out waiting for request" {})))))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; xml configuration

(def appender-classes
  {:udp "me.moocar.logbackgelf.GelfUDPAppender"
   :tcp "me.moocar.logback.net.SocketEncoderAppender"})

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

(defn logback-xml-sexp
  "Converts a configuration map into an sexpression representation of
  a logback.xml"
  [config]
  {:pre [(map? config)]}
  (let [appender-type (:type (:appender config))]
    [:configuration {:debug (str (or (:debug? config) false))}
     [:appender {:name "GELF Appender"
                 :class (get appender-classes appender-type)}
      [:remoteHost (or (:remote-host (:appender config)) "localhost")]
      [:port (:port (:appender config))]
      [:encoder {:name "GZIP Encoder"
                 :class "ch.qos.logback.core.encoder.LayoutWrappingEncoder"}
       (vec
        (concat
         [:layout {:name "Gelf Layout"
                   :class "me.moocar.logbackgelf.GelfLayout"}
          [:fullMessageLayout {:class "ch.qos.logback.classic.PatternLayout"}
           [:pattern (:full-message-pattern config)]]
          [:shortMessageLayout {:class "ch.qos.logback.classic.PatternLayout"}
           [:pattern (:short-message-pattern config)]]
          [:useLoggerName (:use-logger-name? config)]
          [:useMarker (:use-marker? config)]
          [:host (:host config)]
          [:additionalField "ipAddress:_ip_address"]
          [:additionalField "requestId:_request_id"]
          [:includeFullMDC (:include-full-mdc? config)]]
         (map #(vector :staticAdditionalField (string/join ":" %))
              (:static-additional-fields config))
         (map #(vector :fieldType (string/join ":" %))
              (:field-types config))))]]
     [:root {:level "all"}
      [:appender-ref {:ref "GELF Appender"}]]]))

(defn xml-input-stream
  "Converts an sexpression (vector) representation of XML into an
  input-stream"
  [sexp]
  {:pre [(vector? sexp)]}
  (let [element (xml/sexp-as-element sexp)
        xml-string (xml/emit-str element)]
    (jio/input-stream (.getBytes xml-string "UTF-8"))))

(defn configure-logback-xml
  "Reconfigures logback using the specified xml-input-stream"
  [input-stream]
  {:pre [input-stream]}
  (let [lc (doto (LoggerFactory/getILoggerFactory)
             (.reset))
        configurator (doto (JoranConfigurator.)
                       (.setContext lc))]
    (.doConfigure configurator input-stream)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Logging

(def location-aware-levels
  {:trace LocationAwareLogger/TRACE_INT
   :debug LocationAwareLogger/DEBUG_INT
   :info  LocationAwareLogger/INFO_INT
   :warn  LocationAwareLogger/WARN_INT
   :error LocationAwareLogger/ERROR_INT})

(defn level->syslog-int
  [level-k]
  (let [event (doto (LoggingEvent.)
                (.setLevel (Level/toLevel (name level-k))))]
    (LevelToSyslogSeverity/convert event)))

(defn log-event
  [logger log]
  (.log logger
        (when (:marker log)
          (MarkerFactory/getMarker (:marker log)))
        (:logger-name log)
        (get location-aware-levels (:level log))
        (:message log)
        (make-array Object 0)
        nil))

(defn configure-logback
  [config]
  (-> config
      logback-xml-sexp
      xml-input-stream
      configure-logback-xml))

(defmacro with-logger
  [[binding-form config] & body]
  `(let [logger# (LoggerFactory/getLogger "this_logger")
         ~binding-form logger#]
     (configure-logback ~config)
     ~@body))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; log event generation

(defn message-gen [low high]
  (gen/fmap string/join
            (gen/bind (gen/choose low high)
                      #(gen/vector gen/char-ascii %))))

(defn level-gen []
  (gen/elements (vec (keys location-aware-levels))))

(defn log-gen []
  (gen/hash-map
   :level (level-gen)
   :logger-name (message-gen 1 30)
   :message (message-gen 1 5000)
   :marker (gen/one-of [(gen/return nil) (message-gen 1 20)])))

(defn mdc-gen []
  (gen/map (gen/such-that (comp pos? count) gen/string-alphanumeric)
           (gen/such-that (comp pos? count) gen/string-alphanumeric)))

(def field-types
  [["int" gen/int]
   ["Integer" gen/int]
   ["long" gen/int]
   ["Long" gen/int]
   ;; Hard to get float testing working with json serialization
;   ["float" (gen/fmap double gen/ratio)]
;   ["Float" (gen/fmap double gen/ratio)]
   ["double" (gen/fmap double gen/ratio)]
   ["Double" (gen/fmap double gen/ratio)]])

(defn field-gen []
  (gen/bind (gen/elements field-types)
            (fn [[name gen]]
              (gen/tuple (gen/return name) gen))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests

(defmacro fixture [[binding-form config] & body]
  `(let [config# ~config]
     (with-test-system [system# (new-test-system config#)]
       (let [~binding-form system#]
         (try
           ~@body
           (finally
             (.stop (LoggerFactory/getILoggerFactory))))))))

(defn log->expected-json
  "Converts a test.check generated log into the same format it should
  be received in by the server"
  [config log mdc]
  (-> log
      (update :level level->syslog-int)
      (dissoc :marker)
      (assoc :host (:host config)
             :_facility (val (first (:static-additional-fields config)))
             :version (:version config))
      (cond-> (:marker log)
        (assoc :_marker (:marker log)))
      (rename-keys {:message :full_message
                    :logger-name :_loggerName})
      (as-> log
          (assoc log :short_message (:full_message log))
        (reduce-kv (fn [m k v]
                     (assoc m (keyword (str "_" k)) v))
                   log
                   mdc))))

(defn t-test
  "Generate general logs and make sure they arrive on the server in
  the expected format"
  [{:keys [config server] :as system}]
  (let [msg-ch (:msg-ch server)]
    (configure-logback config)
    (prop/for-all [log (log-gen)
                   mdc (mdc-gen)]
      (MDC/clear)
      (doseq [[k v] mdc]
        (MDC/put k v))
      (let [logger (LoggerFactory/getLogger (:logger-name log))]
        (log-event logger log)
        (let [json (wait msg-ch)]
          (and (<= (:timestamp json) (System/currentTimeMillis))
               (= (log->expected-json config log mdc)
                  (dissoc json :timestamp))))))))

(defn t-substitute
  [{:keys [config server] :as system}]
  (let [msg-ch (:msg-ch server)]
    (with-logger [logger config]
      (.debug logger "This is a ({}) log" "sub")
      (let [json (wait msg-ch)]
        (= (:full_message json)
           "This is a (sub) log")))))

(defn t-exception
  [{:keys [config server] :as system}]
  (let [msg-ch (:msg-ch server)]
    (with-logger [logger config]
      (.error logger "my msg" (ex-info "the exception" {}))
      (let [json (wait msg-ch)]
        (is (string? (:_line json)))
        (is (string? (:_file json)))))))

(defn t-static-additional-field
  [{:keys [config server] :as system}]
  (let [msg-ch (:msg-ch server)
        config (assoc config :static-additional-fields {"_node_name" "www013"})]
    (with-logger [logger config]
      (.debug logger "my msg")
      (let [json (wait msg-ch)]
        (is (= "www013" (:_node_name json)))))))

(defn t-undefined-hostname-string
  "Ensure that when a bad remote host is included, that an
  appropariate error is reported to the user. Note that I can't think
  of a way to test this programatically, so please watch the server
  stoutput to make sure an error message is logged"
  [{:keys [config server] :as system}]
  (let [msg-ch (:msg-ch server)
        config (-> config
                   (assoc-in [:appender :remote-host] "GRAYLOG_SERVER_IP_IS_UNDEFINED")
                   (assoc :debug? true))]
    (with-logger [logger config]
      (.debug logger "my msg"))))

(defn t-field-types [system]
  (prop/for-all [[field-type field-val] (field-gen)
                 field-name (gen/not-empty gen/string-alphanumeric)]
    (let [{:keys [config server]} system
          msg-ch (:msg-ch server)
          config (assoc config :field-types {(str \_ field-name) field-type})]
      (with-logger [logger config]
        (MDC/clear)
        (MDC/put field-name (str field-val))
        (.debug logger "msg")
        (let [json (wait msg-ch)]
          (is (= field-val
                 (get json (keyword (str "_" field-name))))))))))

(defn t-all []
  (fixture [system (make-config)]
           (t-substitute system)
           (t-exception system)
           (t-static-additional-field system)
           (t-undefined-hostname-string system)
           (is (= true (:result (tc/quick-check 100 (t-field-types system)))))
           (is (= true (:result (tc/quick-check 100 (t-test system)))))))

(deftest t
  (t-all))

(defn send-request
  "For dev purposes"
  []
  (let [config (make-config)]
    (configure-logback-xml (xml-input-stream (logback-xml-sexp config)))
    (let [logger (LoggerFactory/getLogger "this_logger")]
      (dotimes [_ 10]
        (future (.debug logger (string/join (repeatedly (* 512 5) #(rand-nth "abcdefghijklmnopqrstuvwxyz"))) #_(ex-info "ERROR ME TIMBER" {}))))
      #_(.debug logger (string/join (repeatedly 30 #(rand-nth "abcdefghijklmnopqrstuvwxyz"))) #_(ex-info "ERROR ME TIMBER" {})))))
