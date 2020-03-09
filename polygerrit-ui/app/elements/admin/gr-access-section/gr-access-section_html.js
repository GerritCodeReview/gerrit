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

<link rel="import" href="../../../behaviors/fire-behavior/fire-behavior.html">
<link rel="import" href="../../../behaviors/gr-access-behavior/gr-access-behavior.html">
<link rel="import" href="/bower_components/iron-input/iron-input.html">
<link rel="import" href="../../../styles/gr-form-styles.html">
<link rel="import" href="../../../styles/shared-styles.html">
<link rel="import" href="../../shared/gr-button/gr-button.html">
<link rel="import" href="../../shared/gr-icons/gr-icons.html">
<link rel="import" href="../../shared/gr-rest-api-interface/gr-rest-api-interface.html">
<link rel="import" href="../gr-permission/gr-permission.html">

<script src="../../../scripts/util.js"></script>

<dom-module id="gr-access-section">
  <template>
    <style include="shared-styles">
      :host {
        display: block;
        margin-bottom: var(--spacing-l);
      }
      fieldset {
        border: 1px solid var(--border-color);
      }
      .name {
        align-items: center;
        display: flex;
      }
      .header,
      #deletedContainer {
        align-items: center;
        background: var(--table-header-background-color);
        border-bottom: 1px dotted var(--border-color);
        display: flex;
        justify-content: space-between;
        min-height: 3em;
        padding: 0 var(--spacing-m);
      }
      #deletedContainer {
        border-bottom: 0;
      }
      .sectionContent {
        padding: var(--spacing-m);
      }
      #editBtn,
      .editing #editBtn.global,
      #deletedContainer,
      .deleted #mainContainer,
      #addPermission,
      #deleteBtn,
      .editingRef .name,
      .editRefInput {
        display: none;
      }
      .editing #editBtn,
      .editingRef .editRefInput {
        display: flex;
      }
      .deleted #deletedContainer {
        display: flex;
      }
      .editing #addPermission,
      #mainContainer,
      .editing #deleteBtn  {
        display: block;
      }
      .editing #deleteBtn,
      #undoRemoveBtn {
        padding-right: var(--spacing-m);
      }
    </style>
    <style include="gr-form-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <fieldset id="section"
        class$="gr-form-styles [[_computeSectionClass(editing, canUpload, ownerOf, _editingRef, _deleted)]]">
      <div id="mainContainer">
        <div class="header">
          <div class="name">
            <h3>[[_computeSectionName(section.id)]]</h3>
            <gr-button
                id="editBtn"
                link
                class$="[[_computeEditBtnClass(section.id)]]"
                on-click="editReference">
              <iron-icon id="icon" icon="gr-icons:create"></iron-icon>
            </gr-button>
          </div>
          <iron-input
              class="editRefInput"
              bind-value="{{section.id}}"
              type="text"
              on-input="_handleValueChange">
            <input
                class="editRefInput"
                bind-value="{{section.id}}"
                is="iron-input"
                type="text"
                on-input="_handleValueChange">
          </iron-input>
          <gr-button
              link
              id="deleteBtn"
              on-click="_handleRemoveReference">Remove</gr-button>
        </div><!-- end header -->
        <div class="sectionContent">
          <template
              is="dom-repeat"
              items="{{_permissions}}"
              as="permission">
            <gr-permission
                name="[[_computePermissionName(section.id, permission, permissionValues, capabilities)]]"
                permission="{{permission}}"
                labels="[[labels]]"
                section="[[section.id]]"
                editing="[[editing]]"
                groups="[[groups]]"
                on-added-permission-removed="_handleAddedPermissionRemoved">
            </gr-permission>
          </template>
          <div id="addPermission">
            Add permission:
            <select id="permissionSelect">
              <!-- called with a third parameter so that permissions update
                  after a new section is added. -->
              <template
                  is="dom-repeat"
                  items="[[_computePermissions(section.id, capabilities, labels, section.value.permissions.*)]]">
                <option value="[[item.value.id]]">[[item.value.name]]</option>
              </template>
            </select>
            <gr-button
                link
                id="addBtn"
                on-click="_handleAddPermission">Add</gr-button>
          </div>
          <!-- end addPermission -->
        </div><!-- end sectionContent -->
      </div><!-- end mainContainer -->
      <div id="deletedContainer">
        <span>[[_computeSectionName(section.id)]] was deleted</span>
        <gr-button
            link
            id="undoRemoveBtn"
            on-click="_handleUndoRemove">Undo</gr-button>
      </div><!-- end deletedContainer -->
    </fieldset>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
  </template>
  <script src="gr-access-section.js"></script>
</dom-module>
