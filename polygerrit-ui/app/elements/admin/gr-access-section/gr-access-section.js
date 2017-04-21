/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
(function() {
  'use strict';

  /**
   * Fired when the section has been modified or removed.
   *
   * @event access-modified
   */

  const GLOBAL_NAME = 'GLOBAL_CAPABILITIES';

  // The name that gets automatically input when a new reference is added.
  const NEW_NAME = 'refs/heads/*';
  const REFS_NAME = 'refs/';
  const ON_BEHALF_OF = '(On Behalf Of)';
  const LABEL = 'Label';
  const ROLE = 'Role';

  Polymer({
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
      roles: Object,
      editing: {
        type: Boolean,
        value: false,
        observer: '_handleEditingChanged',
      },
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

    _computePermissions(name, capabilities, labels, roles) {
      let allPermissions;
      if (name === GLOBAL_NAME) {
        allPermissions = this.toSortedArray(capabilities);
      } else {
        const labelOptions = this._computeLabelOptions(labels);
        const roleOptions = this._computeRoleOptions(roles);
        allPermissions = labelOptions.concat(
          roleOptions,
          this.toSortedArray(this.permissionValues)
        )
      }
      return allPermissions.filter(permission => {
        return !this.section.value.permissions[permission.id];
      });
    },

    _computeHideEditClass(section) {
      return section.id === 'GLOBAL_CAPABILITIES' ? 'hide' : '';
    },

    _computeRoleOptions(roles) {
      if (!roles) { return []; }
      const roleOptions = [];
      for (const roleName of Object.keys(roles)) {
        roleOptions.push({
          id: 'role-' + roleName,
          value: {
            name: `${ROLE} ${roleName}`,
            id: 'role-' + roleName,
          }
        })
      }
      return roleOptions;
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
      } else if (permission.value.role) {
        return `${ROLE} ${permission.value.role}`;
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

    _computeSectionClass(editing, editingRef, deleted) {
      const classList = [];
      if (editing) {
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
    },
  });
})();
