---
title: " Gerrit Code Review - PolyGerrit Plugin Development"
sidebar: gerritdoc_sidebar
permalink: dev-plugins-pg.html
---
> **Caution**
> 
> Work in progress. Hard hat area.  
> This document will be populated with details along with
> implementation.  
> [Join the
> discussion.](https://groups.google.com/d/topic/repo-discuss/vb8WJ4m0hK0/discussion)

## Plugin loading and initialization

[Entry
point](https://gerrit-review.googlesource.com/Documentation/js-api.html#_entry_point)
for the plugin and the loading method is based on [HTML
Imports](http://w3c.github.io/webcomponents/spec/imports/) spec.

  - The plugin provides index.html, similar to [.js Web UI
    plugins](https://gerrit-review.googlesource.com/Documentation/dev-plugins.html#deployment)

  - index.html contains a `dom-module` tag with a script that uses
    `Gerrit.install()`.

  - PolyGerrit imports index.html along with all required resources
    defined in it (fonts, styles, etc)

  - For standalone plugins, the entry point file is a `pluginname.html`
    file located in `gerrit-site/plugins` folder, where `pluginname` is
    an alphanumeric plugin name.

Here’s a sample `myplugin.html`:

\`\`\` html \<dom-module id="my-plugin"\> \<script\>
Gerrit.install(function() { console.log(*Ready.*); }); \</script\>
\</dom-module\> \`\`\`

## Low-level DOM API

Basically, the DOM is the API surface. Low-level API provides methods
for decorating, replacing, and styling DOM elements exposed through a
set of endpoints.

PolyGerrit provides a simple way for accessing the DOM via DOM hooks
API. A DOM hook is a custom element that is instantiated for the plugin
endpoint. In the decoration case, a hook is set with a `content`
attribute that points to the DOM element.

1.  Get the DOM hook API instance via `plugin.hook(endpointName)`

2.  Set up an `onAttached` callback

3.  Callback is called when the hook element is created and inserted
    into DOM

4.  Use element.content to get UI element

\`\`\` js Gerrit.install(function(plugin) { const domHook =
plugin.hook(*reply-text*); domHook.onAttached(element ⇒ { if
(\!element.content) { return; } // element.content is a reply dialog
text area. }); }); \`\`\`

### Decorating DOM Elements

For each endpoint, PolyGerrit provides a list of DOM properties (such as
attributes and events) that are supported in the long-term.

> **Note**
> 
> TODO: Insert link to the full endpoints API.

\`\`\` js Gerrit.install(function(plugin) { const domHook =
plugin.hook(*reply-text*); domHook.onAttached(element ⇒ { if
(\!element.content) { return; } element.content.style.border = *1px red
dashed*; }); }); \`\`\`

### Replacing DOM Elements

An endpoint’s contents can be replaced by passing the replace attribute
as an option.

\`\`\` js Gerrit.install(function(plugin) { domHook.onAttached(element ⇒
{ element.appendChild(document.createElement(*my-site-header*)); }); });
\`\`\`

### Styling DOM Elements

A plugin may provide Polymer’s [style
modules](https://www.polymer-project.org/2.0/docs/devguide/style-shadow-dom#style-modules)
to style individual endpoints using
`plugin.registerStyleModule(endpointName, moduleName)`. A style must be
defined as a standalone `<dom-module>` defined in the same .html file.

Note: TODO: Insert link to the full styling API.

\`\`\` html \<dom-module id="my-plugin"\> \<script\>
Gerrit.install(function(plugin) {
plugin.registerStyleModule(*change-metadata*, *some-style-module*); });
\</script\> \</dom-module\>

\<dom-module id="some-style-module"\> \<style\> html {
--change-metadata-label-status: { display: none; }
--change-metadata-strategy: { display: none; } } \</style\>
\</dom-module\> \`\`\`

