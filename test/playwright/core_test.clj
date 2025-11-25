(ns playwright.core-test
  (:require
   [playwright.core :as pw :refer [is with-open-page with-open-page-debug]]
   [playwright.server :as server]
   [clojure.test :refer [deftest]]
   [expectations.clojure.test :refer [expect]]))


(defn setup
  []
  (println "test setup")
  (server/start!)
  (pw/launch! :playwright/headless? true)
  (pw/set-browser-context-options! {:base-url (str "http://localhost:" server/*port*)}))


(defn teardown
  []
  (println "test teardown")
  (pw/dispose!)
  (server/stop!))


(defn kaocha-pre-hook!
  [config]
  (println "Koacha pre hook")
  (setup)
  config)


(defn kaocha-post-hook!
  [result]
  (println "Koacha post hook")
  (teardown)
  result)

(def username-field "input[name='login/username']")
(def password-field "input[name='login/password']")
(def login-button "button[name='login']")

(def username-value "picard@starfleet.org")
(def password-value "enterprise")

(deftest login-test
  (with-open-page "/"
    (is :page/title "Login")
    (is username-field :id "username")
    (is username-field :attribute "name" "login/username")
    (is username-field :attribute :name "login/username") ;; attr can be a keyword
    (is login-button :role "button")

    (pw/fill username-field username-value)
    (pw/fill password-field password-value)
    (pw/click login-button)

    (is :page/title "Main")
    (is "span.greeting" :attached)
    (is "span.greeting" :text "Welcome Jean-Luc!")))

(deftest verbose-login-test
  (with-open [pg (pw/->page)]
    (pw/navigate pg "/")
    (expect true (pw/assert-title pg "Login"))

    (pw/fill (pw/locator pg username-field) username-value)
    (pw/fill (pw/locator pg password-field) password-value)
    (pw/click (pw/get-by-role pg :button {:name "Login"}))

    (expect true (pw/assert-title pg "Main"))
    (expect true (pw/assert-attached (pw/locator pg "span.greeting")))
    (expect true (pw/assert-text (pw/locator pg "span.greeting") "Welcome Jean-Luc!"))))


(deftest attributes-test
  (with-open-page "/"
    (expect {:data-testid "username"
             :id "username"
             :name "login/username"
             :placeholder "Username"
             :type "text"}
            (pw/attributes username-field))))

(comment
  (setup)

  ;; debugging example for repl
  ;; pw/*page* will REMAIN bound
  (with-open-page-debug "/"
    (pw/fill username-field username-value)
    (pw/fill password-field password-value)
    (pw/click login-button)
    (is :page/title "Main")
    (is "span.greeting" :attached)
    (is "span.greeting" :text "Welcome Jean-Luc!"))

  ;; done debugging, clean up
  (pw/end-open-page-debug!)

  )
