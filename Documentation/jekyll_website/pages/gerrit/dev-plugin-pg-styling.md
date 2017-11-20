---
title: " Gerrit Code Review - PolyGerrit Plugin Styling"
sidebar: gerritdoc_sidebar
permalink: dev-plugin-pg-styling.html
---
> **Caution**
> 
> Work in progress. Hard hat area.  
> This document will be populated with details along with
> implementation.  
> [Join the
> discussion.](https://groups.google.com/d/topic/repo-discuss/vb8WJ4m0hK0/discussion)

## Plugin styles

Plugins may provide [Polymer style
modules](https://www.polymer-project.org/2.0/docs/devguide/style-shadow-dom#style-modules)
for UI CSS-based customization.

PolyGerrit UI implements number of styling endpoints, which apply CSS
mixins [using @apply](https://tabatkins.github.io/specs/css-apply-rule/)
to its direct contents.

> **Note**
> 
> Only items (ie CSS properties and mixin targets) documented here are
> guaranteed to work in the long term, since they are covered by
> integration tests.  
> When there is a need to add new property or endpoint, please [file a
> bug](https://bugs.chromium.org/p/gerrit/issues/entry?template=PolyGerrit%20Issue)
> stating your usecase to track and maintain for future releases.

Plugin should be html-based and imported following PolyGerrit’s [dev
guide](dev-plugins-pg.html#loading).

Plugin should provide Style Module, for example:

\`\`\` html \<dom-module id="some-style"\> \<style\> :root {
--css-mixin-name: { property: value; } } \</style\> \</dom-module\>
\`\`\`

Plugin should register style module with a styling endpoint using
`Plugin.prototype.registerStyleModule(endpointName, styleModuleName)`,
for example:

\`\`\` js Gerrit.install(function(plugin) {
plugin.registerStyleModule(*some-endpoint*, *some-style*); }); \`\`\`

## Available styling endpoints

### change-metadata

Following custom css mixins are recognized:

  - `--change-metadata-assignee`
    
    is applied to `gr-change-metadata section.assignee` \*
    `--change-metadata-label-status`
    
    is applied to `gr-change-metadata section.labelStatus` \*
    `--change-metadata-strategy`
    
    is applied to `gr-change-metadata section.strategy` \*
    `--change-metadata-topic`
    
    is applied to `gr-change-metadata section.topic`

Following CSS properties have [long-term support via integration
test](https://gerrit.googlesource.com/gerrit/+/master/polygerrit-ui/app/elements/change/gr-change-metadata/gr-change-metadata-it_test.html):

  - `display`
    
    can be set to `none` to hide a section.

