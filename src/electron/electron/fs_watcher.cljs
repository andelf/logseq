(ns electron.fs-watcher
  (:require [cljs-bean.core :refer [->js]]
            [electron.utils :as utils]
            [electron.configs :as configs]
            [clojure.string :as string]
            [promesa.core :as p]
            ["fs-extra" :as fse]
            ["fs/promises" :as fs]
            ["path" :as path]
            ["@parcel/watcher" :as watcher]))

(defonce watcher-cache-path configs/watcher-snapshot-root)

(defonce watcher-options #js {:ignore [".git", "logseq/bak", ".DS_Store", "logseq/.recycle"]})

;; window-id -> current subscription
(defonce window-subscriptions (atom {}))
;; window-id -> current path
(defonce window-graph-paths (atom {}))

;; https://github.com/parshap/node-sanitize-filename/blob/master/index.js
;; Illegal Characters on Various Operating Systems
(defonce illegal-re #"[\/?<>\\:*|\"]")
;; Unicode Control codes * C0 0x00-0x1f & C1 (0x80-0x9f)
(defonce control-re #"[\u0000-\u001f\u0080-\u009f]")
;; Reserved filenames on Unix-based systems (".", "..")
(defonce reserved-re #"^\.+$")
;; Reserved filenames in Windows
(defonce windows-reserved-re #"(?i)^(con|prn|aux|nul|com[0-9]|lpt[0-9])(\..*)?$")
(defonce windows-trailing-re #"[. ]+$")

(defn- sanitize-filename
  "NOTE: This is a one-way sanitize."
  [filename]
  (-> filename
      (string/replace illegal-re "+")
      (string/replace control-re "+")
      (string/replace reserved-re "+")
      (string/replace windows-reserved-re "+")
      (string/replace windows-trailing-re "+")))

(defn- graph-path->fs-watcher-snapshot-path
  [graph-path]
  (let [basename (sanitize-filename graph-path)
        ;; basename (re-find #".{240}$" basename)
        snapshot-name (str basename ".watcher")]
    (path/join watcher-cache-path snapshot-name)))

(defonce file-watcher-chan "file-watcher")

(defn- send-file-watcher! [^js win type payload]
  (when-not (.isDestroyed win)
    (prn :send payload)
    (.. win -webContents
        (send file-watcher-chan
              (->js {:type type :payload payload})))))

(defn- stat-async
  [path]
  (-> (fs/stat path)
      (p/catch #(identity nil))))


(defn- read-file-async
  [path]
  (-> (fs/readFile path)
      (p/then #(.toString %))
      (p/catch (fn [error]
                 (js/console.error "Error reading file: " path error)))))



(defn- publish-file-event!
  [win dir path event]
  (p/let [stat (stat-async path)
          content (when (and stat (.isFile stat))
                    (read-file-async path))]
    (send-file-watcher! win event
                        {:dir (utils/fix-win-path! dir)
                         :path (utils/fix-win-path! path)
                         :content content
                         :stat stat})))

(defn watch-dir!
  [win dir]
  (when (fse/pathExistsSync dir)
    (p/let [watcher-snapshot (graph-path->fs-watcher-snapshot-path dir)
            _ (prn :snapshot watcher-snapshot)
            stat (stat-async watcher-snapshot)
            first-time? (not stat)
            _ (fse/ensureFile watcher-snapshot)
            events-callback (fn [^js events]
                              (->> events
                                   (filter #(not (utils/ignored-path? dir (.-path %))))
                                   (map #(let [type (.-type %)
                                               path (.-path %)]
                                           (prn :path path :type type)
                                           (case type
                                             "create" (publish-file-event! win dir path "add")
                                             "update" (publish-file-event! win dir path "change")
                                             "delete" (send-file-watcher! win "unlink"
                                                                          {:dir (utils/fix-win-path! dir)
                                                                           :path (utils/fix-win-path! path)})
                                             (js/console.error "unknown watcher event:" %))))
                                   p/all))
            old-events (watcher/getEventsSince dir watcher-snapshot watcher-options)
            _ (events-callback old-events)
            win-id (.-id win)
            subscription (watcher/subscribe dir
                                            (fn [^js err events]
                                              (if (not err)
                                                (events-callback events)
                                                (js/console.error err)))
                                            watcher-options)
            _ (when first-time?
                (watcher/writeSnapshot dir watcher-snapshot watcher-options))]
      (swap! window-subscriptions assoc win-id subscription)
      (swap! window-graph-paths assoc win-id dir))))

(defn- close-watcher-inner!
  [win-id]
  (when-let [sub (get @window-subscriptions win-id)]
    (.. sub -unsubscribe)
    (swap! window-subscriptions dissoc win-id))
  (when-let [dir (get @window-graph-paths win-id)]
    (let [watcher-snapshot (graph-path->fs-watcher-snapshot-path dir)]
      (watcher/writeSnapshot dir watcher-snapshot watcher-options)
      (swap! window-graph-paths dissoc win-id))))

(defn close-watcher!
  [win]
  (close-watcher-inner! (.-id win)))


(defn close-all-watcher!
  []
  (doall (map #(.. % -unsubscribe) (vals @window-subscriptions)))
  (->> (vals @window-graph-paths)
       distinct
       (map #(let [watcher-snapshot (graph-path->fs-watcher-snapshot-path %)]
               (watcher/writeSnapshot % watcher-snapshot watcher-options))))
  (swap! window-subscriptions empty)
  (swap! window-graph-paths empty))

