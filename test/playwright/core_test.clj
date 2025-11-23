(ns playwright.core-test
  (:require
   [playwright.core :as pw :refer [with-open-page with-open-page-debug]]
   [playwright.server :as server]
   [clojure.test :refer [deftest is]]
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

(deftest login-with-open-page-test
  (with-open-page "/"
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

    (pw/click "button[name='login']")

    (expect true (pw/assert-title "Main"))
    (expect true (pw/assert-attached "span.greeting"))
    (expect true (pw/assert-text "span.greeting" "Welcome Jean-Luc!"))))

(deftest login-with-open-page-asserts-test
  (with-open-page "/"
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

    (pw/click "button[name='login']")

    (pw/assert-page :title "Main")
    (pw/assert "span.greeting" :attached)
    (pw/assert "span.greeting" :text "Welcome Jean-Luc!")))

(deftest login-with-open-page-is-test
  (with-open-page "/"
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

    (pw/click "button[name='login']")

    (is (playwright/page :title "Main"))
    (is (playwright/locator "span.greeting" :attached))
    (is (playwright/locator "span.greeting" :text "Welcome Jean-Luc!"))))

(comment
  (setup)

  ;; example of debugging
  (with-open-page-debug "/"
    (expect {:data-testid "username"
             :id "username"
             :name "login/username"
             :placeholder "Username"
             :type "text"}
            (pw/attributes "input[name='login/username']"))

    (pw/fill "input[name='login/username']"
             "picard@starfleet.org")

    (pw/fill "input[name='login/password']"
             "enterprise"))

  ;; *page* is still bound so can debug
  (pw/attributes "input[type='password']")

  (pw/assert-page :title "Main")

  (pw/attributes "button")

  ;; cleanup
  (pw/end-open-page-debug!)

  ;; done with example
  )

(comment
  (pw/expect "Main" :id "input[type='password']" )

  (expect 1 2)

  (pw/assert-page :title "Login")

  (pw/assert-page :title "Main")

  (pw/assert-page :url "Main")
  (pw/assert "input[type='password']" :id "hello")
  (pw/assert "span.greeting" :attached)
  (pw/assert :role )
  )
