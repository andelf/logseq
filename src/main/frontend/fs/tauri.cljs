(ns frontend.fs.tauri
  (:require [frontend.fs.protocol :as protocol]
            [goog.object :as gobj]
            [promesa.core :as p]
            [clojure.string :as string]
            ["@tauri-apps/api/fs" :as fs]
            ["@tauri-apps/api/dialog" :as dialog]
            [frontend.util :as util]))



(defn- <readdir [dir]
  (p/let [entries (p/chain (.readDir fs dir (clj->js {:recursive true}))
                           #(js->clj % :keywordize-keys true))
          flatten-entries (loop [entries entries
                                 result []]
                            (if (seq entries)
                              (let [entry (first entries)
                                    name (:name entry)
                                    path (:path entry)]
                                (cond
                                  (or (string/starts-with? name ".")
                                      (contains? #{"node_modules" "bak" "version-files"} name))
                                  (recur (rest entries) result)

                                  (seq (:children entry))
                                  (recur (concat (:children entry) (rest entries)) result)
                                  
                                  :else ;; FXIME: src/electron/electron/utils.cljs
                                  (if (contains? #{"md" "markdown" "org" "js" "edn" "css"} (util/get-file-ext name))
                                    (recur (rest entries) (conj result {:path path :name name}))
                                    (recur (rest entries) result))))

                              result))
          read-out (p/all (map (fn [{:keys [path] :as ent}]
                                 (p/chain (.readTextFile fs path)
                                          #(assoc ent :content % :size (count %))))
                               flatten-entries))]
    read-out))

(defrecord Tauri []
  protocol/Fs
  (available? [_]
    (boolean (and js/window (gobj/get js/window "__TAURI__"))))
  (backend-name [_]
    "tauri")
  (mkdir! [_this dir]
    (prn ::mkdir! dir)
    (.createDir fs dir (clj->js {:recursive true})))
  (readdir [_this dir]
    (prn ::readdir dir)
    (<readdir dir))
  (unlink! [_this _repo path opts]
    (prn ::unlink! path opts))
  (rmdir! [_this dir]
    (prn ::rmdir! dir))
  (read-file [_this dir path options]
    (prn ::read-file dir path options))
  (write-file! [_this _repo dir path content _opts]
    (prn ::write-file! dir path content))
  (rename! [_this _repo old-path new-path]
    (prn ::rename! old-path new-path))
  (stat [_this dir path]
    (prn ::stat dir path))
  (open-dir [_this _ok-handler]
    (p/let [graph-path (.open dialog (clj->js {:directory true
                                               :multiple false
                                               :recursive true
                                               :title "Select a directory"}))
            files (<readdir graph-path)]
      (prn ::open-dir-reading graph-path (count files))
      [graph-path files]))


  (get-files [_this _path-or-handle _ok-handler]
    (prn ::get-files))
  (watch-dir! [_this dir]
    (prn ::watch-dir! dir))
  (unwatch-dir! [_this dir]
    (prn ::unwatch-dir! dir)))
