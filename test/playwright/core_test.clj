(ns playwright.core-test
  (:require
   [playwright.core :as pw]
   [playwright.server :as server]
   [clojure.test :refer [deftest]]
   [expectations.clojure.test :refer [expect use-fixtures]]))


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


(deftest login-test
  (with-open [pg (pw/->page)]
    (pw/navigate pg "/")

    (expect {:data-testid "username"
             :id "username"
             :name "login/username"
             :placeholder "Username"
             :type "text"}
            (-> pg
                (pw/locator "input[name='login/username']")
                pw/attributes))


    (pw/fill (pw/locator pg "input[name='login/username']")
             "picard@starfleet.org")

    (pw/fill (pw/locator pg "input[name='login/password']")
             "enterprise")

    (pw/click (pw/get-by-role pg :button {:name "Login"})
              :force? true)

    (expect true (pw/assert-title pg "Main"))
    (expect true (pw/assert-attached (pw/locator pg "span.greeting")))
    (expect true (pw/assert-text (pw/locator pg "span.greeting") "Welcome Jean-Luc!"))))

(deftest login-with-page-binding-test
  (with-open [pg (pw/->page)]
    (binding [pw/*page* pg]
      (pw/navigate "/")

      (expect {:data-testid "username"
               :id "username"
               :name "login/username"
               :placeholder "Username"
               :type "text"}
              (pw/attributes "input[name='login/username']"))


      (pw/fill "input[name='login/username']"
               "picard@starfleet.org")

      (pw/fill "input[name='login/password']"
               "enterprise")

      (pw/click "button[name='login']" :force? true)

      (expect true (pw/assert-title "Main"))
      (expect true (pw/assert-attached "span.greeting"))
      (expect true (pw/assert-text "span.greeting" "Welcome Jean-Luc!")))))


(comment
  (setup)
  )
