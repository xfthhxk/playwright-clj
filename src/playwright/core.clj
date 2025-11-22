(ns playwright.core
  "Playwright wrapper."
  (:refer-clojure :exclude [count])
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io])
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
   (com.microsoft.playwright.options AriaRole SelectOption WaitForSelectorState)))

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
  ([]
   (->page (->browser-context)))
  ([^BrowserContext bc]
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


(defn locator?
  [x]
  (instance? Locator x))

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
               ^AriaRole role# (if (instance? AriaRole s#)
                                 s#
                                 (-> s# name str/upper-case AriaRole/valueOf))
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


(defn assert-title
  ([s]
   (assert-title *page* s))
  ([^Page p s]
   (let [a (PlaywrightAssertions/assertThat p)]
     (if (string? s)
       (.hasTitle a ^String s)
       (.hasTitle a ^Pattern s))
     true)))


(defn assert-url
  ([s]
   (assert-url *page* s))
  ([^Page p s]
   (let [a (PlaywrightAssertions/assertThat p)]
     (if (string? s)
       (.hasURL a ^String s)
       (.hasURL a ^Pattern s))
     true)))


(defn assert-contains-class
  [loc s]
  (if (string? s)
    (.containsClass (PlaywrightAssertions/assertThat (as-locator loc)) ^String s)
    (.containsClass (PlaywrightAssertions/assertThat (as-locator loc)) (ArrayList/new ^Collection s)))
  true)

(defn assert-contains-text
  [loc ^String s]
  (.containsText (PlaywrightAssertions/assertThat (as-locator loc)) s)
  true)

(defn assert-accessible-description
  [loc ^String s]
  (.hasAccessibleDescription (PlaywrightAssertions/assertThat (as-locator loc)) s)
  true)


(defn assert-accessible-error-message
  [loc ^String s]
  (.hasAccessibleErrorMessage (PlaywrightAssertions/assertThat (as-locator loc)) s)
  true)

(defn assert-accessible-name
  [loc ^String s]
  (.hasAccessibleName (PlaywrightAssertions/assertThat (as-locator loc)) s)
  true)

(defn assert-attribute
  [loc ^String attr-nm ^String attr-val]
  (.hasAttribute (PlaywrightAssertions/assertThat (as-locator loc)) attr-nm attr-val)
  true)

(defn assert-class
  "Must fully match element's `class` attribute"
  [loc ^String s]
  (.hasClass (PlaywrightAssertions/assertThat (as-locator loc)) s)
  true)

(defn assert-count
  [loc n]
  (.hasCount (PlaywrightAssertions/assertThat (as-locator loc)) n)
  true)


(defn assert-id
  [loc ^String id]
  (.hasId (PlaywrightAssertions/assertThat (as-locator loc)) id)
  true)

(defn assert-role
  [loc role]
  (.hasRole (PlaywrightAssertions/assertThat (as-locator loc)) (-> role
                                                    name
                                                    str/upper-case
                                                    AriaRole/valueOf))
  true)

(defn assert-text
  [loc ^String s]
  (.hasText (PlaywrightAssertions/assertThat (as-locator loc)) s)
  true)

(defn assert-value
  [loc ^String s]
  (.hasValue (PlaywrightAssertions/assertThat (as-locator loc)) s)
  true)


(defn assert-values
  [loc xs]
  (.hasValues (PlaywrightAssertions/assertThat (as-locator loc)) ^String/1 (into-array xs))
  true)

(defn assert-attached
  [loc]
  (.isAttached (PlaywrightAssertions/assertThat (as-locator loc)))
  true)

(defn assert-checked
  [loc]
  (.isChecked (PlaywrightAssertions/assertThat (as-locator loc)))
  true)

(defn assert-disabled
  [loc]
  (.isDisabled (PlaywrightAssertions/assertThat (as-locator loc)))
  true)

(defn assert-editable
  [loc]
  (.isEditable (PlaywrightAssertions/assertThat (as-locator loc)))
  true)

(defn assert-empty
  [loc]
  (.isEmpty (PlaywrightAssertions/assertThat (as-locator loc)))
  true)

(defn assert-enabled
  [loc]
  (.isEnabled (PlaywrightAssertions/assertThat (as-locator loc)))
  true)

(defn assert-focused
  [loc]
  (.isFocused (PlaywrightAssertions/assertThat (as-locator loc)))
  true)

(defn assert-hidden
  [loc]
  (.isHidden (PlaywrightAssertions/assertThat (as-locator loc)))
  true)

(defn assert-in-viewport
  [loc]
  (.isInViewport (PlaywrightAssertions/assertThat (as-locator loc)))
  true)

(defn assert-visible
  [loc]
  (.isVisible (PlaywrightAssertions/assertThat (as-locator loc)))
  true)

(defn assert-matches-aria-snapshot
  [loc s]
  (.matchesAriaSnapshot (PlaywrightAssertions/assertThat (as-locator loc)) s)
  true)


(defn set-page!
  [pg]
  (alter-var-root #'*page* (constantly pg)))
