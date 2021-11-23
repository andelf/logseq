(ns e2e.core
  "Basic operations"
  (:require
   [cljs.core.async :refer [go]]
   [cljs.core.async.interop :refer-macros [<p!]]
   [promesa.core :as p]
   ["playwright" :rename {_electron electron}]
   ["@playwright/test" :refer [test, expect]]))

(defonce electron-app (atom nil))
(defonce page (atom nil))

(defn rand-str [len]
  (apply str (take len (repeatedly #(char (+ (rand 26) 65))))))

(.beforeAll test
            (fn []
              (p/let [app (.
                           electron
                           launch
                           (clj->js {:cwd "./static"
                                     :args ["electron.js"]}))
                      app-path (. app evaluate (fn [args]
                                                 (.. args -app getAppPath)))]
                (js/console.log "app-path:" app-path)
                (reset! electron-app app))))

(.afterAll test
           (fn []
             (p/do!
              (. @page close)
              ;; (.. @electron-app close)
              )))

(test "render app"
      (fn []
        (p/let [app-page (.. @electron-app firstWindow)
                _ (reset! page app-page)
                _ (.. @page (waitForLoadState "domcontentloaded"))
                _ (.. @page (waitForFunction "window.document.title != \"Loading\""))
                title (.. @page title)]
          (-> title (expect) (.toMatch #"^Logseq.*?"))
          ;; (js/console.log "tilte" title)
          ;; (.. @page (title) (expect) (.toMatch #"^Logseq.*?"))
          ;;     (js/console.log @electron-app)
          ;;(js/console.log @page)
          )))

(test "open sidebar"
      (fn []
        (p/do!
         (. @page (waitForTimeout 4000))
         (p/let [sidebar-visible? (.. @page (isVisible "#sidebar-nav-wrapper .left-sidebar-inner"))]
           (when (not sidebar-visible?)
             (p/do!
              (. @page (click "#left-menu.button"))
              (. @page (waitForTimeout 1000)))))

         (p/let [sidebar-visible? (.. @page (isVisible "#sidebar-nav-wrapper .left-sidebar-inner"))]
           (->  (expect sidebar-visible?) (.toBe true))))))

(test "create new page"
      (fn []
        (p/do!
         (. @page (click "#sidebar-nav-wrapper a:has-text(\"New page\")"))
         (. @page (fill "[placeholder=\"Search or create page\"]" (rand-str 10)))
         (. @page (click "text=/.*New page: \".*/"))
         (. @page (waitForTimeout 2000))

         (p/let [blocks (.$$ @page ".ls-block")]
           (-> (expect blocks) (.toHaveLength 1))))))

(defn main
  []
  (js/console.log (clj->js {:cwd "./static" :args ["electron.js"]}))
  (js/console.log "Hello World!"))
