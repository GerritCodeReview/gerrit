% Polygerrit plugins

# Objective

To provide a robust, powerful, simple UI extension interface for PolyGerrit.

## Manifest
(inspired by [chromium extension manifesto](https://www.chromium.org/developers/design-documents/extensions/how-the-extension-system-works/extension-manifesto))

This design aims to provide plugin system, that is:

*   **Open**
Plugins should not require a specific product stack, tool, library, or framework to function. Plugins should be based on open standards.
*   **General**
It should be possible for one plugin to handle all types of tasks required. It should be possible to move code from PolyGerrit into plugins and vice versa.
*   **Stable**
Plugins should not be able to negatively affect normal PolyGerrit workflow, crash or significantly affect performance.
*   **Simple**
It should be easy and straightforward to create, support and troubleshoot a plugin.
*   **Compatible**
Plugins targeting different versions of PolyGerrit should work without major changes.
Plugin migration from GWT UI should be possible.

## Goals

*   Provide plugin configuration
*   Migration code from PolyGerrit into plugins and other way around
*   Insert new elements into PolyGerrit UI
*   Decorate existing elements in PolyGerrit UI
*   Support as much of existing Gerrit JS API as possible
*   Plugins targeting different versions of PolyGerrit work without major changes
*   Provide necessary tools for plugin development
*   Provide guidelines for upgrading GWT plugins
*   Users of upstream Gerrit are well enough supported that they do not refuse to upgrade their Gerrit installations

## Non-goals

*   PolyGerrit to emulate/implement/polyfill and support non-related functionality (e.g. GWT internals) to make GWT UI plugins to work without any changes
*   Bug-for-bug compatibility with the GWT UI's implementation of the JS API
*   Provide a build environment for GWT plugins

# Background

Gerrit has an extensive [plugin development API](https://gerrit-review.googlesource.com/Documentation/config-plugins.html) that covers a myriad of plugin use-cases. This document focuses on the UI-focused extension points and how they differ from their GWT UI counterparts. No changes to server-side plugins are proposed.

The Gerrit GWT UI provides [number of UI extension options](https://gerrit-review.googlesource.com/Documentation/dev-plugins.html#gwt_ui_extension) with plugin developers who use GWT being the primary focus. Per the documentation above, a GWT UI extension must extend the abstract class [PluginEntryPoint](https://gerrit.googlesource.com/gerrit/+/master/gerrit-plugin-gwtui/src/main/java/com/google/gerrit/plugin/client/PluginEntryPoint.java), but can also add dependencies on other classes like stock GWT components (e.g. [DialogBox](http://www.gwtproject.org/javadoc/latest/com/google/gwt/user/client/ui/DialogBox.html)). The class can then be integrated into the Gerrit core UI via the [RootPanel](http://www.gwtproject.org/javadoc/latest/com/google/gwt/user/client/ui/RootPanel.html) class. Once compiled, the jar file is placed into the site's plugins/ folder and the [PluginLoader](https://gerrit.googlesource.com/gerrit/+/master/gerrit-gwtui/src/main/java/com/google/gerrit/client/api/PluginLoader.java) class handles loading the plugin.

Gerrit also provides [JS API](https://gerrit-review.googlesource.com/Documentation/js-api.html) to be used for JS plugins that don't use GWT compilation. This API is used as an interface between a plugin compiled with use of [gerrit-plugin-gwtui](https://gerrit.googlesource.com/gerrit/+/master/gerrit-plugin-gwtui/src/main/java/com/google/gerrit/plugin/client/) and GWT UI running in browser. This way a plugin can work with any other implementation of JS API. In practice, the documentation does not fully describe the [existing implementation](https://bugs.chromium.org/p/gerrit/issues/detail?id=5134#c5).

Also it's worth noting that historically non-GWT plugin authors in many cases chose to [bypass the provided JS API](https://gerrit.googlesource.com/plugins/cookbook-plugin/+/75465f63548ce62a860f292f48d00596baadff81/src/main/resources/static/greetings.js#26) and carry the burden of implementing and maintaining direct DOM/CSS manipulation of Gerrit UI knowing it may and will break without warning with the future version of Gerrit instead of using a proper route of contacting Gerrit team for upgrading or fixing existing JS API.

Since PolyGerrit UI is based on a completely different UI technology (Polymer instead of GWT), providing one-to-one feature parity to plugins built this way as well as seamless migration would require significant modification of existing gerrit-plugin-gwtui and/or Gerrit GWT UI. In addition, such modification would have to be platform, browser, and version agnostic in order to handle current and future versions of both GWT UI and PolyGerrit until GWT UI is deprecated and deleted.

As of now, PolyGerrit implements part of Gerrit JS API interface and has introduced a number of experimental private APIs in order to accommodate primary clients (Chrome, Android), to gather additional use-cases and provide feature parity with code review systems that are currently used by aforementioned teams.

# Overview

This document describes changes to the existing Gerrit JavaScript API that allow plugin authors to modify and extend the behavior of PolyGerrit UI.

tl;dr: heavily based on [Web Components](http://webcomponents.org/)

After analysis of [existing Gerrit plugins](https://gerrit.googlesource.com/plugins/), it became apparent that the plugins would benefit a larger API surface than declared in existing JS API. For example, plugins need ability to build UI using same elements and styles host UI uses, and extend such elements for custom appearance or behavior. This naturally calls for using DOM as a part of API surface. (TODO See corresponding section for more detail)

At the same time, it's important to provide plugin developers with API that is stable, well defined, and minimize breaking changes with future UI development. JS API is a perfect example of that - it's easy to support and test such methods, and makes possible to swap implementation keeping same interface. However, the cost for implementing JS API as expressive as DOM is likely will be prohibitive. So it make sense to take advantage of another well-defined, stable, and robust API that already exists - Gerrit REST API. This API is already used in UI, so for simple cases (e.g. hide an action button from change actions), plugin author may chose to take advantage of decorating REST API response before it's consumed by UI. (TODO See corresponding section for more detail)

Also there are number of API changes and additions facilitating plugin development and deployment for PolyGerrit.

[Entry point](https://gerrit-review.googlesource.com/Documentation/js-api.html#_entry_point) for the plugin and the loading method is changed to follow [HTML Imports](http://w3c.github.io/webcomponents/spec/imports/) spec. index.html to be loaded from following URL by PolyGerrit:

```
gerrit-site-root/plugins/plugin-name/static/index.html
```

It expected to contain a JS script tag (inline or referenced), which in turn would follow current Gerrit JS API entry point requirements, i.e. wrapped in Gerrit.install().

PolyGerrit to provide API for simple plugin configuration via `pg-plugin-name.config` file, all properties of which will be inherited and may be overwritten on project and site level via `refs/meta/config` branch and `gerrit.config`.

[self.panel](https://gerrit-review.googlesource.com/Documentation/js-api.html#self_panel) is deprecated in the new API, and an alternative is offered.

As a better long-term alternative, PolyGerrit will provide number of UI insertion/decoration points, aiming to keep feature parity with the already defined [panels API](https://gerrit-review.googlesource.com/Documentation/dev-plugins.html#panels).

Also PolyGerrit to provide tools, samples, and guidelines on best practices for plugin development.

# Detailed design
## Packaging and installation
### Plugin file structure ###

Currently Gerrit supports two types of plugins: [.jar and .js based](https://gerrit-review.googlesource.com/Documentation/dev-plugins.html#deployment). Following changes to file structure are proposed:

The entry point instead of .js file to be index.html:

```
gerrit-site-root/plugins/plugin-name/static/index.html
```

In order to simplify migration, already existing js files may be referenced from the index.html.

For .jar-based plugins, multiple options are available:

*   bundle UI files in .jar
*   place files next to .jar in /static folder
*   combination of both

Final file structure to look like this:

```
  +--- gerrit-site-root/plugins
       +--- my-plugin/
            +--- my-plugin.config (optional)
            +--- static/
                 +--- index.html
                 +--- my-plugin.js
                 +--- my-submit-button.html (optional)
                 +--- my-submit-button.js (optional)
```

When needed, index.html should be used for [preloading](https://www.w3.org/TR/preload/) fonts, images, libraries, etc.

Minimal form for index.html to look like this:

``` html
<dom-module id="my-plugin">
  <script src="my-plugin.js"></script>
</dom-module>
```

Here `my-plugin` is a plugin name. By custom elements specification, the custom element's name must contain a dash (-).

my-plugin.js to follow supported part of [Gerrit JS plugin API](https://gerrit-review.googlesource.com/Documentation/js-api.html#_entry_point) (see migration section for specifics)

Additionally, a plugin may contain any number of html files describing individual UI elements to be inserted into PolyGerrit UI or for decorating insertion points.

### Loading and initializing ###

Polygerrit [to import](http://w3c.github.io/webcomponents/spec/imports/) top-level index.html on startup. Due to the nature of HTML Imports spec its content is never added to the DOM and thus never rendered. Plugin authors should use index.html in order to preload resources if needed and to provide an entry point for top-level script.

Polygerrit to load plugin-specific configuration and provide it to plugin via top-level API. Plugin to initialize itself on Gerrit.install().

Polygerrit to query and insert plugin-provided UI elements into corresponding extension points.

Individual plugin-provided elements are imported directly into endpoint elements.

Recommended approach for production is use Polymer + vulcanize same way PolyGerrit uses, Cookbook plugin to be updated as an example.

### (TBD) UI Blocking vs non-blocking ###

The existing spec is build on assumption that all plugins are loaded and executed after PolyGerrit start, in other words plugins don't block UI.

#### Pros: ####

*   Simplifies interface
*   Makes maintenance easier
*   Does not slow the startup

#### Cons ####

*   Potentially may result in triggering UI layouts and repaints, elements changing position etc after the page was completely loaded.

Generally, the cons side is severe enough to justify adding a way for plugin to block either part or whole PolyGerrit UI, however this probably could come after rest of the doc is stable.


## Configuration and versioning
### Configuration ###

tl;dr: Promote to API level what [chumpdetector](https://chromium.googlesource.com/infra/gerrit-plugins/chumpdetector) and [landingwidget](https://chromium.googlesource.com/infra/gerrit-plugins/landingwidget) do.

PolyGerrit to fetch default configuration for each plugin and provide it as a new method as a top-level API:

```
// in gr-public-api-interface.js:
Plugin.prototype.getConfig = function() {
  // returns combined config object.
}
```

Plugin configuration a key-value hash, created by overlapping plugin-specific configuration from following sources, in that order:

1.  pg-myplugin.config file in plugin folder
2.  gerrit.config, section [pgplugin "myplugin"]
3.  pg-myplugin.config file in refs/meta/config branch of a project, with properties being inherited from All Projects project

All following sources are to be loaded and combined by existing API endpoint.

Note that all configuration names and values are public, so if the plugin needs to hide some portion on configuration, it should implement it as a Java server-side plugin.

Here are samples, provided in lower to higher override levels:

```
# gerrit-testsite/plugins/myplugin/pg-myplugin.config
[foo]
  bar = some-initial-value
```


```
# gerrit.config
[pgplugin "myplugin foo"]
  bar = site-wide-emergency-override
```


```
# refs/meta/config / pg-myplugin.config
[foo]
  bar = project-specific-override
```


And the plugin code may look following:

``` js
Gerrit.install(function(plugin) {
  var config = plugin.getConfig();
  // config is {foo: {bar: 'project-specific-override'}}
  var bar = config.foo.bar;
});
```

TODO: Consider exposing plugin variables in admin panel (see cookbook or git-numberer)

### Versions ###

Plugin developer may declare minimal and target API version required in pg-plugin.config

Version format for PolyGerrit API is similar to npm's [semver](https://docs.npmjs.com/misc/semver) and has following format:

```
MAJOR.MINOR.PATCH
```

*   MAJOR version change indicates incompatible API changes (e.g. removal of a deprecated method)
*   MINOR version change indicates new functionality in a backwards-compatible manner (e.g. new features, deprecating part of the API, etc)
*   PATCH version change indicates backwards-compatible bug fixes

Any part of version may be replaced with x as a wildcard, and rest of version is ignored, e.g. 1.2.x matches all patch versions for version 1.2, and 1.x means any minor/patch versions for major version 1.

```
[version]
  minApiVersion = 1.2.x
  targetApiVersion = 1.3.2
```

PolyGerrit will read version numbers from plugin and take following action in case of mismatch:

*   if min api version is not satisfied, plugin is not loaded and error message is printed into JS console
*   if target version's MINOR version number differ, warnings are displayed in JS console upon plugin load and accessing deprecated methods
*   it target version's MAJOR version number differ, plugin is not loaded and error message is printed into JS console

This mechanism uses regular plugin configuration inheritance and may be used by admin as an escape hatch in case new PolyGerrit API is deployed but plugin author has not updated required API version yet.

## REST API decoration ##

Plugin author may choose to avoid modifying DOM and take advantage of stability of REST API and modify incoming REST API responses before they are used in PolyGerrit. New plugin method to be added, which invokes plugin-provided callback to modify REST request body or JSON server response. Modified object is passed further and is handled in the same way regular payload/response is.

``` js
Gerrit.install(function(plugin) {
  plugin.modifyRestResponse(method, url, function(result) {
    // Modify response here.
    result.foo = 'bar';
    return result;
  });
});
```

## DOM API
PolyGerrit to use part of DOM as an API available for extension.


### New elements ###

PolyGerrit to provide extension points for insertion/decoration via new gr-plugin-endpoint element.

Plugin to provide UI elements (as [custom elements](http://w3c.github.io/webcomponents/spec/custom/)) for insertion into extension points.

New method to be added to public JS API for registering element with an endpoint.

Appropriate instance of gr-plugin-endpoint to instantiate and setup plugin-provided UI elements.

``` html
<!-- in gr-change-view.html -->
<div class="changeInfo-column changeMetadata">
  <!-- ... -->
  <gr-plugin-endpoint name="change/info"></gr-endpoint>
  <!-- ... -->
</div>
<!-- my-submit-button.html, Polymer-style -->
<dom-element id="my-submit-button">
  <div>Plugin-provided button</div>
  <script src="my-submit-button.js"></script>
</dom-element>
```

### Decorating/modifying existing elements ###

PolyGerrit to provide description for endpoints, including elements that are instantiated and key properties for those elements.

gr-endpoint-decorator to instantiate plugin-provided UI decorator web component. Plugin-provided decorator receives a reference to element for decoration, and an event when the element was updated (created/modified/etc).

UI decorator web component can provide styles that will be imported into decorator endpoint. The intent here is to use Polymer [style modules](https://www.polymer-project.org/1.0/docs/devguide/styling#style-modules) and whatever web standard comes in to replace it.

``` html
<!-- in gr-change-actions.html -->
<template is="dom-repeat" items="[[_actions]]">
  <gr-endpoint-decorator name="[[_computeEndpointName(action.__key)]]">
    <gr-button
      title$="[[action.title]]"
      on-tap="_handleActionTap"></gr-button>
  </gr-endpoint-decorator>
</templates>
// in gr-change-actions.js
Polymer({
  is: 'gr-change-actions',

  // ...

  _computeEndpointName: function(actionKey) {
    return 'change/actions/' + actionKey;
  },
});
```

### self.panel() alternative ###

The self.panel() will be marked as `@deprecated` and will be removed in one of the following releases, with strong recommendation of using recommended approach (i.e. Web Components).

PolyGerrit to provide implementation of self.panel(), that has feature parity with existing GWT implementation.

PolyGerrit UI to have an alternative implementation of [self.panel](https://gerrit-review.googlesource.com/Documentation/js-api.html#self_panel), which will:

*   Create and register common simplistic CustomComponent
*   Invoke provided callback on connectedCallback lifecycle event (when element is attached to DOM)
*   Register the autogenerated Custom Element using standatd plugin API

This is essentially a syntactic sugar for creating custom elements. It is mostly compatible with existing self.panel(), and hides creating and registering custom elements for simple cases. This approach can also be used to make migration from GWT UI simpler.

This would enable plugin developer to do following:

``` js
// In my-plugin.js
Gerrit.install(function(plugin) {
  plugin.registerInlineElement('change/info', function() {
    // this is an Element
    var element = this;
    element.innerHTML = '<p>Lorem ipsum</p>';
  });
});
```

This should be equivalent to creating two files as per recommended approach:

``` html
<!-- in my-plugin/index.html -->
<link rel="import" href="polymer/polymer.html">
<link rel="import" href="my-lorem-ipsum.html">
<script src="my-plugin.js"></script>
// in my-plugin.js
Gerrit.install(function(plugin) {
  plugin.registerElement('change/info', 'my-lorem-ipsum');
});
<!-- in my-lorem-ipsum.html -->
<dom-module id="my-lorem-ipsum">
  <p>Lorem ipsum</p>
</dom-module>
```

Sample code for PolyGerrit interface:

``` js
// In PolyGerrit interface, gr-public-js-api.js:
Plugin.prototype.registerInlineElement = function(name, callback) {
  var pluginName = this.getPluginName();
  var hash = Math.random().toString().substr(2, 5);
  var componentName = [pluginName, name, hash].join('-');

  class PanelComponent extends Polymer.Element {
    static get is() { return componentName; }

    connectedCallback() {
      // Called every time the element is inserted into the DOM
      callback({body: this});
    }
  }

  customElements.define(PanelComponent.is, PanelComponent);

  // Register the component with standard API on plugin's behalf.
  this.registerElement(componentName, PanelComponent.is);
};

// @deprecated, provided to simplify migration
Plugin.prototype.panel = function(name, callback) {
  this.registerInlineElement(name, function() {
    var element = this;
    callback({
      body: element,
      change: element.change,
      revision: element.revision,
      plugin: element.plugin,
    });
  });
};
```

Also, the code creating a custom component wrapper for a callback can be exposed to plugin developers to provide simple way for creating basic one-shot components, which could be reused or extended later if needed.


### Styling ###

(see related [change](https://gerrit.googlesource.com/gerrit/+/refs/changes/50/96550/4/polygerrit-ui/doc/gr-plugin-styling.md))

PolyGerrit loads shared styles using style modules approach and applies them to the document level This makes possible for plugins to provide custom global CSS variables in form of CSS mixins. gr-change-metadata applies plugin-provided mixins to specific sections of DOM to hide them.

Related Polymer API:

https://www.polymer-project.org/1.0/docs/devguide/styling#custom-style

Sample code for using proposed API, in plugin.js:

``` js
Gerrit.install(function(plugin) {
  var stylesUrl = document.currentScript.src.replace(
    /\/[^\/]+\.js/, '/plugin-style.html');
  plugin.registerStyleModule('change-metadata', stylesUrl);
});
```

Please note that plugin-style has to be unique, so plugin name should be used.

Sample code for using proposed API, in plugin-style.html:

``` html
<dom-module id="plugin-style">
  <template>
    <style>
      :root {
        --change-metadata-assignee: {
          display: none;
        }
        --change-metadata-label-status: {
          display: none;
        }
        --change-metadata-strategy: {
          display: none;
        }
        --change-metadata-topic: {
          display: none;
        }
      }
    </style>
  </template>
</dom-module>
```

### Interacting with PolyGerrit UI and inside plugin ###

Each plugin-provided UI element is a [Custom Element](http://w3c.github.io/webcomponents/spec/custom/). Each UI element is defined in a separate html file. Upon creation, each UI element get following attributes set:

1.  a reference to top-level plugin object (self returned into Gerrit.install callback)
2.  DOM element for decoration
3.  change, revision, etc - depending on availability

``` html
<!-- sample code for the purpose of illustrating structure -->
<gr-plugin-endpoint name="change/info">
  <my-submit-button
    plugin-context="[[plugin]]">
    <gr-button
      primary
      data-label="Submit"></gr-button>
  </my-submit-button>
</gr-endpoint>
```

### Plugins reacting to events ###

Since plugin is expected to consist of number of separate UI elements, it's important to provide a way for any part of plugin to react to PolyGerrit event or action.

gr-plugin-endpoint sends DOM events for meta-level actions (comment created, change description updated, etc) into all plugin-provided UI elements, so any element can react to any/all events if needed.

## UI Toolkit ##

Project polygerrit-ui-toolkit to be created and to contain as many of independent PolyGerrit UI elements as possible (e.g. [gr-button](https://gerrit.googlesource.com/gerrit/+/master/polygerrit-ui/app/elements/shared/gr-button/)).

UI Toolkit to be minified independently from main PolyGerrit app, and its development to use same tools and technologies as main PolyGerrit app (eg, Polymer, vulcanize, etc).

PolyGerrit app to import minified toolkit via common url and use elements from it.

Plugin author may choose to import the toolkit as well to use or extend elements provided.

UI Toolkit to provide a minimal skeleton plugin that could be copied and filled in for a quick start.

For recommended approach on using UI toolkit, see Cookbook plugin as an example.

## Plugin development (samples, tools, building, testing, and best practices)
### Plugin development ###

UI Toolkit to contain sample plugin skeleton for quick start.

run-server.go to be modified to take params and overwrite available plugins, also to serve plugin files locally.

### Plugin testing ###

There are no hard requirements for plugins to have any kinds of test suite. However, it's strongly encouraged to have a test suite for each custom component and main plugin script itself.

Recommended approach is use same methods and practices PolyGerrit uses, ie Polymer + WCT.

For recommended way for writing tests, see Cookbook plugin as an example.

### Sample Cookbook plugin ###

Build an alternative Cookbook plugin to show what's different from previous version and how to do same things in the new one.

Topics to cover:

*   vulcanization/minification
*   reusing PolyGerrit components
*   extending PolyGerrit components
*   inserting elements
*   decorating elements
*   tests
*   deployment
*   documentation

# Migration
## Existing plugin

[gerrit-plugin-gwtui](https://gerrit.googlesource.com/gerrit/+/master/gerrit-plugin-gwtui/) is not supported. PolyGerrit aims to provide equal or better feature parity eventually, but existing GWT plugins wouldn't work directly.

Existing [Gerrit JS API](https://gerrit-review.googlesource.com/Documentation/js-api.html) will be mostly supported. Here's the list of notable changes (not final):

*   Top menu links are not supported, eg Gerrit.refreshMenuBar() does nothing
*   Gerrit.html() and Gerrit.css() will be local to extension point
*   URL structure is changed:
    *   some of Gerrit.go() links need to be updated
    *   some of Gerrit.screen() handling needs to be updated
*   [extension points](https://gerrit-review.googlesource.com/Documentation/dev-plugins.html#panels) will be different
*   context.popup() will be handled differently, maybe as full-screen dialog

The plugin should just include index.html of minimal form:

``` html
<dom-module id="my-plugin">
  <script src="my-plugin.js"></script>
</dom-module>
```

If plugin uses document.querySelector(), the author should consider using  [webcomponents](#bookmark=id.anpdz31xjzr5) or self.panel(). If the existing support is insufficient for author's needs, the author should file a bug against PolyGerrit team to implement what's missing or provide guidance on best approach.

If plugin uses panels, two options are available:

*   Short term: migrate to PolyGerrit extension points, generate new DOM structure, and keep using self.panel() until it's no longer supported.
*   Long term: Migrate UI to [webcomponents](#bookmark=id.anpdz31xjzr5).

If plugin relies on URL structure, the author should test and update it if needed.

If plugin uses popup(), plugin author should ensure new UI looks appropriate.
