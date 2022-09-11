#!/usr/bin/env bb
(ns quick-preview
  "Script for previewing SBI HTML site.
  Based loosely on https://gist.github.com/holyjak/36c6284c047ffb7573e8a34399de27d8"
  (:require [babashka.deps :as deps]
            [babashka.fs :as fs]
            [clojure.java.browse :as browse]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [org.httpkit.server :as server]))


(deps/add-deps '{:deps {org.babashka/spec.alpha {:git/url "https://github.com/babashka/spec.alpha"
                                                 :sha "1a841c4cc1d4f6dab7505a98ed2d532dd9d56b78"}
                        cli-matic/cli-matic {:mvn/version "0.5.4"}}})
(require '[cli-matic.core :refer [run-cmd]])


(def example-html-path
  (fs/path (System/getProperty "user.home") "Dropbox/Shared SBI/HGME UYOH/christmas-greetings.html"))


(comment
  ;; Put this inside a comment otherwise script will fail when run on someone else's computer
  (def example-html-string
    (slurp (fs/file example-html-path))))


(def example-image-path "/Users/tobiaslocsei/Dropbox/Shared SBI/HGME UYOH/image-files/christmas-greetings-mood-image-800x534.jpg")
(def example-html-folder "/Users/tobiaslocsei/Dropbox/Shared SBI/HGME UYOH/")


(defn list-zz-includes
  "Return a list of zz include file names referenced in a html file"
  [html-string]
  (->> (re-seq #"\*\*\*(zz-.*?\.shtml)" html-string)
       (map second ,,,)
       (distinct ,,,)))
(comment
  (list-zz-includes example-html-string))


(defn multi-replace
  "Make multiple string replacements"
  [s match-and-replacement-pairs]
  (reduce (fn [s [match replacement]] (str/replace s match replacement))
          s
          match-and-replacement-pairs))
(comment
  (multi-replace "foo bar baz"
                 [["foo" "FOO"]
                  ["baz" "BAZ"]]) ; => "FOO bar BAZ"
  :_)


(defn expand-includes
  "Given a path to an HTML file, expand out all the zz includes in it"
  [html-file-path]
  (let [parent-dir (fs/parent html-file-path)
        unexpanded-html (slurp (fs/file html-file-path))
        zz-include-file-names (list-zz-includes unexpanded-html)
        match-replacement-pairs (for [zz-file-name zz-include-file-names]
                                  [(str "***" zz-file-name "***")
                                   (slurp (fs/file (fs/path parent-dir zz-file-name)))])
        expanded-html (multi-replace unexpanded-html match-replacement-pairs)]
    expanded-html))
(comment
  (println (expand-includes example-html-path))
  :_)


(defn html?
  [uri]
  (str/ends-with? uri ".html"))
(comment
  (html? "foo.html") ; => true
  (html? "images/bar.png") ; => false
  :_)


(defn make-app
  [html-file-path]
  (let [html-folder (fs/parent html-file-path)]
    (fn [{:keys [uri]}]
      (if (html? uri)
        {:headers {"Content-Type" "text/html"}
         :body (expand-includes (fs/path html-folder (subs uri 1)))}
        {:body (fs/file (fs/path html-folder (subs uri 1)))}))))


(defonce server (atom nil))


(defn stop-server []
  (when-not (nil? @server)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    ;; :timeout is optional, when no timeout, stop immediately
    (@server :timeout 100)
    (reset! server nil)))
(comment (stop-server))


(defn start-server
  [{:keys [html-file-path port]}]
  (stop-server)
  (reset! server
          (server/run-server (make-app html-file-path) {:port port}))
  (let [url (str "http://localhost:" port "/" (fs/file-name html-file-path))]
    (println "See page preview at:")
    (println url)
    (browse/browse-url url)))
(comment
  (start-server {:html-file-path example-html-path
                 :port 3002})
  :_)


(defn cli-handler
  [{:keys [html port]}]
  (start-server {:html-file-path html
                 :port port})
  @(promise))


(def CONFIGURATION
  {:command "quick_preview.clj"
   :description "Start a local server to enable preview of SBI HTML site"
   :examples ["quick_preview.cj --html path/to/my-file.html"]
   :opts [{:as "HTML file path"
           :option "html"
           :short "h"
           :type :string
           :default :present}
          {:as "Port"
           :option "port"
           :short "p"
           :type :int
           :default 3001}]
   :runs cli-handler})


(defn -main []
  (run-cmd *command-line-args* CONFIGURATION))


(when (= *file* (System/getProperty "babashka.file"))
  (-main))



;; Copy script to /bin folder
(comment
  (shell/sh
    "cp"
    (str (System/getProperty "user.dir") "/src/quick_preview.clj")
    "/Users/tobiaslocsei/Dropbox/Tobs documents/Programming/bin")
  :_)