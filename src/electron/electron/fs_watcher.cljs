(ns electron.fs-watcher
  (:require [cljs-bean.core :as bean]
            [electron.utils :as utils]
            ["fs" :as fs]
            ["@parcel/watcher" :as watcher]))

(defonce file-watcher (atom nil))

(defonce file-watcher-chan "file-watcher")
(defn- send-file-watcher! [^js win type payload]
  (when-not (.isDestroyed win)
    (prn :sed payload)
    (.. win -webContents
        (send file-watcher-chan
              (bean/->js {:type type :payload payload})))))

(defn- publish-file-event!
  [win dir path event]
  (send-file-watcher! win event {:dir (utils/fix-win-path! dir)
                                 :path (utils/fix-win-path! path)
                                 :content (utils/read-file path)
                                 :stat (fs/statSync path)}))

(defn watch-dir!
  [win dir]
  (when (fs/existsSync dir)
    (let [subscription (.subscribe watcher dir (fn [err events]
                                                 (if (not err)
                                                   (->> events
                                                        (filter #(not (utils/ignored-path? dir (.-path %))))
                                                        (map #(let [type (.-type %)
                                                                    path (.-path %)]
                                                                (case type
                                                                  "create" (publish-file-event! win dir path "add")
                                                                  "update" (publish-file-event! win dir path "change")
                                                                  "delete" (send-file-watcher! win "unlink"
                                                                                               {:dir (utils/fix-win-path! dir)
                                                                                                :path (utils/fix-win-path! path)})
                                                                  (js/console.error "unknown watcher event:" %))))
                                                        doall)
                                                   (js/console.error err)))
                                   (clj->js {:ignore [".git", "logseq/bak"]}))])))

(defn close-watcher!
  []
  (when-let [watcher @file-watcher]
    (.close watcher)))
