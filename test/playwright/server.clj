(ns playwright.server
  (:require
   [clj-simple-router.core :as router]
   [ring.adapter.jetty9 :as jetty]
   [ring.middleware.defaults :as ring.defaults]
   [ring.util.anti-forgery :as anti-forgery]
   [hiccup.page :as page])
  (:import
   (org.eclipse.jetty.server Server)))

(defonce ^:dynamic *instance* nil)
(defonce ^:dynamic *port* nil)

(defn render-page
  [f request]
  (let [{:keys [page/status page/title page/body]} (f request)]
    {:status status
     :headers {"content-type" "text/html"}
     :body
     (page/html5 {:lang "en"}
       [:head
        [:meta {:charset "utf-8"}]
        [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
        [:title title]]
       [:body body])}))

(defn login-page
  [_req & {:keys [page/status error?]}]
  {:page/status (or status 200)
   :page/title "Login"
   :page/body
   [:div
    [:h2 "Login"]
    (when error?
      [:p.notice "Oops that's not right.."])
    [:div
     [:form {:method :post :action "/"}
      (anti-forgery/anti-forgery-field)
      [:p
       [:input {:type "text" :placeholder "Username" :id "username" :name  "login/username" :data-testid "username"}]]
      [:p
       [:input {:type "password" :placeholder "Password" :id "password" :name "login/password"}]]
      [:p
       [:button {:type "submit" :id "login-button" :name "login"} "Login"]]]]
    [:div
     "NB. username picard@starfleet.org and password is enterprise"]]})

(defn authenticate
  [req]
  (if (= {:login/username "picard@starfleet.org"
          :login/password "enterprise"}
         (select-keys (:form-params req) [:login/username :login/password]))
    {:page/status 200
     :page/title "Main"
     :page/body [:span.greeting "Welcome Jean-Luc!"]}
    (login-page req :page/status 403 :error? true)))

(defn wrap-keywordize-params
  [handler]
  (fn [request]
    (-> request
        (update :form-params update-keys keyword)
        (update :query-params update-keys keyword)
        handler)))


(def routes
  (-> {"GET /" (fn [req] (render-page #'login-page req))
       "POST /" (fn [req] (render-page #'authenticate req))}
      router/router
      wrap-keywordize-params
      (ring.defaults/wrap-defaults ring.defaults/site-defaults)))


(defn stop!
  []
  (try
    (alter-var-root #'*instance* (fn [^Server s] (some-> s .stop) nil))
    (alter-var-root #'*port* (constantly nil))
    (catch InterruptedException _)))

(defn start!
  []
  (let [opts {:host "0.0.0.0"
              :port 0 ; random port
              :join? false}]
    (alter-var-root #'*instance* (constantly (jetty/run-jetty #'routes opts)))
    (alter-var-root #'*port* (constantly (-> *instance*
                                             .getConnectors
                                             first
                                             .getLocalPort)))))

(defn restart!
  []
  (stop!)
  (start!))

(comment
  (restart!)
  )
