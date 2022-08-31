(ns frontend.fs
  (:require [cljs-bean.core :as bean]
            [clojure.string :as string]
            [frontend.config :as config]
            [frontend.db :as db]
            [frontend.encrypt :as encrypt]
            [frontend.fs.bfs :as bfs]
            [frontend.fs.capacitor-fs :as mobile]
            [frontend.fs.nfs :as nfs]
            [frontend.fs.node :as node]
            [frontend.fs.protocol :as protocol]
            [frontend.fs.tauri :as tauri]
            [frontend.mobile.util :as mobile-util]
            [frontend.state :as state]
            [frontend.util :as util]
            [lambdaisland.glogi :as log]
            [promesa.core :as p]))

(defonce nfs-record (nfs/->Nfs))
(defonce bfs-record (bfs/->Bfs))
(defonce node-record (node/->Node))
(defonce mobile-record (mobile/->Capacitorfs))
(defonce tauri-record (tauri/->Tauri))

(prn ::debug-tauri-enabled? (protocol/available? tauri-record))

(defn local-db?
  [dir]
  (and (string? dir)
       (config/local-db? (subs dir 1))))

(defn get-fs
  [dir]
  (let [bfs-local? (or (string/starts-with? dir "/local")
                       (string/starts-with? dir "local"))]
    (cond
      (and (util/electron?) (not bfs-local?))
      node-record

      (mobile-util/native-platform?)
      mobile-record

      (and (protocol/available? tauri-record) (not bfs-local?))

      tauri-record
      (local-db? dir)
      nfs-record

      :else
      bfs-record)))

(defn mkdir!
  [dir]
  (protocol/mkdir! (get-fs dir) dir))

(defn mkdir-recur!
  [dir]
  (protocol/mkdir-recur! (get-fs dir) dir))

(defn readdir
  [dir]
  (protocol/readdir (get-fs dir) dir))

(defn unlink!
  "Should move the path to logseq/recycle instead of deleting it."
  [repo path opts]
  (protocol/unlink! (get-fs path) repo path opts))

(defn rmdir!
  "Remove the directory recursively.
   Warning: only runs when it is a browser cache(bfs)."
  [dir]
  (protocol/rmdir! (get-fs dir) dir))

(defn write-file!
  [repo dir path content opts]
  (when content
    (let [fs-record (get-fs dir)]
      (p/let [md-or-org? (contains? #{"md" "markdown" "org"} (util/get-file-ext path))
              content (if-not md-or-org? content (encrypt/encrypt content))]
        (->
         (p/let [opts (assoc opts
                             :error-handler
                             (fn [error]
                               (state/pub-event! [:instrument {:type :write-file/failed
                                                               :payload {:fs (type fs-record)
                                                                         :user-agent (when js/navigator js/navigator.userAgent)
                                                                         :path path
                                                                         :content-length (count content)
                                                                         :error-str (str error)
                                                                         :error error}}])))
                 _ (protocol/write-file! (get-fs dir) repo dir path content opts)]
           (when (= bfs-record fs-record)
             (db/set-file-last-modified-at! repo (config/get-file-path repo path) (js/Date.))))
         (p/catch (fn [error]
                    (log/error :file/write-failed {:dir dir
                                                   :path path
                                                   :error error})
                    ;; Disable this temporarily
                    ;; (js/alert "Current file can't be saved! Please copy its content to your local file system and click the refresh button.")
                    )))))))

(defn read-file
  ([dir path]
   (let [fs (get-fs dir)
         options (if (= fs bfs-record)
                   {:encoding "utf8"}
                   {})]
     (read-file dir path options)))
  ([dir path options]
   (protocol/read-file (get-fs dir) dir path options)))

(defn rename!
  [repo old-path new-path]
  ;; See https://github.com/isomorphic-git/lightning-fs/issues/41
  (cond
                                        ; See https://github.com/isomorphic-git/lightning-fs/issues/41
    (= old-path new-path)
    (p/resolved nil)

    :else
    (let [[old-path new-path]
          (map #(if (or (util/electron?) (mobile-util/native-platform?))
                  %
                  (str (config/get-repo-dir repo) "/" %))
               [old-path new-path])]
      (protocol/rename! (get-fs old-path) repo old-path new-path))))

(defn copy!
  [repo old-path new-path]
  (cond
    (= old-path new-path)
    (p/resolved nil)

    :else
    (let [[old-path new-path]
          (map #(if (or (util/electron?) (mobile-util/native-platform?))
                  %
                  (str (config/get-repo-dir repo) "/" %))
               [old-path new-path])]
      (protocol/copy! (get-fs old-path) repo old-path new-path))))

(defn stat
  [dir path]
  (protocol/stat (get-fs dir) dir path))

(defn- get-record
  []
  (cond
    (util/electron?)
    node-record

    (mobile-util/native-platform?)
    mobile-record

    (protocol/available? tauri-record)
    tauri-record

    :else
    nfs-record))

(defn open-dir
  [ok-handler]
  (let [record (get-record)]
    (p/let [result (protocol/open-dir record ok-handler)]
      (if (or (util/electron?) (mobile-util/native-platform?))
        (let [[dir & paths] result] [(:path dir) paths])
        result))))

(defn get-files
  [path-or-handle ok-handler]
  (let [record (get-record)
        electron? (util/electron?)
        mobile? (mobile-util/native-platform?)]
    (p/let [result (protocol/get-files record path-or-handle ok-handler)]
      (if (or electron? mobile?)
        (let [result (bean/->clj result)]
          (if electron? (rest result) result))
        result))))

(defn watch-dir!
  [dir]
  (when (not= dir "/local")
    (protocol/watch-dir! (get-record) dir)))

(defn unwatch-dir!
  [dir]
  (when (not= dir "/local")
    (js/console.log "using fs backend" (protocol/backend-name (get-record)))
    (protocol/unwatch-dir! (get-record) dir)
    (js/console.log "ok")))

(defn mkdir-if-not-exists
  [dir]
  (->
   (when dir
     (util/p-handle
      (stat dir nil)
      (fn [_stat])
      (fn [_error]
        (mkdir! dir))))
   (p/catch (fn [error] (js/console.error error)))))

(defn create-if-not-exists
  ([repo dir path]
   (create-if-not-exists repo dir path ""))
  ([repo dir path initial-content]
   (let [path (if (util/absolute-path? path) path
                  (if (util/starts-with? path "/")
                    path
                    (str "/" path)))]
     (->
      (p/let [_stat (stat dir path)]
        true)
      (p/catch
       (fn [_error]
         (p/let [_ (write-file! repo dir path initial-content nil)]
           false)))))))

(defn file-exists?
  [dir path]
  (util/p-handle
   (stat dir path)
   (fn [_stat] true)
   (fn [_e] false)))

(defn dir-exists?
  [dir]
  (file-exists? dir ""))
