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

<link rel="import" href="/bower_components/iron-input/iron-input.html">
<link rel="import" href="/bower_components/paper-toggle-button/paper-toggle-button.html">
<link rel="import" href="../../../styles/gr-form-styles.html">
<link rel="import" href="../../../styles/shared-styles.html">
<link rel="import" href="../../shared/gr-button/gr-button.html">

<dom-module id="gr-plugin-config-array-editor">
  <template>
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-form-styles">
      .wrapper {
        width: 30em;
      }
      .existingItems {
        background: var(--table-header-background-color);
        border: 1px solid var(--border-color);
        border-radius: var(--border-radius);
      }
      gr-button {
        float: right;
        margin-left: var(--spacing-m);
        width: 4.5em;
      }
      .row {
        align-items: center;
        display: flex;
        justify-content: space-between;
        padding: var(--spacing-m) 0;
        width: 100%;
      }
      .existingItems .row {
        padding: var(--spacing-m);
      }
      .existingItems .row:not(:first-of-type) {
        border-top: 1px solid var(--border-color);
      }
      input {
        flex-grow: 1;
      }
      .hide {
        display: none;
      }
      .placeholder {
        color: var(--deemphasized-text-color);
        padding-top: var(--spacing-m);
      }
    </style>
    <div class="wrapper gr-form-styles">
      <template is="dom-if" if="[[pluginOption.info.values.length]]">
        <div class="existingItems">
          <template is="dom-repeat" items="[[pluginOption.info.values]]">
            <div class="row">
              <span>[[item]]</span>
              <gr-button
                  link
                  disabled$="[[disabled]]"
                  data-item$="[[item]]"
                  on-click="_handleDelete">Delete</gr-button>
            </div>
          </template>
        </div>
      </template>
      <template is="dom-if" if="[[!pluginOption.info.values.length]]">
        <div class="row placeholder">None configured.</div>
      </template>
      <div class$="row [[_computeShowInputRow(disabled)]]">
        <iron-input
            on-keydown="_handleInputKeydown"
            bind-value="{{_newValue}}">
          <input
              is="iron-input"
              id="input"
              on-keydown="_handleInputKeydown"
              bind-value="{{_newValue}}">
        </iron-input>
        <gr-button
            id="addButton"
            disabled$="[[!_newValue.length]]"
            link
            on-click="_handleAddTap">Add</gr-button>
      </div>
    </div>
  </template>
  <script src="gr-plugin-config-array-editor.js"></script>
</dom-module>
