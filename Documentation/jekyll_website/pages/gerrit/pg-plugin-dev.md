---
title: " Gerrit Code Review - PolyGerrit Plugin Development"
sidebar: gerritdoc_sidebar
permalink: pg-plugin-dev.html
---
> **Caution**
> 
> Work in progress. Hard hat area. Please [send
> feedback](https://bugs.chromium.org/p/gerrit/issues/entry?template=PolyGerrit%20plugins)
> if something’s not right.

## Plugin loading and initialization

[Entry point](js-api.html#_entry_point) for the plugin and the loading
method is based on [HTML
Imports](http://w3c.github.io/webcomponents/spec/imports/) spec.

  - The plugin provides pluginname.html, and can be a standalone file or
    a static asset in a jar as a [Web UI
    plugin](dev-plugins.html#deployment).

  - pluginname.html contains a `dom-module` tag with a script that uses
    `Gerrit.install()`. There should only be single `Gerrit.install()`
    per file.

  - PolyGerrit imports pluginname.html along with all required resources
    defined in it (fonts, styles, etc).

  - For standalone plugins, the entry point file is a `pluginname.html`
    file located in `gerrit-site/plugins` folder, where `pluginname` is
    an alphanumeric plugin name.

Note: Code examples target modern brosers (Chrome, Firefox, Safari,
Edge)

Here’s a recommended starter `myplugin.html`:

\`\`\` html \<dom-module id="my-plugin"\> \<script\>
Gerrit.install(plugin ⇒ { *use strict*;

```` 
      // Your code here.
    });
  </script>
</dom-module>
```
````

## Low-level DOM API concepts

Basically, the DOM is the API surface. Low-level API provides methods
for decorating, replacing, and styling DOM elements exposed through a
set of [endpoints](pg-plugin-endpoints.html).

PolyGerrit provides a simple way for accessing the DOM via DOM hooks
API. A DOM hook is a custom element that is instantiated for the plugin
endpoint. In the decoration case, a hook is set with a `content`
attribute that points to the DOM element.

1.  Get the DOM hook API instance via `plugin.hook(endpointName)`

2.  Set up an `onAttached` callback

3.  Callback is called when the hook element is created and inserted
    into DOM

4.  Use element.content to get UI element

\`\`\` js Gerrit.install(plugin ⇒ { const domHook =
plugin.hook(*reply-text*); domHook.onAttached(element ⇒ { if
(\!element.content) { return; } // element.content is a reply dialog
text area. }); }); \`\`\`

### Decorating DOM Elements

For each endpoint, PolyGerrit provides a list of DOM properties (such as
attributes and events) that are supported in the long-term.

\`\`\` js Gerrit.install(plugin ⇒ { const domHook =
plugin.hook(*reply-text*); domHook.onAttached(element ⇒ { if
(\!element.content) { return; } element.content.style.border = *1px red
dashed*; }); }); \`\`\`

### Replacing DOM Elements

An endpoint’s contents can be replaced by passing the replace attribute
as an option.

\`\`\` js Gerrit.install(plugin ⇒ { domHook.onAttached(element ⇒ {
element.appendChild(document.createElement(*my-site-header*)); }); });
\`\`\`

### Styling DOM Elements

A plugin may provide Polymer’s [style
modules](https://www.polymer-project.org/2.0/docs/devguide/style-shadow-dom#style-modules)
to style individual endpoints using
`plugin.registerStyleModule(endpointName, moduleName)`. A style must be
defined as a standalone `<dom-module>` defined in the same .html file.

Note: TODO: Insert link to the full styling API.

\`\`\` html \<dom-module id="my-plugin"\> \<script\>
Gerrit.install(plugin ⇒ { plugin.registerStyleModule(*change-metadata*,
*some-style-module*); }); \</script\> \</dom-module\>

\<dom-module id="some-style-module"\> \<style\> html {
--change-metadata-label-status: { display: none; }
--change-metadata-strategy: { display: none; } } \</style\>
\</dom-module\> \`\`\`

## High-level DOM API concepts

High leve API is based on low-level DOM API and is essentially a
standartized way for doing common tasks. It’s less flexible, but will be
a bit more stable.

Common way to access high-leve API is through `plugin` instance passed
into setup callback parameter of `Gerrit.install()`, also sometimes
referred as `self`.

## Low-level DOM API

Low-level DOM API methods are the base of all UI customization.

### attributeHelper

`plugin.attributeHelper(element)`

Note: TODO

### eventHelper

`plugin.eventHelper(element)`

Note: TODO

### hook

`plugin.hook(endpointName, opt_options)`

See list of supported [endpoints](pg-plugin-endpoints.html). Note: TODO

### registerCustomComponent

`plugin.registerCustomComponent(endpointName, opt_moduleName,
opt_options)`

Note: TODO

### registerStyleModule

`plugin.registerStyleModule(endpointName, moduleName)`

Note: TODO

## High-level API

Plugin instance provides access to number of more specific APIs and
methods to be used by plugin authors.

### changeReply

`plugin.changeReply()`

Note: TODO

### changeView

`plugin.changeView()`

Note: TODO

### delete

`plugin.delete(url, opt_callback)`

Note: TODO

### get

`plugin.get(url, opt_callback)`

Note: TODO

### getPluginName

`plugin.getPluginName()`

Note: TODO

### getServerInfo

`plugin.getServerInfo()`

Note: TODO

### on

`plugin.on(eventName, callback)`

Note: TODO

### popup

`plugin.popup(moduleName)`

Note: TODO

### post

`plugin.post(url, payload, opt_callback)`

Note: TODO

`plugin.project()`

  - none

<!-- end list -->

  - Instance of [GrProjectApi](pg-plugin-project-api.html).

### put

`plugin.put(url, payload, opt_callback)`

Note: TODO

### theme

`plugin.theme()`

Note: TODO

### url

`plugin.url(opt_path)`

Note: TODO

