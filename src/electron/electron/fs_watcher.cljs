(ns electron.fs-watcher
  (:require [cljs-bean.core :as bean]
            [electron.utils :as utils]
            [electron.configs :as configs]
            [clojure.string :as string]
            [promesa.core :as p]
            ["fs-extra" :as fse]
            ["fs" :as fs]
            ["path" :as path]
            ["@parcel/watcher" :as watcher]))

(defonce watcher-cache-path configs/watcher-snapshot-root)

;; window -> current subscription
(defonce window-subscriptions (atom {}))
(defonce window-graph-path (atom {}))

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
              (bean/->js {:type type :payload payload})))))

(defn- safe-statSync
  [path]
  (try
    (fs/statSync path)
    (catch js/Object e
      (.log js/console e))))

(defn- publish-file-event!
  [win dir path event]
  (send-file-watcher! win event {:dir (utils/fix-win-path! dir)
                                 :path (utils/fix-win-path! path)
                                 :content (utils/read-file path)
                                 :stat nil }))
                                ;; (safe-statSync path)}))

(defn watch-dir!
  [win dir]
  (when (fs/existsSync dir)

    (p/let [watcher-snapshot (graph-path->fs-watcher-snapshot-path dir)
            _ (prn :snapshot watcher-snapshot)
            _ (fse/ensureFile watcher-snapshot)
            events-callback (fn [^js events]
                              (doseq [event (->> events
                                                 (filter #(not (utils/ignored-path? dir (.-path %)))))]
                                (let [type (.-type event)
                                      path (.-path event)]
                                  (prn :path path)
                                  (case type
                                    "create" (publish-file-event! win dir path "add")
                                    "update" (publish-file-event! win dir path "change")
                                    "delete" (send-file-watcher! win "unlink"
                                                                 {:dir (utils/fix-win-path! dir)
                                                                  :path (utils/fix-win-path! path)})
                                    (js/console.error "unknown watcher event:" event)))))
            opts (clj->js {:ignore [".git", "logseq/bak"]})
            old-events (watcher/getEventsSince dir watcher-snapshot opts)
            _ (events-callback old-events)
            subscription (watcher/subscribe dir (fn [err events]
                                                  (if (not err)
                                                    (events-callback events)
                                                    (js/console.error err)))
                                            opts)]
      (swap! window-subscriptions assoc win subscription)
      (swap! window-graph-path assoc win dir))))

(defn close-watcher!
  [win]
  (when-let [sub (get @window-subscriptions win)]
    (.. sub -unsubscribe)
    (swap! window-subscriptions dissoc win))
  (when-let [dir (get @window-graph-path win)]
    (.writeSnapshot watcher dir)
    (swap! window-graph-path dissoc win)))

(defn close-all-watcher!
  []
  (doall (map #(.. % -unsubscribe) (vals @window-subscriptions)))
  (swap! window-subscriptions empty))

