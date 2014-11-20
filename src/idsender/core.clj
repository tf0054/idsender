(ns idsender.core
  (:require
   [idsender.http :as http]
   [throttler.core :refer [throttle-chan]]
   [clojure.core.async :refer [chan >!! <!! thread]]
   [clojure.tools.cli :refer [parse-opts]]
   [clojure.string :as string]
   ))

; ---
(def persec 10)
; ---

(def cli-options
  ;definitions of option
  ;long option should have an example in it....
  [["-t" "--tuple A" "REQUIRED: Target tuple"
    :id :tuple
    :default "covtype01"]
   ["-g" "--gserver 192.168.30.10:9191" "The genn.ai address if you want to send data"
    :id :server
    :default "192.168.30.10:9191"]
   ["-u" "--userid 545562980cf2827387ebbfe6" "User id string"
    :id :userid
    :default "545562980cf2827387ebbfe6"]
   ["-s" "--speed 10" "number of sendings per sec"
    :id :persec
    :default 10
    :parse-fn #(Integer/parseInt %)]
   ["-h" "--help" "Show this help msg"]])

(defn usage [options-summary]
  (->> ["This is my program. There are many like it, but this one is mine."
        ""
        "Usage: program-name [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Please refer to the manual page for more information."]
       (string/join \newline)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(def in (chan 1))
(def slow-chan (throttle-chan in persec :second))
;(def slow-chan (throttle-chan in 1 :millisecond))

(def not-nil? (complement nil?))

(defn showThreadId []
  "Getting thread-id of this processing"
  (.getId (Thread/currentThread)))

(defn showResponse
  "Showing corespondent response."
  [object status body]
  (println (str status " "
                (- (System/currentTimeMillis) (:start object)) " "
                (:line object) " (" (:threadid object) ")")))

(defn postEachLine
  "Post the given data with http-kit client."
  [options line]
  (http/postItem
   (:server options) (:userid options) (:tuple options) line
   (partial showResponse {:line line
                          :threadid (showThreadId)
                          :start (System/currentTimeMillis)})))

(defn async-kicker
  "Start num-consumers threads that will consume work from the slow-chan"
  [options num-consumers]
  (dotimes [_ num-consumers]
    (thread
      (while true
        (let [line (<!! slow-chan)]
          (postEachLine options (clojure.string/trim-newline line)))))))

(defn -main
  "Main function called via 'lein run'"
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (if (:help options)
      (exit 0 (usage summary)))
    (if (nil? (:tuple options))
      (do (println "Tuple have to be defined using \"-t\" option")
        (exit 0 (usage summary))))
    (println
     (str "idsender started with " (:persec options)
          " threads targeting to " (:server options) "."))
    (do
      (async-kicker options 8)
      (while true (let [line (str "{Id:" (rand-int 99940) "}")]
        (>!! in line))))))
