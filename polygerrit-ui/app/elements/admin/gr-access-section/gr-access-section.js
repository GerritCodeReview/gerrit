/**
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
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../behaviors/gr-access-behavior/gr-access-behavior.js';
import '../../../../@polymer/iron-input/iron-input.js';
import '../../../styles/gr-form-styles.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-icons/gr-icons.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../gr-permission/gr-permission.js';
/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function(window) {
  'use strict';

  const util = window.util || {};

  util.parseDate = function(dateStr) {
    // Timestamps are given in UTC and have the format
    // "'yyyy-mm-dd hh:mm:ss.fffffffff'" where "'ffffffffff'" represents
    // nanoseconds.
    // Munge the date into an ISO 8061 format and parse that.
    return new Date(dateStr.replace(' ', 'T') + 'Z');
  };

  util.getCookie = function(name) {
    const key = name + '=';
    const cookies = document.cookie.split(';');
    for (let i = 0; i < cookies.length; i++) {
      let c = cookies[i];
      while (c.charAt(0) === ' ') {
        c = c.substring(1);
      }
      if (c.startsWith(key)) {
        return c.substring(key.length, c.length);
      }
    }
    return '';
  };
  window.util = util;
})(window);

/**
 * Fired when the section has been modified or removed.
 *
 * @event access-modified
 */

/**
 * Fired when a section that was previously added was removed.
 * @event added-section-removed
 */

const GLOBAL_NAME = 'GLOBAL_CAPABILITIES';

