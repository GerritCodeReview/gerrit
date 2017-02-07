PolyGerrit Plugin styling
===
- by: viktard@google.com
- status: Draft

Objective
---
PolyGerrit plugins need to provide styling capabilities to PolyGerrit interface.
Typical example is hiding arbitrary part of UI.

Background
---
For Chrome team, not all section in change metadata [are relevant](https://bugs.chromium.org/p/gerrit/issues/detail?id=5402).

Requirements and Scale
---
- Ability to hide arbitrary elements in UI
- Configurable at project level

Design Ideas
---
See [the code](https://gerrit-review.googlesource.com/c/96550/).

PolyGerrit loads shared styles using style modules approach and applies
them to the document level
This makes possible for plugins to provide custom global CSS variables
in form of CSS mixins.
gr-change-metadata applies plugin-provided mixins to specific sections
of DOM to hide them.

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

Please note that `plugin-style` has to be unique, so plugin name should be used.

Sample code for using proposed API, in plugin-style.html

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

Alternatives Considered
---

### Pure JS API ###

PolyGerrit to imlement JS API methods specifically to hide aforementioned sections, eg:

``` js
Gerrit.install(function(plugin) {
    Gerrit.hideElement('CHANGE_METADATA.TOPIC');
    Gerrit.hideElement('CHANGE_METADATA.STRATEGY');
    Gerrit.hideElement('CHANGE_METADATA.LABEL_STATUS');
    Gerrit.hideElement('CHANGE_METADATA.ASSIGNEE');
});
```

Rejected because this design does not scale.
This appoach solves the immediate case, but addning more actions (styling, layout, etc) or places (other elements at change view, other places) would significantly increase maintenance cost and make adding new features more complicated.
It will eventually result in building UI model of the entire application over time.
