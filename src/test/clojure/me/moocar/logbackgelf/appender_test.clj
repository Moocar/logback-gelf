(ns me.moocar.logbackgelf.appender-test
  (:require [clojure.core.async :as async :refer [<!!]]
            [clojure.test :refer [run-tests deftest is testing]]
            [me.moocar.logbackgelf.system :as system])
  (:import (ch.qos.logback.core.encoder LayoutWrappingEncoder)
           (java.io OutputStream IOException)
           (me.moocar.logbackgelf GelfUDPAppender GelfLayout)
           (org.slf4j LoggerFactory)))

(defn- new-throwing-output-stream []
  (proxy [OutputStream] []
    (write [b]
      (throw (IOException. "Excepted exception")))))

(defn- new-udp-appender [context config encoder]
  (doto (GelfUDPAppender.)
    (.setContext context)
    (.setPort (:port (:appender config)))
    (.setEncoder encoder)
    (.start)))

(defn- new-layout [context]
  (doto (GelfLayout.)
    (.setContext context)
    (.start)))

(defn- new-layout-encoder [context layout]
  (doto (LayoutWrappingEncoder.)
    (.setContext context)
    (.setLayout layout)
    (.start)))

(defn- build-appender [config]
  (let [context (doto (LoggerFactory/getILoggerFactory)
                  (.reset))
        gelf-layout (new-layout context)
        encoder (new-layout-encoder context gelf-layout)]
    (new-udp-appender context config encoder)))

(deftest t-io-exception-not-fatal
  (testing "when an IO exception is thrown in the output stream
  append, make sure that future appends still complete. I.e it should recover"
    (system/fixture
     [system (system/make-config)]
     (let [{:keys [config server]} system
           appender (build-appender config)
           gelf-output-stream (.getOutputStream appender)
           throwing-output-stream (new-throwing-output-stream)
           logger (doto (LoggerFactory/getLogger "this_logger")
                    (.addAppender appender))
           msg-ch (:msg-ch server)]

       (.debug logger "msg 1")
       (.setOutputStream appender throwing-output-stream)
       (.debug logger "msg 2")
       (.setOutputStream appender gelf-output-stream)
       (.debug logger "msg 3")

       (is (= ["msg 1" "msg 3"]
              (->> msg-ch
                   (async/take 2)
                   (async/into [])
                   <!!
                   (map :full_message)))
           "msg 2 should not succeed, but msg 3 should work again")))))
