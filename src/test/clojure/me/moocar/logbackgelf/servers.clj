(ns me.moocar.logbackgelf.servers
  (:require [clojure.core.async :as async :refer [<!! >!!]]
            [clojure.data.json :as json]
            [clojure.java.io :as jio]
            [com.stuartsierra.component :as component])
  (:import (java.net DatagramSocket DatagramPacket SocketException ServerSocket)
           (java.nio.charset Charset)
           (java.util Scanner)))

(def utf-8 (Charset/forName "UTF-8"))

(defprotocol SocketReader
  (listen [this msg-ch]
    "Listen for new messages on socket"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UDP

(def ^:private max-packet-size 65536)

(defn ^:private bytes? [x]
  (= (Class/forName "[B") (type x)))

(defn ^:private packet?
  [x]
  (and (number? (:length x))
       (number? (:offset x))
       (bytes?  (:data x))))

(defn ^:private packet->json
  "Converts packet into a clojure datastructure using
  clojure.data.json"
  [packet]
  {:pre [(packet? packet)]}
  (let [{:keys [^int length ^int offset ^bytes data]} packet
        json-string (String. data offset length utf-8)]
    (json/read-str json-string :key-fn keyword)))

(defn ^:private chunked?
  "Returns true if packet is chunked"
  [packet]
  {:pre [(packet? packet)]}
  (let [{:keys [data offset]} packet]
    (= [0x1e 0x0f] [(aget data offset) (aget data (inc offset))])))

(defn ^:private dechunk
  "Converts a packet into a map of :message-id (coll of
  bytes), :seq-number (byte), :seq-count (byte), :payload (coll of
  bytes)"
  [packet]
  {:pre [(chunked? packet)]}
  (let [{:keys [data offset length]} packet
        v (vec (take length (drop offset data)))]
    {:message-id (subvec v 2 10)
     :seq-number (nth v 10)
     :seq-count  (nth v 11)
     :payload (subvec v 12)}))

(defn ^:private chunk?
  [x]
  (every? #(contains? x %) [:message-id :seq-number :seq-count :payload]))

(defn ^:private rebuild-payload
  "Returns the complete payload by concatenating the payload bytes
  from all the chunks. The chunks must all have the same message-id"
  [chunks]
  {:pre [(every? chunk? chunks)
         (= 1 (count (set (map :message-id chunks))))]}
  (->> chunks
       (sort-by :seq-number)
       (mapcat :payload)))

(defn ^:private all-chunks?
  "Returns true if all the chunks for this message-id are in the set of chunks"
  [chunks]
  {:pre [(every? chunk? chunks)]}
  (let [seq-count (:seq-count (first chunks))]
    (= (count chunks) seq-count)))

(defn ^:private find-message-chunks
  "Finds chunks in all-chunks that share the same message-id as
  new-chunk, and returns them with new-chunk added to the end"
  [all-chunks new-chunk]
  {:pre [(every? chunk? all-chunks)
         (chunk? new-chunk)]}
  (->> all-chunks
       (filter #(= (:message-id new-chunk) (:message-id %)))
       (cons new-chunk)))

(defn ^:private process-chunk
  "Process the new chunk. If it is the last chunk in a set it rebuilds
  the complete payload and returns a pair
  of [chunks-with-out-matching-chunks reconstructed-packet]. If chunk
  is part of an uncomplete set, returns [chunks-with-new-chunk-added
  nil]"
  [chunks new-chunk]
  {:pre [(every? chunk? chunks)
         (chunk? new-chunk)]}
  (let [message-chunks (find-message-chunks chunks new-chunk)]
    (if (all-chunks? message-chunks)
      (let [full-payload (rebuild-payload message-chunks)
            full-packet {:data (byte-array full-payload)
                         :offset 0
                         :length (count full-payload)}]
        [(remove (set message-chunks) chunks)
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
              (let [[chunks full-packet] (process-chunk chunks (dechunk packet))]
                (when full-packet
                  (>!! msg-ch (packet->json full-packet)))
                (recur chunks))
              (do (>!! msg-ch (packet->json packet))
                  (recur chunks)))))))))

(defn new-datagram-socket-reader
  "Creates a new UDP SocketReader that implements a DatagramSocket
  listener. It listens on socket for new UDP packets. If a packet is
  not chunked, it is immediately put onto :msg-ch. If a packet is
  chunked, it is stored in memory until a full set of message chunks
  can be created, at which point the full payload is rebuilt,
  converted to clojure using json deserialization and put
  onto :msg-ch"
  [server]
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

(defn new-server-socket-reader
  "Creates a bew TCP SocketReader that implements a ServerSocket
  listener. It listens on socket for new connections. When one is
  received, it uses a scanner to wait for null delimited (\0) strings,
  converts them into clojure using json deserialization and then puts
  them onto :msg-ch"
  [server]
  (ServerSocket. (:port server)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generic Server

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

(defn new-test-server
  "Creates a new Server that will bind to `(:port (:appender
  config))`, using a UDP or TCP server depending on `(:type (:appender
  config))`. Received messages will be put onto `msg-ch`"
  [config msg-ch]
  (map->TestServer {:port (:port (:appender config))
                    :msg-ch msg-ch
                    :make-server-socket (if (= :tcp (:type (:appender config)))
                                          new-server-socket-reader
                                          new-datagram-socket-reader)}))
