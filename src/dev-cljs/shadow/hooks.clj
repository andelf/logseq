(ns shadow.hooks
  (:require [clojure.java.shell :refer [sh]]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; copied from https://gist.github.com/mhuebert/ba885b5e4f07923e21d1dc4642e2f182
(defn exec [& cmd]
  (let [cmd (str/split (str/join " " (flatten cmd)) #"\s+")
        _ (println (str/join " " cmd))
        {:keys [exit out err]} (apply sh cmd)]
    (if (zero? exit)
      (when-not (str/blank? out)
        (println out))
      (println err))))

(defn purge-css
  {:shadow.build/stage :flush}
  [state {:keys [css-source
                 js-globs
                 public-dir]}]
  (case (:shadow.build/mode state)
    :release
    (exec "purgecss --css " css-source
          (for [content (if (string? js-globs) [js-globs] js-globs)]
            (str "--content " content))
          "-o" public-dir)

    :dev
    (do
      (exec "mkdir -p" public-dir)
      (exec "cp" css-source (str public-dir "/" (last (str/split css-source #"/"))))))
  state)


(defn rsync
  [from-dir to-dir]
  (let [from-dir (if (str/ends-with? from-dir "/") from-dir (str from-dir "/"))
        to-dir (if (str/ends-with? to-dir "/") to-dir (str to-dir "/"))
        {:keys [exit out err]} (apply sh "rsync" "-avz" "--delete" from-dir to-dir)]
    (when-not (zero? exit)
      (println err))))


(defn copy-to-dir
  {:shadow.build/stage :flush}
  [state dst-dir]
  (let [src-dir (get-in state [:shadow.build/config :output-dir])]
    (prn (sh "mkdir" "-p" dst-dir))
    (sh "sh" "-c" (str "cp -rv " src-dir "/* '" dst-dir "'")))
  (prn "copied!")
  state)


(defn fix-resource-config
  {:shadow.build/stage :configure}
  [build-state]
  (prn :shadow.build/config (:shadow.build/config build-state))
  (prn (keys build-state))
  (prn (:previously-compiled build-state))
  (prn (:devtools build-state))

                                        ;; (update-in build-state [:shadow.build/config] #())
  build-state)

(defn demo
  {:shadow.build/stage :flush}
;  {::build/stages #{:configure :flush}}
  [state & args]
  (let [output-dir (get-in state [:shadow.build/config :output-dir])
        ;;build-sources (:build-sources state)
        modules (:shadow.build.closure/modules state)]
    (prn :config output-dir)
    (prn :modules modules)
    ; (prn :souce state)
;    (copy-dir output-dir "./fuck")
    )
  (println ::flush (keys state))
  state)
