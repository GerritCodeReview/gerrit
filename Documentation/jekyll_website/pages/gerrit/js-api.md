---
title: " Gerrit Code Review - JavaScript API"
sidebar: gerritdoc_sidebar
permalink: js-api.html
---
Gerrit Code Review supports an API for JavaScript plugins to interact
with the web UI and the server process.

## Entry Point

JavaScript is loaded using a standard `<script src='...'>` HTML tag.
Plugins should protect the global namespace by defining their code
within an anonymous function passed to `Gerrit.install()`. The plugin
will be passed an object describing its registration with Gerrit:

``` javascript
Gerrit.install(function (self) {
  // ... plugin JavaScript code here ...
});
```

## Plugin Instance

The plugin instance is passed to the plugin’s initialization function
and provides a number of utility services to plugin authors.

### self.delete() / self.del()

Issues a DELETE REST API request to the Gerrit server.

**Signature.**

``` javascript
Gerrit.delete(url, callback)
Gerrit.del(url, callback)
```

  - url: URL relative to the plugin’s URL space. The JavaScript library
    prefixes the supplied URL with `/plugins/{getPluginName}/`.

  - callback: JavaScript function to be invoked with the parsed JSON
    result of the API call. DELETE methods often return `204 No
    Content`, which is passed as null.

### self.get()

Issues a GET REST API request to the Gerrit server.

**Signature.**

``` javascript
self.get(url, callback)
```

  - url: URL relative to the plugin’s URL space. The JavaScript library
    prefixes the supplied URL with `/plugins/{getPluginName}/`.

  - callback: JavaScript function to be invoked with the parsed JSON
    result of the API call. If the API returns a string the result is a
    string, otherwise the result is a JavaScript object or array, as
    described in the relevant REST API documentation.

### self.getServerInfo()

