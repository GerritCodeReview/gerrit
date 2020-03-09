<!--
@license
Copyright (C) 2017 The Android Open Source Project

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
<link rel="import" href="/bower_components/iron-autogrow-textarea/iron-autogrow-textarea.html">

<link rel="import" href="../../../behaviors/base-url-behavior/base-url-behavior.html">
<link rel="import" href="../../../behaviors/fire-behavior/fire-behavior.html">
<link rel="import" href="../../../behaviors/gr-access-behavior/gr-access-behavior.html">
<link rel="import" href="../../../behaviors/gr-url-encoding-behavior/gr-url-encoding-behavior.html">
<link rel="import" href="../../../styles/gr-form-styles.html">
<link rel="import" href="../../../styles/shared-styles.html">
<link rel="import" href="../../shared/gr-button/gr-button.html">
<link rel="import" href="../../shared/gr-select/gr-select.html">
<link rel="import" href="../../shared/gr-rest-api-interface/gr-rest-api-interface.html">

<dom-module id="gr-rule-editor">
  <template>
    <style include="shared-styles">
      :host {
        border-bottom: 1px solid var(--border-color);
        padding: var(--spacing-m);
        display: block;
      }
      #removeBtn {
        display: none;
      }
      .editing #removeBtn  {
        display: flex;
      }
      #options {
        align-items: baseline;
        display: flex;
      }
      #options > * {
        margin-right: var(--spacing-m);
      }
      #mainContainer {
        align-items: baseline;
        display: flex;
        flex-wrap: nowrap;
        justify-content: space-between;
      }
      #deletedContainer.deleted {
        align-items: baseline;
        display: flex;
        justify-content: space-between;
      }
      #undoBtn,
      #force,
      #deletedContainer,
      #mainContainer.deleted {
        display: none;
      }
      #undoBtn.modified,
      #force.force {
        display: block;
      }
      .groupPath {
        color: var(--deemphasized-text-color);
      }
    </style>
    <style include="gr-form-styles">
      iron-autogrow-textarea {
        width: 14em;
      }
    </style>
    <div id="mainContainer"
        class$="gr-form-styles [[_computeSectionClass(editing, _deleted)]]">
      <div id="options">
        <gr-select id="action"
            bind-value="{{rule.value.action}}"
            on-change="_handleValueChange">
          <select disabled$="[[!editing]]">
            <template is="dom-repeat" items="[[_computeOptions(permission)]]">
              <option value="[[item]]">[[item]]</option>
            </template>
          </select>
        </gr-select>
        <template is="dom-if" if="[[label]]">
          <gr-select
              id="labelMin"
              bind-value="{{rule.value.min}}"
              on-change="_handleValueChange">
            <select disabled$="[[!editing]]">
              <template is="dom-repeat" items="[[label.values]]">
                <option value="[[item.value]]">[[item.value]]</option>
              </template>
            </select>
          </gr-select>
          <gr-select
              id="labelMax"
              bind-value="{{rule.value.max}}"
              on-change="_handleValueChange">
            <select disabled$="[[!editing]]">
              <template is="dom-repeat" items="[[label.values]]">
                <option value="[[item.value]]">[[item.value]]</option>
              </template>
            </select>
          </gr-select>
        </template>
        <template is="dom-if" if="[[hasRange]]">
          <iron-autogrow-textarea
              id="minInput"
              class="min"
              autocomplete="on"
              placeholder="Min value"
              bind-value="{{rule.value.min}}"
              disabled$="[[!editing]]"></iron-autogrow-textarea>
          <iron-autogrow-textarea
              id="maxInput"
              class="max"
              autocomplete="on"
              placeholder="Max value"
              bind-value="{{rule.value.max}}"
              disabled$="[[!editing]]"></iron-autogrow-textarea>
        </template>
        <a class="groupPath" href$="[[_computeGroupPath(groupId)]]">
          [[groupName]]
        </a>
        <gr-select
            id="force"
            class$="[[_computeForceClass(permission, rule.value.action)]]"
            bind-value="{{rule.value.force}}"
            on-change="_handleValueChange">
          <select disabled$="[[!editing]]">
            <template
                is="dom-repeat"
                items="[[_computeForceOptions(permission, rule.value.action)]]">
              <option value="[[item.value]]">[[item.name]]</option>
            </template>
          </select>
        </gr-select>
      </div>
      <gr-button
          link
          id="removeBtn"
          on-click="_handleRemoveRule">Remove</gr-button>
    </div>
    <div
        id="deletedContainer"
        class$="gr-form-styles [[_computeSectionClass(editing, _deleted)]]">
      [[groupName]] was deleted
      <gr-button link
          id="undoRemoveBtn" on-click="_handleUndoRemove">Undo</gr-button>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
  </template>
  <script src="gr-rule-editor.js"></script>
</dom-module>
