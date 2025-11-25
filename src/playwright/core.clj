(ns playwright.core
  "Playwright wrapper."
  (:refer-clojure :exclude [count assert])
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure.test :as test])
  (:import
   (java.net URI)
   (java.nio.file Path Paths)
   (java.util ArrayList Collection)
   (java.util.regex Pattern)
   (com.microsoft.playwright
    Browser
    Browser$NewContextOptions
    BrowserContext
    BrowserType
    BrowserType$LaunchOptions
    CLI
    FrameLocator
    FrameLocator$GetByRoleOptions
    Locator
    Locator$ClickOptions
    Locator$GetByRoleOptions
    Locator$WaitForOptions
    Locator$ScreenshotOptions
    Page
    Page$GetByRoleOptions
    Page$ScreenshotOptions
    Playwright)
   (com.microsoft.playwright.assertions PlaywrightAssertions LocatorAssertions)
   (com.microsoft.playwright.options AriaRole SelectOption WaitForSelectorState)
   (org.opentest4j AssertionFailedError)))

(set! *warn-on-reflection* true)


(defn- path
  ^Path [path]
  (cond
    (instance? Path path) path
    (instance? URI path) (Paths/get ^URI path)
    :else (.toPath (io/file path))))


(defn install!
  "Installs OS deps and browsers"
  ([]
   (install! {}))
  ([_m]
   (CLI/main (into-array ["install" "--with-deps"]))))


(def ^:private empty-store
  {:playwright/instance nil
   :playwright/browser nil})


(defonce ^:dynamic *store* (atom empty-store))

(defonce ^{:dynamic true :tag Page} *page* nil)


(defn launch!
  [& {:keys [playwright/headless? playwright/slow-mo browser/type]
      :or {headless? true
           type :firefox}}]
  (let [opts (cond-> (BrowserType$LaunchOptions/new)
               true (.setHeadless headless?)
               slow-mo (.setSlowMo slow-mo))
        playwright (Playwright/create)
        browser-type-fn (case type
                          :chromium Playwright/.chromium
                          :webkit Playwright/.webkit
                          Playwright/.firefox)
        ^BrowserType bt (browser-type-fn playwright)
        ^Browser browser (.launch bt opts)]

    (reset! *store* {:playwright/instance playwright
                     :playwright/browser browser})))


(defn close!
  [^java.lang.AutoCloseable c]
  (some-> c .close))

(defn dispose!
  []
  (let [{:keys [playwright/instance
                playwright/browser]} @*store*]
    (close! *page*)
    (close! browser)
    (close! instance)
    (reset! *store* empty-store)))


(defn browser
  []
  (:playwright/browser @*store*))

(defn set-browser-context-options!
  [opts]
  (swap! *store* assoc :playwright/browser-context-options opts))

(defn ->browser-context
  ([]
   (->browser-context (:playwright/browser-context-options @*store*)))
  ([opts]
   (->browser-context (browser) opts))
  ([^Browser b {:keys [accept-downloads? base-url bypass-csp? ignore-https-errors? mobile? locale] :as _opts}]
   (.newContext b (cond->  (Browser$NewContextOptions/new)
                    (boolean? accept-downloads?) (.setAcceptDownloads accept-downloads?)
                    (boolean? bypass-csp?) (.setBypassCSP bypass-csp?)
                    (boolean? ignore-https-errors?) (.setIgnoreHTTPSErrors ignore-https-errors?)
                    (boolean? mobile?) (.setIsMobile mobile?)
                    base-url (.setBaseURL base-url)
                    locale (.setLocale locale)))))


(defn ->page
  (^Page []
   (->page (->browser-context)))
  (^Page [^BrowserContext bc]
   (.newPage bc)))


(defn context
  ([] (context *page*))
  ([^Page p]
   (.context p)))

(defn navigate
  ([url] (navigate *page* url))
  ([^Page p ^String url]
   (.navigate p url)))

(defn frames
  ([] (frames *page*))
  ([^Page p]
   (.frames p)))

(defn main-frame
  ([] (main-frame *page*))
  ([^Page p]
   (.mainFrame p)))

