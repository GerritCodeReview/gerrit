---
title: " Gerrit Code Review - PolyGerrit Plugin Styling"
sidebar: gerritdoc_sidebar
permalink: pg-plugin-endpoints.html
---
Plugin should be html-based and imported following PolyGerrit’s [dev
guide](pg-plugin-dev.html#loading).

Sample code for testing endpoints:

\`\`\` js Gerrit.install(plugin ⇒ { // Change endpoint below const
endpoint = *change-metadata-item*;
plugin.hook(endpoint).onAttached(element ⇒ { console.log(endpoint,
element); const el = element.appendChild(document.createElement(*div*));
el.textContent = *Ah, there it is. Lovely.*; el.style = *background:
pink; line-height: 4em; text-align: center;*; }); }); \`\`\`

## Default parameters

All endpoints receive the following params, set as attributes to custom
components that are instantiated at the endpoint:

  - `plugin`
    
    the current plugin instance, the one that is used by
    `Gerrit.install()`.

  - `content`
    
    decorated DOM Element, is only set for registrations that decorate
    existing components.

## Plugin endpoints

Following endpoints are available to plugins

### change-view-integration

Extension point is located between `Files` and `Messages` section on the
change view page, and it may take full page’s width. Primary purpose is
to enable plugins to display custom CI related information (build
status, etc).

  - `change`
    
    current change displayed, an instance of
    [ChangeInfo](rest-api-changes.html#change-info)

  - `revision`
    
    current revision displayed, an instance of
    [RevisionInfo](rest-api-changes.html#revision-info)

### change-metadata-item

Extension point is located on the bottom of the change view left panel,
under `Label Status` and `Links` sections. It’s width is equal to the
left panel’s and primary purpose is to enable plugins to add sections of
metadata to the left panel.

In addition to default parameters, the following are available:

  - `change`
    
    current change displayed, an instance of
    [ChangeInfo](rest-api-changes.html#change-info)

  - `revision`
    
    current revision displayed, an instance of
    [RevisionInfo](rest-api-changes.html#revision-info)

