(ns me.moocar.logbackgelf.end-to-end-test
  (:require [clojure.core.async :as async :refer [<!! >!!]]
            [clojure.data.json :as json]
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
            [com.stuartsierra.component :as component])
  (:import (ch.qos.logback.classic.joran JoranConfigurator)
           (ch.qos.logback.classic Level)
           (ch.qos.logback.classic.spi LoggingEvent)
           (ch.qos.logback.classic.util LevelToSyslogSeverity)
           (java.io ByteArrayOutputStream)
           (java.net DatagramSocket DatagramPacket SocketException ServerSocket)
           (java.nio.charset Charset)
           (java.util Scanner)
           (org.slf4j LoggerFactory MDC MarkerFactory)
           (org.slf4j.spi LocationAwareLogger)))

(def default-threshold 1000)
(def utf-8 (Charset/forName "UTF-8"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test Server

(defprotocol SocketReader
  (listen [this msg-ch]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UDP

(def ^:private max-packet-size 8192)

(defn packet->json [{:keys [^int length ^int offset ^bytes data] :as packet}]
  (let [json-string (String. data offset length utf-8)]
    (json/read-str json-string :key-fn keyword)))

(defn chunked? [{:keys [data offset length] :as packet}]
  (= [0x1e 0x0f] [(aget data offset) (aget data (inc offset))]))

(defn dechunk [{:keys [data offset length] :as packet}]
  {:pre [(chunked? packet)]}
  (let [coll (take length (drop offset data))
        [magic-bytes rest-bytes] (split-at 2 coll)
        [message-id rest-bytes] (split-at 8 rest-bytes)
        [[seq-number] rest-bytes] (split-at 1 rest-bytes)
        [[seq-count] payload-bytes] (split-at 1 rest-bytes)]
    {:message-id message-id
     :seq-number seq-number
     :seq-count seq-count
     :payload payload-bytes}))

(defn stitch-chunks [chunks {:keys [message-id payload] :as chunk}]
  (->> (conj chunks chunk)
       (filter #(= message-id (:message-id %)))
       (sort-by :seq-number)
       (mapcat :payload)))

(defn process-chunk [chunks packet]
  (let [new-chunk (dechunk packet)
        existing-chunks (filter #(= (:message-id new-chunk) (:message-id %)) chunks)]
    (if (= (count existing-chunks) (dec (:seq-count new-chunk)))
      (let [full-bytes-coll (stitch-chunks chunks new-chunk)
            full-packet {:data (byte-array full-bytes-coll)
                         :offset 0
                         :length (count full-bytes-coll)}]
        [(remove (set existing-chunks) chunks)
         full-packet])
      [(conj chunks new-chunk)
       nil])))

(extend-type DatagramSocket
  SocketReader
  (listen [socket msg-ch]
    (loop [chunks #{}]
      (when (not (.isClosed socket))
        (let [buf (byte-array max-packet-size)
              packet (DatagramPacket. buf max-packet-size)]
          (.receive socket packet)
          (let [packet (bean packet)
                {:keys [length offset data]} packet]
            (if (chunked? packet)
              (let [[chunks full-packet] (process-chunk chunks packet)]
                (when full-packet
                  (>!! msg-ch (packet->json full-packet)))
                (recur chunks))
              (do (>!! msg-ch (packet->json packet))
                  (recur chunks)))))))))

(defn new-datagram-socket-reader [server]
  (doto (DatagramSocket. (:port server))
    (.setReceiveBufferSize 5240000)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; TCP

(extend-type ServerSocket
  SocketReader
  (listen [server-socket msg-ch]
    (while (not (.isClosed server-socket))
      (let [socket (.accept server-socket)
            input-stream (jio/input-stream socket)
            scanner (doto (Scanner. input-stream "UTF-8")
                      (.useDelimiter "\0"))]
        (loop []
          (when (.hasNext scanner)
            (let [string (.next scanner)
                  json (json/read-str string :key-fn keyword)]
              (>!! msg-ch json)
              (recur))))))))

(defn new-server-socket-reader [server]
  (ServerSocket. (:port server)))

(defrecord TestServer [port msg-ch make-server-socket server-socket]
  component/Lifecycle
  (start [this]
    (if server-socket
      this
      (let [server-socket (make-server-socket this)]
        (async/thread
          (try
            (listen server-socket msg-ch)
            (catch SocketException e
              ;; Ignore as the server has been shutown. End loop
              )))
        (assoc this :server-socket server-socket))))
  (stop [this]
    (if server-socket
      (do (.close server-socket)
          (assoc this :server-socket nil))
      this)))

(defn new-test-server [config msg-ch]
  (map->TestServer {:port (:port (:appender config))
                    :msg-ch msg-ch
                    :make-server-socket (if (= :tcp (:type (:appender config)))
                                          new-server-socket-reader
                                          new-datagram-socket-reader)}))

(defn new-test-system [config]
  (component/system-map
   :server (new-test-server config (async/chan 100))
   :config config))

(defmacro with-test-system [[binding-form system-map] & body]
  `(let [system# (component/start ~system-map)
         ~binding-form system#]
     (try ~@body
          (finally
            (component/stop system#)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Async utils

(defn wait [ch]
  (async/alt!! ch
               ([v] v)
               (async/timeout 1000)
               ([_] (throw (ex-info "Timed out waiting for request" {})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; xml configuration

(def appender-classes
  {:udp "me.moocar.logbackgelf.GelfUDPAppender"
   :tcp "me.moocar.logbackgelf.SocketEncoderAppender"})

(defn make-config
  []
  {:full-message-pattern "%rEx%m"
   :short-message-pattern "%.5m"
   :use-logger-name? true
   :use-marker? true
   :host "Test"
   :version "1.1"
   :appender {:type :udp
              :port 12201}
   :static-additional-fields {"_facility" "logback-gelf-test"}
   :include-full-mdc? true})

(defn logback-xml-sexp [config]
  (let [appender-type (:type (:appender config))]
    [:configuration {:debug "true"}
     [:appender {:name "GELF Appender"
                 :class (get appender-classes appender-type)}
      [:remoteHost "localhost"]
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

(defn xml-input-stream [sexp]
  (let [element (xml/sexp-as-element sexp)
        xml-string (xml/emit-str element)]
    (jio/input-stream (.getBytes xml-string "UTF-8"))))

(defn configure-logback-xml
  [input-stream]
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

(defn level->syslog-int [level-k]
  (let [event (doto (LoggingEvent.)
                (.setLevel (Level/toLevel (name level-k))))]
    (LevelToSyslogSeverity/convert event)))

(defn log-event [logger log]
  (.log logger
        (when (:marker log)
          (MarkerFactory/getMarker (:marker log)))
        (:logger-name log)
        (get location-aware-levels (:level log))
        (:message log)
        (make-array Object 0)
        nil))

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
   :message (message-gen 1 50000)
   :marker (gen/one-of [(gen/return nil) (message-gen 1 20)])))

(defn mdc-gen []
  (gen/map (gen/such-that (comp pos? count) gen/string-alphanumeric)
           (gen/such-that (comp pos? count) gen/string-alphanumeric)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests

(defn log->expected-json
  [config log mdc]
  (-> log
      (update :level level->syslog-int)
      (dissoc :marker)
      (assoc :host (:host config)
             :facility (:facility config)
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

(defn t-test [system]
  (let [config (:config system)
        msg-ch (:msg-ch (:server system))]
    (configure-logback-xml (xml-input-stream (logback-xml-sexp config)))
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

(defn t-substitute [system]
  (let [{:keys [config server]} system
        msg-ch (:msg-ch server)
        logger (LoggerFactory/getLogger "this_logger")
        string "This is a ({}) log"]
    (configure-logback-xml (xml-input-stream (logback-xml-sexp config)))
    (.debug logger string "sub")
    (let [json (wait msg-ch)]
      (= (:full_message json)
         "This is a (sub) log"))))

(defn t-exception [system]
  (let [{:keys [config server]} system
        msg-ch (:msg-ch server)
        logger (LoggerFactory/getLogger "this_logger")
        string "my msg"
        exception (ex-info "the exception" {})]
    (configure-logback-xml (xml-input-stream (logback-xml-sexp config)))
    (.error logger string exception)
    (let [json (wait msg-ch)]
      (is (string? (:line json)))
      (is (string? (:file json))))))

(defn t-static-additional-field [system]
  (let [{:keys [config server]} system
        msg-ch (:msg-ch server)
        logger (LoggerFactory/getLogger "this_logger")
        string "my msg"
        xml (-> config
                (assoc :static-additional-fields {"_node_name" "www013"})
                logback-xml-sexp
                xml-input-stream)]
    (configure-logback-xml xml)
    (.debug logger string)
    (let [json (wait msg-ch)]
      (is (= "www013" (:_node_name json))))))

(def field-types
  [["int" gen/int]
   ["Integer" gen/int]
   ["long" gen/int]
   ["Long" gen/int]
   ;; Hard to get float testing working with json serialization
;   ["float" (gen/fmap double gen/ratio)]
;   ["Float" (gen/fmap double gen/ratio)]
   ["double" (gen/fmap double gen/ratio)]
   ["Double" (gen/fmap double gen/ratio)]
   ["foo" gen/string-alphanumeric]])

(defn t-field-types [system]
  (prop/for-all [[field-type field-val] (gen/bind (gen/elements field-types)
                                                  (fn [[name gen]]
                                                    (gen/tuple (gen/return name) gen)))
                 field-name gen/string-alphanumeric]
    (let [{:keys [config server]} system
          msg-ch (:msg-ch server)
          logger (LoggerFactory/getLogger "this_logger")
          xml (-> config
                  (assoc :field-types {(str \_ field-name) field-type})
                  logback-xml-sexp
                  xml-input-stream)]
      (configure-logback-xml xml)

      (MDC/clear)
      (MDC/put field-name (str field-val))
      (.debug logger "msg")
      (let [json (wait msg-ch)]
        (is (= field-val
               (get json (keyword (str "_" field-name)))))))))

(defn t-all []
  (let [config (make-config)]
    (with-test-system [system (new-test-system config)]
      (try
        (t-substitute system)
        (t-exception system)
        (t-static-additional-field system)
        (is (= true (:result (tc/quick-check 100 (t-field-types system)))))
        (is (= true (:result (tc/quick-check 100 (t-test system)))))
        (finally
          (.stop (LoggerFactory/getILoggerFactory)))))))

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
