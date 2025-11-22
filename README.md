playwright-clj
================

A Clojure wrapper for Playwright.


### NB
* Status: alpha

### Usage
* Require
`(require '[playwright.core :as pw])`
* Install playwright and dependencies.
`(pw/install!)`
* Potentially set the `PLAYWRIGHT_SKIP_VALIDATE_HOST_REQUIREMENTS` ie
`export PLAYWRIGHT_SKIP_VALIDATE_HOST_REQUIREMENTS=true`
This was necessary on ubuntu running `nix-shell`. YMMV


### Gotchas
* Looking things up by role is preferred but can get tricky. Given the following HTML
```html
<input id="user-name" placeholder="Username" type="text" data-testid="username" name="user-name">
<input id="login-button" type="submit" data-testid="login-button" value="Login">
```
this code will timeout because the `:name` must be a value that the user sees. It does *NOT* correspond to the
`input` element's `name` attribute.
```clj
;; timesout
(pw/fill (playwright/get-by-role page :textbox {:name "user-name"}) "picard@starfleet.org")
```

Some workable alternatives:
```clj
(pw/fill (pw/get-by-placeholder page "Username") "picard@starfleet.org")
;; or
(pw/fill (pw/locator page "input[name='user-name']") "picard@starfleet.org")
;; or
(pw/fill (pw/locator page "#username") "picard@starfleet.org")
;; or
(pw/fill (pw/get-by-test-id page "username") "picard@starfleet.org")
```

This will work because the button's value `Login` is visible to the user.
```clj
(pw/click (pw/get-by-role pg :button {:name "Login"}))
```