// The name that gets automatically input when a new reference is added.
const NEW_NAME = 'refs/heads/*';
const REFS_NAME = 'refs/';
const ON_BEHALF_OF = '(On Behalf Of)';
const LABEL = 'Label';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      :host {
        display: block;
        margin-bottom: 1em;
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
        padding: 0 .7em;
      }
      #deletedContainer {
        border-bottom: 0;
      }
      .sectionContent {
        padding: .7em;
      }
      #editBtn,
      .editing #editBtn.global,
      #deletedContainer,
      .deleted #mainContainer,
      #addPermission,
      #deleteBtn,
      .editingRef .name,
      #editRefInput {
        display: none;
      }
      .editing #editBtn,
      .editingRef #editRefInput {
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
        padding-right: .7em;
      }
    </style>
    <style include="gr-form-styles"></style>
    <fieldset id="section" class\$="gr-form-styles [[_computeSectionClass(editing, canUpload, ownerOf, _editingRef, _deleted)]]">
      <div id="mainContainer">
        <div class="header">
          <div class="name">
            <h3>[[_computeSectionName(section.id)]]</h3>
            <gr-button id="editBtn" link="" class\$="[[_computeEditBtnClass(section.id)]]" on-tap="editReference">
              <iron-icon id="icon" icon="gr-icons:create"></iron-icon>
            </gr-button>
          </div>
          <input id="editRefInput" bind-value="{{section.id}}" is="iron-input" type="text" on-input="_handleValueChange">
          <gr-button link="" id="deleteBtn" on-tap="_handleRemoveReference">Remove</gr-button>
        </div><!-- end header -->
        <div class="sectionContent">
          <template is="dom-repeat" items="{{_permissions}}" as="permission">
            <gr-permission name="[[_computePermissionName(section.id, permission, permissionValues, capabilities)]]" permission="{{permission}}" labels="[[labels]]" section="[[section.id]]" editing="[[editing]]" groups="[[groups]]" on-added-permission-removed="_handleAddedPermissionRemoved">
            </gr-permission>
          </template>
          <div id="addPermission">
            Add permission:
            <select id="permissionSelect">
              <!-- called with a third parameter so that permissions update
                  after a new section is added. -->
              <template is="dom-repeat" items="[[_computePermissions(section.id, capabilities, labels, section.value.permissions.*)]]">
                <option value="[[item.value.id]]">[[item.value.name]]</option>
              </template>
            </select>
            <gr-button link="" id="addBtn" on-tap="_handleAddPermission">Add</gr-button>
          </div>
          <!-- end addPermission -->
        </div><!-- end sectionContent -->
      </div><!-- end mainContainer -->
      <div id="deletedContainer">
        <span>[[_computeSectionName(section.id)]] was deleted</span>
        <gr-button link="" id="undoRemoveBtn" on-tap="_handleUndoRemove">Undo</gr-button>
      </div><!-- end deletedContainer -->
    </fieldset>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-access-section',

  properties: {
    capabilities: Object,
    /** @type {?} */
    section: {
      type: Object,
      notify: true,
      observer: '_updateSection',
    },
    groups: Object,
    labels: Object,
    editing: {
      type: Boolean,
      value: false,
      observer: '_handleEditingChanged',
    },
    canUpload: Boolean,
    ownerOf: Array,
    _originalId: String,
    _editingRef: {
      type: Boolean,
      value: false,
    },
    _deleted: {
      type: Boolean,
      value: false,
    },
    _permissions: Array,
  },

  behaviors: [
    Gerrit.AccessBehavior,
  ],

  listeners: {
    'access-saved': '_handleAccessSaved',
  },

  _updateSection(section) {
    this._permissions = this.toSortedArray(section.value.permissions);
    this._originalId = section.id;
  },

  _handleAccessSaved() {
    // Set a new 'original' value to keep track of after the value has been
    // saved.
    this._updateSection(this.section);
  },

  _handleValueChange() {
    if (!this.section.value.added) {
      this.section.value.modified = this.section.id !== this._originalId;
      // Allows overall access page to know a change has been made.
      // For a new section, this is not fired because new permissions and
      // rules have to be added in order to save, modifying the ref is not
      // enough.
      this.dispatchEvent(new CustomEvent('access-modified', {bubbles: true}));
    }
    this.section.value.updatedId = this.section.id;
  },

  _handleEditingChanged(editing, editingOld) {
    // Ignore when editing gets set initially.
    if (!editingOld) { return; }
    // Restore original values if no longer editing.
    if (!editing) {
      this._editingRef = false;
      this._deleted = false;
      delete this.section.value.deleted;
      // Restore section ref.
      this.set(['section', 'id'], this._originalId);
      // Remove any unsaved but added permissions.
      this._permissions = this._permissions.filter(p => !p.value.added);
      for (const key of Object.keys(this.section.value.permissions)) {
        if (this.section.value.permissions[key].added) {
          delete this.section.value.permissions[key];
        }
      }
    }
  },

  _computePermissions(name, capabilities, labels) {
    let allPermissions;
    if (name === GLOBAL_NAME) {
      allPermissions = this.toSortedArray(capabilities);
    } else {
      const labelOptions = this._computeLabelOptions(labels);
      allPermissions = labelOptions.concat(
          this.toSortedArray(this.permissionValues));
    }
    return allPermissions.filter(permission => {
      return !this.section.value.permissions[permission.id];
    });
  },

  _computeHideEditClass(section) {
    return section.id === 'GLOBAL_CAPABILITIES' ? 'hide' : '';
  },

  _handleAddedPermissionRemoved(e) {
    const index = e.model.index;
    this._permissions = this._permissions.slice(0, index).concat(
        this._permissions.slice(index + 1, this._permissions.length));
  },

  _computeLabelOptions(labels) {
    const labelOptions = [];
    for (const labelName of Object.keys(labels)) {
      labelOptions.push({
        id: 'label-' + labelName,
        value: {
          name: `${LABEL} ${labelName}`,
          id: 'label-' + labelName,
        },
      });
      labelOptions.push({
        id: 'labelAs-' + labelName,
        value: {
          name: `${LABEL} ${labelName} ${ON_BEHALF_OF}`,
          id: 'labelAs-' + labelName,
        },
      });
    }
    return labelOptions;
  },

  _computePermissionName(name, permission, permissionValues, capabilities) {
    if (name === GLOBAL_NAME) {
      return capabilities[permission.id].name;
    } else if (permissionValues[permission.id]) {
      return permissionValues[permission.id].name;
    } else if (permission.value.label) {
      let behalfOf = '';
      if (permission.id.startsWith('labelAs-')) {
        behalfOf = ON_BEHALF_OF;
      }
      return `${LABEL} ${permission.value.label}${behalfOf}`;
    }
  },

  _computeSectionName(name) {
    // When a new section is created, it doesn't yet have a ref. Set into
    // edit mode so that the user can input one.
    if (!name) {
      this._editingRef = true;
      // Needed for the title value. This is the same default as GWT.
      name = NEW_NAME;
      // Needed for the input field value.
      this.set('section.id', name);
    }
    if (name === GLOBAL_NAME) {
      return 'Global Capabilities';
    } else if (name.startsWith(REFS_NAME)) {
      return `Reference: ${name}`;
    }
    return name;
  },

  _handleRemoveReference() {
    if (this.section.value.added) {
      this.dispatchEvent(new CustomEvent('added-section-removed',
          {bubbles: true}));
    }
    this._deleted = true;
    this.section.value.deleted = true;
    this.dispatchEvent(new CustomEvent('access-modified', {bubbles: true}));
  },

  _handleUndoRemove() {
    this._deleted = false;
    delete this.section.value.deleted;
  },

  editReference() {
    this._editingRef = true;
    this.$.editRefInput.focus();
  },

  _isEditEnabled(canUpload, ownerOf, sectionId) {
    return canUpload || ownerOf.indexOf(sectionId) >= 0;
  },

  _computeSectionClass(editing, canUpload, ownerOf, editingRef, deleted) {
    const classList = [];
    if (editing && this._isEditEnabled(canUpload, ownerOf, this.section.id)) {
      classList.push('editing');
    }
    if (editingRef) {
      classList.push('editingRef');
    }
    if (deleted) {
      classList.push('deleted');
    }
    return classList.join(' ');
  },

  _computeEditBtnClass(name) {
    return name === GLOBAL_NAME ? 'global' : '';
  },

  _handleAddPermission() {
    const value = this.$.permissionSelect.value;
    const permission = {
      id: value,
      value: {rules: {}, added: true},
    };

    // This is needed to update the 'label' property of the
    // 'label-<label-name>' permission.
    //
    // The value from the add permission dropdown will either be
    // label-<label-name> or labelAs-<labelName>.
    // But, the format of the API response is as such:
    // "permissions": {
    //  "label-Code-Review": {
    //    "label": "Code-Review",
    //    "rules": {...}
    //    }
    //  }
    // }
    // When we add a new item, we have to push the new permission in the same
    // format as the ones that have been returned by the API.
    if (value.startsWith('label')) {
      permission.value.label =
          value.replace('label-', '').replace('labelAs-', '');
    }
    // Add to the end of the array (used in dom-repeat) and also to the
    // section object that is two way bound with its parent element.
    this.push('_permissions', permission);
    this.set(['section.value.permissions', permission.id],
        permission.value);
  }
});