Returns the server’s [ServerInfo](rest-api-config.html#server-info)
data.

### self.getCurrentUser()

Returns the currently signed in user’s AccountInfo data; empty account
data if no user is currently signed in.

### Gerrit.getUserPreferences()

Returns the preferences of the currently signed in user; the default
preferences if no user is currently signed in.

### Gerrit.refreshUserPreferences()

Refreshes the preferences of the current user.

### self.getPluginName()

Returns the name this plugin was installed as by the server
administrator. The plugin name is required to access REST API views
installed by the plugin, or to access resources.

### self.post()

Issues a POST REST API request to the Gerrit server.

**Signature.**

``` javascript
self.post(url, input, callback)
```

  - url: URL relative to the plugin’s URL space. The JavaScript library
    prefixes the supplied URL with `/plugins/{getPluginName}/`.

  - input: JavaScript object to serialize as the request payload.

  - callback: JavaScript function to be invoked with the parsed JSON
    result of the API call. If the API returns a string the result is a
    string, otherwise the result is a JavaScript object or array, as
    described in the relevant REST API documentation.

<!-- end list -->

``` javascript
self.post(
  '/my-servlet',
  {start_build: true, platform_type: 'Linux'},
  function (r) {});
```

### self.put()

Issues a PUT REST API request to the Gerrit server.

**Signature.**

``` javascript
self.put(url, input, callback)
```

  - url: URL relative to the plugin’s URL space. The JavaScript library
    prefixes the supplied URL with `/plugins/{getPluginName}/`.

  - input: JavaScript object to serialize as the request payload.

  - callback: JavaScript function to be invoked with the parsed JSON
    result of the API call. If the API returns a string the result is a
    string, otherwise the result is a JavaScript object or array, as
    described in the relevant REST API documentation.

<!-- end list -->

``` javascript
self.put(
  '/builds',
  {start_build: true, platform_type: 'Linux'},
  function (r) {});
```

### self.on()

Register a JavaScript callback to be invoked when events occur within
the web interface.

**Signature.**

``` javascript
Gerrit.on(event, callback);
```

  - event: A supported event type. See below for description.

  - callback: JavaScript function to be invoked when event happens.
    Arguments may be passed to this function, depending on the event.

Supported events:

  - `history`: Invoked when the view is changed to a new screen within
    the Gerrit web application. The token after "\#" is passed as the
    argument to the callback function, for example "/c/42/" while
    showing change 42.

  - `showchange`: Invoked when a change is made visible. A
    [ChangeInfo](rest-api-changes.html#change-info) and
    [RevisionInfo](rest-api-changes.html#revision-info) are passed as
    arguments.

  - `submitchange`: Invoked when the submit button is clicked on a
    change. A [ChangeInfo](rest-api-changes.html#change-info) and
    [RevisionInfo](rest-api-changes.html#revision-info) are passed as
    arguments. Similar to a form submit validation, the function must
    return true to allow the operation to continue, or false to prevent
    it.

  - `comment`: Invoked when a DOM element that represents a comment is
    created. This DOM element is passed as argument. This DOM element
    contains nested elements that Gerrit uses to format the comment. The
    DOM structure may differ between comment types such as inline
    comments, file-level comments and summary comments, and it may
    change with new Gerrit versions.

### self.onAction()

Register a JavaScript callback to be invoked when the user clicks on a
button associated with a server side `UiAction`.

**Signature.**

``` javascript
self.onAction(type, view_name, callback);
```

  - type: `'change'`, `'edit'`, `'revision'`, `'project'`, or `'branch'`
    indicating which type of resource the `UiAction` was bound to in the
    server.

  - view\_name: string appearing in URLs to name the view. This is the
    second argument of the `get()`, `post()`, `put()`, and `delete()`
    binding methods in a `RestApiModule`.

  - callback: JavaScript function to invoke when the user clicks. The
    function will be passed a [action context](#ActionContext).

### self.screen()

Register a JavaScript callback to be invoked when the user navigates to
an extension screen provided by the plugin. Extension screens are
usually linked from the [top
menu](dev-plugins.html#top-menu-extensions). The callback can populate
the DOM with the screen’s contents.

**Signature.**

``` javascript
self.screen(pattern, callback);
```

  - pattern: URL token pattern to identify the screen. Argument can be
    either a string (`'index'`) or a RegExp object (`/list\/(.*)/`). If
    a RegExp is used the matching groups will be available inside of the
    context as `token_match`.

  - callback: JavaScript function to invoke when the user navigates to
    the screen. The function will be passed a [screen
    context](#ScreenContext).

### self.settingsScreen()

Register a JavaScript callback to be invoked when the user navigates to
an extension settings screen provided by the plugin. Extension settings
screens are automatically linked from the settings menu under the given
menu entry. The callback can populate the DOM with the screen’s
contents.

**Signature.**

``` javascript
self.settingsScreen(path, menu, callback);
```

  - path: URL path to identify the settings screen.

  - menu: The name of the menu entry in the settings menu that should
    link to the settings screen.

  - callback: JavaScript function to invoke when the user navigates to
    the settings screen. The function will be passed a [settings screen
    context](#SettingsScreenContext).

### self.panel()

Register a JavaScript callback to be invoked when a screen with the
given extension point is loaded. The callback can populate the DOM with
the panel’s contents.

**Signature.**

``` javascript
self.panel(extensionpoint, callback);
```

  - extensionpoint: The name of the extension point that marks the
    position where the panel is added to an existing screen. The
    available extension points are described in the [plugin development
    documentation](dev-plugins.html#panels).

  - callback: JavaScript function to invoke when a screen with the
    extension point is loaded. The function will be passed a [panel
    context](#PanelContext).

### self.url()

Returns a URL within the plugin’s URL space. If invoked with no
parameter the URL of the plugin is returned. If passed a string the
argument is appended to the plugin
URL.

``` javascript
self.url();                    // "https://gerrit-review.googlesource.com/plugins/demo/"
self.url('/static/icon.png');  // "https://gerrit-review.googlesource.com/plugins/demo/static/icon.png"
```

## Action Context

A new action context is passed to the `onAction` callback function each
time the associated action button is clicked by the user. A context is
initialized with sufficient state to issue the associated REST API RPC.

### context.action

An [ActionInfo](rest-api-changes.html#action-info) object instance
supplied by the server describing the UI button the user used to invoke
the action.

### context.call()

Issues the REST API call associated with the action. The HTTP method
used comes from `context.action.method`, hiding the JavaScript from
needing to care.

**Signature.**

``` javascript
context.call(input, callback)
```

  - input: JavaScript object to serialize as the request payload. This
    parameter is ignored for GET and DELETE methods.

  - callback: JavaScript function to be invoked with the parsed JSON
    result of the API call. If the API returns a string the result is a
    string, otherwise the result is a JavaScript object or array, as
    described in the relevant REST API documentation.

<!-- end list -->

``` javascript
context.call(
  {message: "..."},
  function (result) {
    // ... use result here ...
  });
```

### context.change

When the action is invoked on a change a
[ChangeInfo](rest-api-changes.html#change-info) object instance
describing the change. Available fields of the ChangeInfo may vary based
on the options used by the UI when it loaded the change.

### context.delete()

Issues a DELETE REST API call to the URL associated with the action.

**Signature.**

``` javascript
context.delete(callback)
```

  - callback: JavaScript function to be invoked with the parsed JSON
    result of the API call. DELETE methods often return `204 No
    Content`, which is passed as null.

<!-- end list -->

``` javascript
context.delete(function () {});
```

### context.get()

Issues a GET REST API call to the URL associated with the action.

**Signature.**

``` javascript
context.get(callback)
```

  - callback: JavaScript function to be invoked with the parsed JSON
    result of the API call. If the API returns a string the result is a
    string, otherwise the result is a JavaScript object or array, as
    described in the relevant REST API documentation.

<!-- end list -->

``` javascript
context.get(function (result) {
  // ... use result here ...
});
```

### context.go()

Go to a screen. Shorthand for [`Gerrit.go()`](#Gerrit_go).

### context.hide()

Hide the currently visible popup displayed by
[`context.popup()`](#context_popup).

### context.post()

Issues a POST REST API call to the URL associated with the action.

**Signature.**

``` javascript
context.post(input, callback)
```

  - input: JavaScript object to serialize as the request payload.

  - callback: JavaScript function to be invoked with the parsed JSON
    result of the API call. If the API returns a string the result is a
    string, otherwise the result is a JavaScript object or array, as
    described in the relevant REST API documentation.

<!-- end list -->

``` javascript
context.post(
  {message: "..."},
  function (result) {
    // ... use result here ...
  });
```

### context.popup()

Displays a small popup near the activation button to gather additional
input from the user before executing the REST API RPC.

The caller is always responsible for closing the popup with
link\#context\_hide\[`context.hide()`\]. Gerrit will handle closing a
popup if the user presses `Escape` while keyboard focus is within the
popup.

**Signature.**

``` javascript
context.popup(element)
```

  - element: an HTML DOM element to display as the body of the popup.
    This is typically a `div` element but can be any valid HTML element.
    CSS can be used to style the element beyond the defaults.

A common usage is to gather more input:

``` javascript
self.onAction('revision', 'start-build', function (c) {
  var l = c.checkbox();
  var m = c.checkbox();
  c.popup(c.div(
    c.div(c.label(l, 'Linux')),
    c.div(c.label(m, 'Mac OS X')),
    c.button('Build', {onclick: function() {
      c.call(
        {
          commit: c.revision.name,
          linux: l.checked,
          mac: m.checked,
        },
        function() { c.hide() });
    });
});
```

### context.put()

Issues a PUT REST API call to the URL associated with the action.

**Signature.**

``` javascript
context.put(input, callback)
```

  - input: JavaScript object to serialize as the request payload.

  - callback: JavaScript function to be invoked with the parsed JSON
    result of the API call. If the API returns a string the result is a
    string, otherwise the result is a JavaScript object or array, as
    described in the relevant REST API documentation.

<!-- end list -->

``` javascript
context.put(
  {message: "..."},
  function (result) {
    // ... use result here ...
  });
```

### context.refresh()

Refresh the current display. Shorthand for
[`Gerrit.refresh()`](#Gerrit_refresh).

### context.revision

When the action is invoked on a specific revision of a change, a
[RevisionInfo](rest-api-changes.html#revision-info) object instance
describing the revision. Available fields of the RevisionInfo may vary
based on the options used by the UI when it loaded the change.

### context.project

When the action is invoked on a specific project, the name of the
project.

### HTML Helpers

The [action context](#ActionContext) includes some HTML helper functions
to make working with DOM based widgets less painful.

  - `br()`: new `<br>` element.

  - `button(label, options)`: new `<button>` with the string `label`
    wrapped inside of a `div`. The optional `options` object may define
    `onclick` as a function to be invoked upon clicking. This calling
    pattern avoids circular references between the element and the
    onclick handler.

  - `checkbox()`: new `<input type='checkbox'>` element.

  - `div(...)`: a new `<div>` wrapping the (optional) arguments.

  - `hr()`: new `<hr>` element.

  - `label(c, label)`: a new `<label>` element wrapping element `c` and
    the string `label`. Used to wrap a checkbox with its label,
    `label(checkbox(), 'Click Me')`.

  - `prependLabel(label, c)`: a new `<label>` element wrapping element
    `c` and the string `label`. Used to wrap an input field with its
    label, `prependLabel('Greeting message', textfield())`.

  - `textarea(options)`: new `<textarea>` element. The options object
    may optionally include `rows` and `cols`. The textarea comes with an
    onkeypress handler installed to play nicely with Gerrit’s keyboard
    binding system.

  - `textfield()`: new `<input type='text'>` element. The text field
    comes with an onkeypress handler installed to play nicely with
    Gerrit’s keyboard binding system.

  - `select(a,i)`: a new `<select>` element containing one `<option>`
    element for each entry in the provided array `a`. The option with
    the index `i` will be pre-selected in the drop-down-list.

  - `selected(s)`: returns the text of the `<option>` element that is
    currently selected in the provided `<select>` element `s`.

  - `span(...)`: a new `<span>` wrapping the (optional) arguments.

  - `msg(label)`: a new label.

## Screen Context

A new screen context is passed to the `screen` callback function each
time the user navigates to a matching URL.

### screen.body

Empty HTML `<div>` node the plugin should add its content to. The node
is already attached to the document, but is invisible. Plugins must call
`screen.show()` to display the DOM node. Deferred display allows an
implementor to partially populate the DOM, make remote HTTP requests,
finish populating when the callbacks arrive, and only then make the view
visible to the user.

### screen.token

URL token fragment that activated this screen. The value is identical to
`screen.token_match[0]`. If the URL is `/#/x/hello/list` the token will
be `"list"`.

### screen.token\_match

Array of matching subgroups from the pattern specified to `screen()`.
This is identical to the result of RegExp.exec. Index 0 contains the
entire matching expression; index 1 the first matching group, etc.

### screen.onUnload()

Configures an optional callback to be invoked just before the screen is
deleted from the browser DOM. Plugins can use this callback to remove
event listeners from DOM nodes, preventing memory leaks.

**Signature.**

``` javascript
screen.onUnload(callback)
```

  - callback: JavaScript function to be invoked just before the
    `screen.body` DOM element is removed from the browser DOM. This
    event happens when the user navigates to another screen.

### screen.setTitle()

Sets the heading text to be displayed when the screen is visible. This
is presented in a large bold font below the menus, but above the content
in `screen.body`. Setting the title also sets the window title to the
same string, if it has not already been set.

**Signature.**

``` javascript
screen.setPageTitle(titleText)
```

### screen.setWindowTitle()

Sets the text to be displayed in the browser’s title bar when the screen
is visible. Plugins should always prefer this method over trying to set
`window.title` directly. The window title defaults to the title given to
`setTitle`.

**Signature.**

``` javascript
screen.setWindowTitle(titleText)
```

### screen.show()

Destroy the currently visible screen and display the plugin’s screen.
This method must be called after adding content to `screen.body`.

## Settings Screen Context

A new settings screen context is passed to the `settingsScreen` callback
function each time the user navigates to a matching URL.

### settingsScreen.body

Empty HTML `<div>` node the plugin should add its content to. The node
is already attached to the document, but is invisible. Plugins must call
`settingsScreen.show()` to display the DOM node. Deferred display allows
an implementor to partially populate the DOM, make remote HTTP requests,
finish populating when the callbacks arrive, and only then make the view
visible to the user.

### settingsScreen.onUnload()

Configures an optional callback to be invoked just before the screen is
deleted from the browser DOM. Plugins can use this callback to remove
event listeners from DOM nodes, preventing memory leaks.

**Signature.**

``` javascript
settingsScreen.onUnload(callback)
```

  - callback: JavaScript function to be invoked just before the
    `settingsScreen.body` DOM element is removed from the browser DOM.
    This event happens when the user navigates to another screen.

### settingsScreen.setTitle()

Sets the heading text to be displayed when the screen is visible. This
is presented in a large bold font below the menus, but above the content
in `settingsScreen.body`. Setting the title also sets the window title
to the same string, if it has not already been set.

**Signature.**

``` javascript
settingsScreen.setPageTitle(titleText)
```

### settingsScreen.setWindowTitle()

Sets the text to be displayed in the browser’s title bar when the screen
is visible. Plugins should always prefer this method over trying to set
`window.title` directly. The window title defaults to the title given to
`setTitle`.

**Signature.**

``` javascript
settingsScreen.setWindowTitle(titleText)
```

### settingsScreen.show()

Destroy the currently visible screen and display the plugin’s screen.
This method must be called after adding content to
`settingsScreen.body`.

## Panel Context

A new panel context is passed to the `panel` callback function each time
a screen with the given extension point is loaded.

### panel.body

Empty HTML `<div>` node the plugin should add the panel content to. The
node is already attached to the document.

### Properties

The extension panel parameters that are described in the [plugin
development documentation](dev-plugins.html#panels) are contained in the
context as properties. Which properties are available depends on the
extension point.

## Gerrit

The `Gerrit` object is the only symbol provided into the global
namespace by Gerrit Code Review. All top-level functions can be accessed
through this name.

### Gerrit.css()

Creates a new unique CSS class and injects it into the document. The
name of the class is returned and can be used by the plugin. See
[`Gerrit.html()`](#Gerrit_html) for an easy way to use generated class
names.

Classes created with this function should be created once at install
time and reused throughout the plugin. Repeatedly creating the same
class will explode the global stylesheet.

**Signature.**

``` javascript
Gerrit.install(function(self)) {
  var style = {
    name: Gerrit.css('background: #fff; color: #000;'),
  };
});
```

### Gerrit.delete()

Issues a DELETE REST API request to the Gerrit server. For plugin
private REST API URLs see [self.delete()](#self_delete).

**Signature.**

``` javascript
Gerrit.delete(url, callback)
```

  - url: URL relative to the Gerrit server. For example to access the
    [changes REST API](rest-api-changes.html) use `'/changes/'`.

  - callback: JavaScript function to be invoked with the parsed JSON
    result of the API call. DELETE methods often return `204 No
    Content`, which is passed as null.

<!-- end list -->

``` javascript
Gerrit.delete(
  '/changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/topic',
  function () {});
```

### Gerrit.get()

Issues a GET REST API request to the Gerrit server. For plugin private
REST API URLs see [self.get()](#self_get).

**Signature.**

``` javascript
Gerrit.get(url, callback)
```

  - url: URL relative to the Gerrit server. For example to access the
    [changes REST API](rest-api-changes.html) use `'/changes/'`.

  - callback: JavaScript function to be invoked with the parsed JSON
    result of the API call. If the API returns a string the result is a
    string, otherwise the result is a JavaScript object or array, as
    described in the relevant REST API documentation.

<!-- end list -->

``` javascript
Gerrit.get('/changes/?q=status:open', function (open) {
  for (var i = 0; i < open.length; i++) {
    console.log(open.get(i).change_id);
  }
});
```

### Gerrit.getCurrentUser()

Returns the currently signed in user’s AccountInfo data; empty account
data if no user is currently signed in.

### Gerrit.getPluginName()

Returns the name this plugin was installed as by the server
administrator. The plugin name is required to access REST API views
installed by the plugin, or to access resources.

Unlike [`self.getPluginName()`](#self_getPluginName) this method must
guess the name from the JavaScript call stack. Plugins are encouraged to
use `self.getPluginName()` whenever possible.

### Gerrit.go()

Updates the web UI to display the screen identified by the supplied URL
token. The URL token is the text after `#` in the browser URL.

``` javascript
Gerrit.go('/admin/projects/');
```

If the URL passed matches `http://...`, `https://...`, or `//...` the
current browser window will navigate to the non-Gerrit URL. The user can
return to Gerrit with the back button.

### Gerrit.html()

Parses an HTML fragment after performing template replacements. If the
HTML has a single root element or node that node is returned, otherwise
it is wrapped inside a `<div>` and the div is returned.

**Signature.**

``` javascript
Gerrit.html(htmlText, options, wantElements);
```

  - htmlText: string of HTML to be parsed. A new unattached `<div>` is
    created in the browser’s document and the innerHTML property is
    assigned to the passed string, after performing replacements. If the
    div has exactly one child, that child will be returned instead of
    the div.

  - options: optional object reference supplying replacements for any
    `{name}` references in htmlText. Navigation through objects is
    supported permitting `{style.bar}` to be replaced with `"foo"` if
    options was `{style: {bar: "foo"}}`. Value replacements are HTML
    escaped before being inserted into the document fragment.

  - wantElements: if options is given and wantElements is also true an
    object consisting of `{root: parsedElement, elements: {...}}` is
    returned instead of the parsed element. The elements object contains
    a property for each element using `id={name}` in htmlText.

**Example.**

``` javascript
var style = {bar: Gerrit.css('background: yellow')};
Gerrit.html(
  '<span class="{style.bar}">Hello {name}!</span>',
  {style: style, name: "World"});
```

Event handlers can be automatically attached to elements referenced
through an attribute id. Object navigation is not supported for ids, and
the parser strips the id attribute before returning the result. Handler
functions must begin with `on` and be a function to be installed on the
element. This approach is useful for onclick and other handlers that do
not want to create circular references that will eventually leak browser
memory.

**Example.**

``` javascript
var options = {
  link: {
    onclick: function(e) { window.close() },
  },
};
Gerrit.html('<a href="javascript:;" id="{link}">Close</a>', options);
```

When using options to install handlers care must be taken to not
accidentally include the returned element into the event handler’s
closure. This is why options is built before calling `Gerrit.html()` and
not inline as a shown above with "Hello World".

DOM nodes can optionally be returned, allowing handlers to access the
elements identified by `id={name}` at a later point in time.

**Example.**

``` javascript
var w = Gerrit.html(
    '<div>Name: <input type="text" id="{name}"></div>'
  + '<div>Age: <input type="text" id="{age}"></div>'
  + '<button id="{submit}"><div>Save</div></button>',
  {
    submit: {
      onclick: function(s) {
        var e = w.elements;
        window.alert(e.name.value + " is " + e.age.value);
      },
    },
  }, true);
```

To prevent memory leaks `w.root` and `w.elements` should be set to null
when the elements are no longer necessary. Screens can use
[screen.onUnload()](#screen_onUnload) to define a callback function to
perform this cleanup:

``` javascript
var w = Gerrit.html(...);
screen.body.appendElement(w.root);
screen.onUnload(function() { w.clear() });
```

### Gerrit.injectCss()

Injects CSS rules into the document by appending onto the end of the
existing rule list. CSS rules are global to the entire application and
must be manually scoped by each plugin. For an automatic scoping
alternative see [`css()`](#Gerrit_css).

``` javascript
Gerrit.injectCss('.myplugin_bg {background: #000}');
```

### Gerrit.install()

Registers a new plugin by invoking the supplied initialization function.
The function is passed the [plugin instance](#self).

``` javascript
Gerrit.install(function (self) {
  // ... plugin JavaScript code here ...
});
```

### Gerrit.post()

Issues a POST REST API request to the Gerrit server. For plugin private
REST API URLs see [self.post()](#self_post).

**Signature.**

``` javascript
Gerrit.post(url, input, callback)
```

  - url: URL relative to the Gerrit server. For example to access the
    [changes REST API](rest-api-changes.html) use `'/changes/'`.

  - input: JavaScript object to serialize as the request payload.

  - callback: JavaScript function to be invoked with the parsed JSON
    result of the API call. If the API returns a string the result is a
    string, otherwise the result is a JavaScript object or array, as
    described in the relevant REST API documentation.

<!-- end list -->

``` javascript
Gerrit.post(
  '/changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/topic',
  {topic: 'tests', message: 'Classify work as for testing.'},
  function (r) {});
```

### Gerrit.put()

Issues a PUT REST API request to the Gerrit server. For plugin private
REST API URLs see [self.put()](#self_put).

**Signature.**

``` javascript
Gerrit.put(url, input, callback)
```

  - url: URL relative to the Gerrit server. For example to access the
    [changes REST API](rest-api-changes.html) use `'/changes/'`.

  - input: JavaScript object to serialize as the request payload.

  - callback: JavaScript function to be invoked with the parsed JSON
    result of the API call. If the API returns a string the result is a
    string, otherwise the result is a JavaScript object or array, as
    described in the relevant REST API documentation.

<!-- end list -->

``` javascript
Gerrit.put(
  '/changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/topic',
  {topic: 'tests', message: 'Classify work as for testing.'},
  function (r) {});
```

### Gerrit.onAction()

Register a JavaScript callback to be invoked when the user clicks on a
button associated with a server side `UiAction`.

**Signature.**

``` javascript
Gerrit.onAction(type, view_name, callback);
```

  - type: `'change'`, `'edit'`, `'revision'`, `'project'` or `'branch'`
    indicating what sort of resource the `UiAction` was bound to in the
    server.

  - view\_name: string appearing in URLs to name the view. This is the
    second argument of the `get()`, `post()`, `put()`, and `delete()`
    binding methods in a `RestApiModule`.

  - callback: JavaScript function to invoke when the user clicks. The
    function will be passed a [ActionContext](#ActionContext).

### Gerrit.screen()

Register a JavaScript callback to be invoked when the user navigates to
an extension screen provided by the plugin. Extension screens are
usually linked from the [top
menu](dev-plugins.html#top-menu-extensions). The callback can populate
the DOM with the screen’s contents.

**Signature.**

``` javascript
Gerrit.screen(pattern, callback);
```

  - pattern: URL token pattern to identify the screen. Argument can be
    either a string (`'index'`) or a RegExp object (`/list\/(.*)/`). If
    a RegExp is used the matching groups will be available inside of the
    context as `token_match`.

  - callback: JavaScript function to invoke when the user navigates to
    the screen. The function will be passed [screen
    context](#ScreenContext).

### Gerrit.refresh()

Redisplays the current web UI view, refreshing all information.

### Gerrit.refreshMenuBar()

Refreshes Gerrit’s menu bar.

### Gerrit.isSignedIn()

Checks if user is signed in.

### Gerrit.url()

Returns the URL of the Gerrit Code Review server. If invoked with no
parameter the URL of the site is returned. If passed a string the
argument is appended to the site URL.

``` javascript
Gerrit.url();        // "https://gerrit-review.googlesource.com/"
Gerrit.url('/123');  // "https://gerrit-review.googlesource.com/123"
```

For a plugin specific version see [`self.url()`](#self_url\(\)).

### Gerrit.showError(message)

Displays the given message in the Gerrit ErrorDialog.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

