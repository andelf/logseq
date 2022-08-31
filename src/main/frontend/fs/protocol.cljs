(ns frontend.fs.protocol
  ;; namespace local config to suppress 'new-path' of 'rename!'. clj-kondo's bug?
  {:clj-kondo/config {:linters {:private-call {:level :off}}}})

(defprotocol Fs
  (available? [this] "Is current backend avaliable?")
  (backend-name [this] "Human readable backend fs implementation name")
  (mkdir! [this dir])
  (mkdir-recur! [this dir])
  (readdir [this dir])
  (unlink! [this repo path opts])
  (rmdir! [this dir])
  (read-file [this dir path opts])
  (write-file! [this repo dir path content opts])
  (rename! [this repo old-path new-path])
  (copy! [this repo old-path new-path])
  (stat [this dir path])
  (open-dir [this ok-handler] "Folder picker")
  ;; [{:path _ :content _ :stat _}]
  (get-files [this path-or-handle ok-handler])
  (watch-dir! [this dir])
  (unwatch-dir! [this dir])
  ;; Ensure the dir is watched, window agnostic.
  ;; Implementation should handle the actual watcher's construction / destruction.
  ;; So shouldn't consider `unwatch-dir!`.
  )