(defn go-back
  ([] (go-back *page*))
  ([^Page p]
   (.goBack p)))


(defn go-forward
  ([] (go-forward *page*))
  ([^Page p]
   (.goForward p)))

(defn reload
  ([] (reload *page*))
  ([^Page p]
   (.reload p)))

(defn title
  ([] (title *page*))
  ([^Page p]
   (.title p)))

(defn url
  ([] (url *page*))
  ([^Page p]
   (.url p)))


(defn viewport-size
  ([] (viewport-size *page*))
  ([^Page p]
   (when-let [x (.viewportSize p)]
     {:height (.-height x)
      :width (.-width x)})))


(defn set-default-navigation-timeout!
  "A zero `timeout-ms` disables timeouts."
  ([timeout-ms]
   (set-default-navigation-timeout! *page* timeout-ms))
  ([^Page p timeout-ms]
   (.setDefaultNavigationTimeout p timeout-ms)))

(defn set-default-timeout!
  "NB `set-default-navigation-timeout!` takes precedence over this one.
  A zero `timeout-ms` disables timeouts."
  ([timeout-ms]
   (set-default-timeout! *page* timeout-ms))
  ([^Page p timeout-ms]
   (.setDefaultTimeout p timeout-ms)))


(defn page?
  [x]
  (instance? Page x))

(defn locator?
  [x]
  (instance? Locator x))

(defn as-aria-role
  ^AriaRole [x]
  (if (instance? AriaRole x)
    x
    (-> x name str/upper-case AriaRole/valueOf)))

(defprotocol ILocator
  "Common fns that apply to Page and Locator.
  NB with `get-by-role` the `:name` value must be visible to a user. ie
  an input with a name attribute 'user-name' won't be located and a timeout
  will occur. You could try the selector input[name='user-name']"
  (get-by-alt-text [this s])
  (get-by-label [this s])
  (get-by-placeholder [this s])
  (get-by-role [this s opts])
  (get-by-test-id [this s])
  (get-by-text [this s])
  (get-by-title [this s])
  (frame-locator [this s])
  (locator [this s]))


