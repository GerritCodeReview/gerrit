<!--
@license
Copyright (C) 2018 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

<link rel="import" href="/bower_components/polymer/polymer.html">
<link rel="import" href="/bower_components/iron-icon/iron-icon.html">
<link rel="import" href="../../../styles/shared-styles.html">
<link rel="import" href="../../shared/gr-button/gr-button.html">
<link rel="import" href="../../shared/gr-rest-api-interface/gr-rest-api-interface.html">

<dom-module id="gr-diff-mode-selector">
  <template>
    <style include="shared-styles">
      :host {
        /* Used to remove horizontal whitespace between the icons. */
        display: flex;
      }
      gr-button.selected iron-icon {
        color: var(--link-color);
      }
      iron-icon {
        height: 1.3rem;
        width: 1.3rem;
      }
    </style>
    <gr-button
        id="sideBySideBtn"
        link
        has-tooltip
        class$="[[_computeSelectedClass(mode, _VIEW_MODES.SIDE_BY_SIDE)]]"
        title="Side-by-side diff"
        on-click="_handleSideBySideTap">
      <iron-icon icon="gr-icons:side-by-side"></iron-icon>
    </gr-button>
    <gr-button
        id="unifiedBtn"
        link
        has-tooltip
        title="Unified diff"
        class$="[[_computeSelectedClass(mode, _VIEW_MODES.UNIFIED)]]"
        on-click="_handleUnifiedTap">
      <iron-icon icon="gr-icons:unified"></iron-icon>
    </gr-button>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
  </template>
  <script src="gr-diff-mode-selector.js"></script>
</dom-module>
