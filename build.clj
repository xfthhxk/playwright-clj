(ns build
  (:require
   [clojure.tools.build.api :as b]
   [deps-deploy.deps-deploy :as dd]
   [clojure.pprint :as pprint]))

(def lib 'xfthhxk/playwright-clj)
(def version (format "0.1.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def clj-src-dirs ["src/clj"])

(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn show-defaults
  [_]
  (println "default-basis:")
  (pprint/pprint @basis)
  (println "target:" "target")
  (println "class-dir:" class-dir)
  (println "jar-file:" jar-file))

(defn clean
  [_]
  (b/delete {:path "target"}))

(defn compile-clj
  [_]
  (println "Compiling clj...")
  (b/compile-clj {:src-dirs clj-src-dirs
                  :class-dir class-dir
                  :basis @basis
                  :java-opts []}))


(defn- pom-template
  [version]
  [[:description "A Clojure wrapper for the Playwright browser automation tool"]
   [:url "https://github.com/xfthhxk/playwright-clj"]
   [:licenses
    [:license
     [:name "MIT License"]
     [:url "https://opensource.org/license/mit/"]]]
   [:developers
    [:developer
     [:name "Amar Mehta"]]]
   [:scm
    [:url "https://github.com/xfthhxk/playwright-clj"]
    [:connection "scm:git:https://github.com/xfthhxk/playwright-clj.git"]
    [:developerConnection "scm:git:ssh:git@github.com:xfthhxk/playwright-clj.git"]
    [:tag (str "v" version)]]])

(defn jar
  [_]
  (clean nil)
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs clj-src-dirs
                :pom-data (pom-template version)})
  (b/copy-dir {:src-dirs (conj clj-src-dirs "resources")
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn install
  [_]
  (jar nil)
  (println "Installing jar into local Maven repo cache...")
  (b/install {:lib lib
              :version version
              :basis @basis
              :class-dir class-dir
              :jar-file jar-file}))

(defn deploy
  [_]
  (dd/deploy {:installer :remote
              :artifact (b/resolve-path jar-file)
              :pom-file (b/pom-path {:lib lib
                                     :class-dir class-dir})}))