(defmacro ^:private deflocator
  [the-type role-opts-type]
  (let [role-opts-type-new (-> role-opts-type
                               name
                               (str "/new")
                               symbol)]
    `(extend-type ~the-type
       ILocator
       (get-by-alt-text [this# s#]
         (if (string? s#)
           (.getByAltText this# ^String s#)
           (.getByAltText this# ^Pattern s#)))

       (get-by-label [this# s#]
         (if (string? s#)
           (.getByLabel this# ^String s#)
           (.getByLabel this# ^Pattern s#)))

       (get-by-placeholder [this# s#]
         (if (string? s#)
           (.getByPlaceholder this# ^String s#)
           (.getByPlaceholder this# ^Pattern s#)))

       (get-by-role [this# s# opts#]
         (let [checked?# (:checked opts#)
               disabled?# (:disabled? opts#)
               exact?# (:exact? opts#)
               expanded?# (:expanded? opts#)
               include-hidden?# (:include-hidden? opts#)
               level# (:level opts#)
               nm# (:name opts#)
               pressed?# (:pressed? opts#)
               selected?# (:selected? opts#)
               ^AriaRole role# (as-aria-role s#)
               ^{:tag ~role-opts-type} role-opts# (~role-opts-type-new)
               role-opts# (cond-> role-opts#
                            (boolean? checked?#) (.setChecked checked?#)
                            (boolean? disabled?#) (.setDisabled disabled?#)
                            (boolean? exact?#) (.setExact exact?#)
                            (boolean? expanded?#) (.setExpanded expanded?#)
                            (boolean? include-hidden?#) (.setIncludeHidden include-hidden?#)
                            level# (.setLevel level#)
                            (string? nm#) (.setName ^String (str/upper-case nm#))
                            (instance? Pattern nm#) (.setName ^Pattern nm#)
                            (boolean? pressed?#) (.setPressed pressed?#)
                            (boolean? selected?#) (.setSelected selected?#))]
           (.getByRole this# role# role-opts#)))

       (get-by-test-id [this# s#]
         (if (string? s#)
           (.getByTestId this# ^String s#)
           (.getByTestId this# ^Pattern s#)))

       (get-by-text [this# s#]
         (if (string? s#)
           (.getByText this# ^String s#)
           (.getByText this# ^Pattern s#)))

       (get-by-title [this# s#]
         (if (string? s#)
           (.getByTitle this# ^String s#)
           (.getByTitle this# ^Pattern s#)))

       (frame-locator [this# s#]
         (.frameLocator this# s#))

       (locator [this# s#]
         (if (string? s#)
           (.locator this# ^String s#)
           (.locator this# ^Locator s#))))))


(deflocator Page Page$GetByRoleOptions)
(deflocator Locator Locator$GetByRoleOptions)
(deflocator FrameLocator FrameLocator$GetByRoleOptions)

(defn all
  [^Locator l]
  (.all l))


(defprotocol IScreenshot
  (screenshot [this] [this opts]))


(defmacro ^:private defscreenshot
  [the-type opts-type]
  (let [opts-type-new (-> opts-type
                          name
                          (str "/new")
                          symbol)]
    `(extend-type ~the-type
       IScreenshot
       (screenshot [this#]
         (.screenshot this#))
       (screenshot [this# opts#]
         (let [file# (:file opts#)
               ^{:tag ~opts-type} sso# (~opts-type-new)
               sso# (cond-> sso#
                      file# (.setPath (path file#)))]
           (.screenshot this# sso#))))))

(defscreenshot Page Page$ScreenshotOptions)
(defscreenshot Locator Locator$ScreenshotOptions)


(defn- as-locator
  ^Locator [locator|s]
  (if (locator? locator|s)
    locator|s
    (locator *page* locator|s)))


(defn blur
  [loc]
  (.blur (as-locator loc)))


(defn check
  [loc]
  (.check (as-locator loc)))


(defn clear
  [loc]
  (.clear (as-locator loc)))


(defn click
  [loc & {:keys [force?]}]
  (.click (as-locator loc)
          (let [co (Locator$ClickOptions/new)]
            (cond-> co
              (some? force?) (.setForce force?)))))


(defn content-frame
  [loc]
  (.contentFrame (as-locator loc)))


(defn count
  [loc]
  (.count (as-locator loc)))

(defn dblclick
  [loc]
  (.dblclick (as-locator loc)))


(defn describe
  [loc description]
  (.describe (as-locator loc) description))

(defn dispatch-event
  [loc event-name]
  (.dispatchEvent (as-locator loc) event-name))


(defn drag-to
  [src tgt]
  (.dragTo (as-locator src) (as-locator tgt)))

(defn evaluate
  [loc js-expr]
  (.evaluate (as-locator loc) js-expr))

(defn evaluate-all
  [loc js-expr]
  (.evaluateAll (as-locator loc) js-expr))

(defn evaluate-handle
  ([loc js-expr]
   (.evaluateHandle (as-locator loc) js-expr))
  ([loc js-expr arg]
   (.evaluateHandle (as-locator loc) js-expr arg)))


(defn fill
  [loc text]
  (.fill (as-locator loc) text))


(defn focus
  [loc text]
  (.focus (as-locator loc) text))


(defn get-attribute
  [loc attr-name]
  (.getAttribute (as-locator loc) attr-name))


(defn highlight
  "Useful for debugging. Don't commit code that uses this fn."
  [loc]
  (.highlight (as-locator loc)))

(defn hover
  [loc]
  (.hover (as-locator loc)))

(defn inner-html
  [loc]
  (.innerHTML (as-locator loc)))

(defn inner-text
  [loc]
  (.innerText (as-locator loc)))

(defn input-value
  [loc]
  (.inputValue (as-locator loc)))


(defn checked?
  [loc]
  (.isChecked (as-locator loc)))


(defn disabled?
  [loc]
  (.isDisabled (as-locator loc)))


(defn editable?
  [loc]
  (.isEditable (as-locator loc)))

(defn enabled?
  [loc]
  (.isEnabled (as-locator loc)))

(defn hidden?
  [loc]
  (.isHidden (as-locator loc)))

(defn visible?
  [loc]
  (.isVisible (as-locator loc)))


(defn page
  [loc]
  (.page (as-locator loc)))


(defn press
  [loc key-name]
  (.press (as-locator loc) key-name))


(defn press-sequentially
  "NB. You should generally prefer `fill`. This should be used
  if there is special keyboard handling."
  [loc text]
  (.pressSequentially (as-locator loc) text))


(defn scroll-into-view-if-needed
  [loc]
  (.scrollIntoViewIfNeeded (as-locator loc)))

(def ensure-viewable scroll-into-view-if-needed)

(defn select-option
  [loc x]
  (let [l (as-locator loc)
        {:keys [^String value
                ^String/1 values
                ^int index
                ^String label]} (cond
                                  (string? x) {:value x}
                                  (int? x) {:index x}
                                  (map? x) x
                                  (= String/1 (class x)) {:values x}
                                  :else (throw (ex-info "Unsupported arg" {:x x})))]
    (cond
      value (.selectOption l value)
      values (.selectOption l values)
      index (.selectOption l (-> (SelectOption/new)
                                 (.setIndex index)))
      label (.selectOption l (-> (SelectOption/new)
                                 (.setLabel label))))))



(defn select-text
  [loc]
  (.selectText (as-locator loc)))

(defn set-checked
  [loc checked?]
  (.setChecked (as-locator loc) checked?))


(defn set-input-files
  [loc xs]
  (.setInputFiles (as-locator loc) ^Path/1 (into-array (map path xs))))


(defn text-content
  "Prefer assertThat to avoid flakiness."
  [loc]
  (.textContent (as-locator loc)))


(defn uncheck
  [loc]
  (.uncheck (as-locator loc)))


(defn wait-for
  [loc]
  (.waitFor (as-locator loc)))

(def ^:private attrs-fn-js
  "(el) => {
  const attrs = el.attributes;
  const m = {};
  for(let i=0; i < attrs.length; i++) {
    m[attrs[i].name] = attrs[i].value;
  }
  return m;
}")


(defn attributes
  [loc]
  (-> (into {} (evaluate (as-locator loc) attrs-fn-js))
      (update-keys keyword)))


(defn ^{:assert/id :page/title} assert-title
  "Takes an `opts` map with key `:not?` to negate the assertion."
  ([^Page p s]
   (assert-title {} p s))
  ([{:keys [not?]} ^Page p s]
   (let [a (cond-> (PlaywrightAssertions/assertThat p)
             not? .not)]
     (if (string? s)
       (.hasTitle a ^String s)
       (.hasTitle a ^Pattern s))
     true)))

(defn ^{:assert/id :page/url} assert-url
  ([s]
   (assert-url {} *page* s))
  ([{:keys [not?]} ^Page p s]
   (let [a (cond-> (PlaywrightAssertions/assertThat p)
             not? .not)]
     (if (string? s)
       (.hasURL a ^String s)
       (.hasURL a ^Pattern s))
     true)))


(defn- ->locator-assertion
  ^LocatorAssertions [{:keys [not?]} loc]
  (cond-> (PlaywrightAssertions/assertThat (as-locator loc))
    not? .not))

;; TODO these assertion fns are repetitive
;; (defmacro defassertion
;;   [id method-sym]
;;   (let [fn-nm (symbol (str "assert-" (name id)))]
;;     `(defn ~(vary-meta fn-nm assoc :assert/id id)
;;        ([loc# x#]
;;         (~fn-nm {} loc# x#))
;;        ([opts# loc# ^String s#]
;;         (~method-sym (->locator-assertion opts# loc#) s#)))))


;; (macroexpand-1 '(defassertion :id LocatorAssertions/.hasId))

(defn ^{:assert/id :contains-class} assert-contains-class
  ([loc s]
   (assert-contains-class {} loc s))
  ([opts loc s]
   (let [la (->locator-assertion opts loc)]
     (if (string? s)
       (.containsClass la ^String s)
       (.containsClass la (ArrayList/new ^Collection s))))
   true))

(defn ^{:assert/id :contains-text} assert-contains-text
  ([loc s]
   (assert-contains-text {} loc s))
  ([opts loc ^String s]
   (.containsText (->locator-assertion opts loc) s)
   true))

(defn ^{:assert/id :accessible-description} assert-accessible-description
  ([loc s]
   (assert-accessible-description {} loc s))
  ([opts loc ^String s]
   (.hasAccessibleDescription (->locator-assertion opts loc) s)
   true))

(defn ^{:assert/id :accessible-error-message} assert-accessible-error-message
  ([loc s]
   (assert-accessible-error-message {} loc s))
  ([opts loc ^String s]
   (.hasAccessibleErrorMessage (->locator-assertion opts loc) s)
   true))

(defn ^{:assert/id :accessible-name} assert-accessible-name
  ([loc s]
   (assert-accessible-name {} loc s))
  ([opts loc ^String s]
   (.hasAccessibleName (->locator-assertion opts loc) s)
   true))

(defn ^{:assert/id :attribute} assert-attribute
  ([loc attr-nm attr-val]
   (assert-attribute {} loc attr-nm attr-val))
  ([opts loc attr-nm ^String attr-val]
   (.hasAttribute (->locator-assertion opts loc) (name attr-nm) attr-val)
   true))

(defn ^{:assert/id :class} assert-class
  "Must fully match element's `class` attribute"
  ([loc s]
   (assert-class {} loc s))
  ([opts loc ^String s]
   (.hasClass (->locator-assertion opts loc) s)
   true))

(defn ^{:assert/id :count} assert-count
  ([loc n]
   (assert-count {} loc n))
  ([opts loc n]
   (.hasCount (->locator-assertion opts loc) n)
   true))

(defn ^{:assert/id :id} assert-id
  ([loc id]
   (assert-id {} loc id))
  ([opts loc ^String id]
   (.hasId (->locator-assertion opts loc) id)
   true))

(defn ^{:assert/id :role} assert-role
  ([loc role]
   (assert-role {} loc role))
  ([opts loc role]
   (.hasRole (->locator-assertion opts loc)
             (as-aria-role role))
   true))

(defn ^{:assert/id :text} assert-text
  ([loc s]
   (assert-text {} loc s))
  ([opts loc ^String s]
   (.hasText (->locator-assertion opts loc) s)
   true))

(defn ^{:assert/id :value} assert-value
  ([loc s]
   (assert-value {} loc s))
  ([opts loc ^String s]
   (.hasValue (->locator-assertion opts loc) s)
   true))


(defn ^{:assert/id :values} assert-values
  ([loc xs]
   (assert-values {} loc xs))
  ([opts loc xs]
   (.hasValues (->locator-assertion opts loc) ^String/1 (into-array xs))
   true))

(defn ^{:assert/id :attached} assert-attached
  ([loc]
   (assert-attached {} loc))
  ([opts loc]
   (.isAttached (->locator-assertion opts loc))
   true))

(defn ^{:assert/id :checked} assert-checked
  ([loc]
   (assert-checked {} loc))
  ([opts loc]
   (.isChecked (->locator-assertion opts loc))
   true))

(defn ^{:assert/id :disabled} assert-disabled
  ([loc]
   (assert-disabled {} loc))
  ([opts loc]
   (.isDisabled (->locator-assertion opts loc))
   true))

(defn ^{:assert/id :editable} assert-editable
  ([loc]
   (assert-editable {} loc))
  ([opts loc]
   (.isEditable (->locator-assertion opts loc))
   true))

(defn ^{:assert/id :empty} assert-empty
  ([loc]
   (assert-empty {} loc))
  ([opts loc]
   (.isEmpty (->locator-assertion opts loc))
   true))

(defn ^{:assert/id :enabled} assert-enabled
  ([loc]
   (assert-enabled {} loc))
  ([opts loc]
   (.isEnabled (->locator-assertion opts loc))
   true))

(defn ^{:assert/id :focused} assert-focused
  ([loc]
   (assert-focused {} loc))
  ([opts loc]
   (.isFocused (->locator-assertion opts loc))
   true))

(defn ^{:assert/id :hidden} assert-hidden
  ([loc]
   (assert-hidden {} loc))
  ([opts loc]
   (.isHidden (->locator-assertion opts loc))
   true))

(defn ^{:assert/id :in-viewport} assert-in-viewport
  ([loc]
   (assert-in-viewport {} loc))
  ([opts loc]
   (.isInViewport (->locator-assertion opts loc))
   true))

(defn ^{:assert/id :visible} assert-visible
  ([loc]
   (assert-visible {} loc))
  ([opts loc]
   (.isVisible (->locator-assertion opts loc))
   true))

(defn ^{:assert/id :matches-aria-snapshot} assert-matches-aria-snapshot
  ([loc s]
   (assert-matches-aria-snapshot {} loc s))
  ([opts loc s]
   (.matchesAriaSnapshot (->locator-assertion opts loc) s)
   true))


(defn set-page!
  "Sets `*page*` by using `alter-var-root`."
  [pg]
  (alter-var-root #'*page* (constantly pg)))

(defmacro with-page
  [pg & body]
  `(binding [*page* ~pg]
     ~@body))

(defmacro with-open-page
  "Creates a new page bound to `*page*` and navigate's to the specified `url`.
  The new page is created from a new browser context. Upon exit of this block
  the page will be closed and the binding to `*page*` undone."
  [url & body]
  `(with-open [pg# (->page)]
     (navigate pg# ~url)
     (with-page pg#
       ~@body)))

(defmacro with-open-page-debug
  "Like `with-open-page` but alter-var-root's the `*page*` and does not close it
  to make it available for debugging. It is up to the user to call `close!` on the
  `*page*`"
  [url & body]
  `(let [pg# (->page)]
     (set-page! pg#)
     (navigate ~url)
     ~@body))

(defn end-open-page-debug!
  "Convenience fn to clean up resources/state creted in `with-open-page-debug`"
  []
  (close! *page*)
  (set-page! nil))


(defn- ->id->assert-fn
  [kw]
  (dissoc
   (->> (ns-publics *ns*)
        vals
        (map (juxt (comp kw meta)
                   identity))
        (into {}))
   nil))

(->> (ns-publics *ns*)
     vals)

(meta #'assert-id)

(def ^:private assert-id->fn
  (->id->assert-fn :assert/id))

(defn ^:no-doc lookup-assert-fn
  [kw]
  (get assert-id->fn kw))



(defn ^:no-doc is-page
  [opts pg|kw kw|arg & more]
  (let [[pg kw more] (if (page? pg|kw)
                       [pg|kw kw|arg more]
                       [*page* pg|kw (cons kw|arg more)])
        assert-fn (lookup-assert-fn kw)
        form (list* kw more)]
    (when-not assert-fn
      (throw (ex-info (format "No page assertion fn for keyword `%s`" kw) {:keyword kw})))
    (try
      (apply assert-fn opts pg more)
      (test/do-report {:type :pass :message "success"
                       :expected form :actual true})
      (catch AssertionFailedError e
        (test/do-report {:type :fail :message (ex-message e)
                         :expected form :actual e})))))


(defn ^:no-doc is-loc
  [opts loc kw & more]
  (let [assert-fn (lookup-assert-fn kw)
        more (vec more) ;; turn to vector to avoid things like ("hi")
        ;; last arg to list* must be a coll, (list* "hi") => (\h \i)
        form (list* loc kw more)]
    (when-not assert-fn
      (throw (ex-info (format "No location assertion fn for keyword `%s`" kw) {:keyword kw})))
    (try
      (apply assert-fn (list* opts loc more))
      (test/do-report {:type :pass :message "success"
                       :expected form :actual true})
      (catch AssertionFailedError e
        (test/do-report {:type :fail :message (ex-message e)
                         :expected form :actual e})))))

(defmacro is
  [& [x :as args]]
  `(let [pg?# (or (page? ~x)
                  (and (keyword? ~x)
                       (= "page" (namespace ~x))))
         opts# {:not? false}]
     (if pg?#
       (apply is-page (list* opts# ~@args []))
       (apply is-loc (list* opts# ~@args [])))))
