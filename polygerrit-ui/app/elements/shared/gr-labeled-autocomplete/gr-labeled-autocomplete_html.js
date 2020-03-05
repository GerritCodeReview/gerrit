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
<link rel="import" href="../../shared/gr-autocomplete/gr-autocomplete.html">
<link rel="import" href="../../../styles/shared-styles.html">

<dom-module id="gr-labeled-autocomplete">
  <template>
    <style include="shared-styles">
      :host {
        display: block;
        width: 12em;
      }
      #container {
        background: var(--chip-background-color);
        border-radius: 1em;
        padding: var(--spacing-m);
      }
      #header {
        color: var(--deemphasized-text-color);
        font-weight: var(--font-weight-bold);
        font-size: var(--font-size-small);
      }
      #body {
        display: flex;
      }
      #trigger {
        color: var(--deemphasized-text-color);
        cursor: pointer;
        padding-left: var(--spacing-s);
      }
      #trigger:hover {
        color: var(--primary-text-color);
      }
    </style>
    <div id="container">
      <div id="header">[[label]]</div>
      <div id="body">
        <gr-autocomplete
            id="autocomplete"
            threshold="[[_autocompleteThreshold]]"
            query="[[query]]"
            disabled="[[disabled]]"
            placeholder="[[placeholder]]"
            borderless></gr-autocomplete>
        <div id="trigger" on-click="_handleTriggerClick">▼</div>
      </div>
    </div>
  </template>
  <script src="gr-labeled-autocomplete.js"></script>
</dom-module>
