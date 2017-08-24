// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function() {
  'use strict';

  const GLOBAL_NAME = 'GLOBAL_CAPABILITIES';

  // The name that gets automatically input when a new reference is added.
  const NEW_NAME = 'refs/heads/*';
  const REFS_NAME = 'refs/';
  const ON_BEHALF_OF = '(On Behalf Of)';
  const LABEL = 'Label';

  Polymer({
    is: 'gr-access-section',

    properties: {
      capabilities: Object,
      /** @type {?} */
      section: {
        type: Object,
        notify: true,
        observer: '_sectionChanged',
      },
      labels: Object,
      editing: {
        type: Boolean,
        value: false,
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

    _sectionChanged(section) {
      this._permissions = this.toSortedArray(section.value.permissions);
      this._originalId = section.id;
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
      this._deleted = true;
      this.set('section.value.deleted', true);
    },

    _handleUndoRemove() {
      this._deleted = false;
      delete this.section.value.deleted;
    },

    _handleEditReference() {
      this._editingRef = true;
    },

    _undoReferenceEdit() {
      this._editingRef = false;
      this.set('section.id', this._originalId);
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
      // TODO implement once read-only mode is disabled.
    },
  });
})();