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

  Polymer({
    is: 'gr-project-access',

    properties: {
      project: {
        type: String,
        observer: '_projectChanged',
      },
      // The current path
      path: String,

      _isAdmin: {
        type: Boolean,
        value: false,
      },
      _capabilities: Object,
      _groups: Object,
      /** @type {?} */
      _inheritsFrom: Object,
      _labels: Object,
      _local: Object,
      _editing: {
        type: Boolean,
        value: false,
      },
      _sections: Array,
    },

    behaviors: [
      Gerrit.AccessBehavior,
      Gerrit.BaseUrlBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    /**
     * @param {string} project
     * @return {!Promise}
     */
    _projectChanged(project) {
      if (!project) { return Promise.resolve(); }
      const promises = [];
      // Always reset sections when a project changes.
      this._sections = [];
      promises.push(this.$.restAPI.getProjectAccessRights(project).then(res => {
        this._inheritsFrom = res.inherits_from;
        this._local = res.local;
        this._groups = res.groups;
        return this.toSortedArray(this._local);
      }));

      promises.push(this.$.restAPI.getCapabilities().then(res => {
        return res;
      }));

      promises.push(this.$.restAPI.getProject(project).then(res => {
        return res.labels;
      }));

      promises.push(this.$.restAPI.getIsAdmin().then(isAdmin => {
        this._isAdmin = isAdmin;
      }));

      return Promise.all(promises).then(([sections, capabilities, labels]) => {
        this._capabilities = capabilities;
        this._labels = labels;
        this._sections = sections;
        // Create a deep copy of original sections because section values
        // are two way data bound. These values are used to determine what to
        // 'remove' and replace with updated values.
        this._originalSections = JSON.parse(JSON.stringify(sections));
      });
    },

    _handleEdit() {
      return this._editing ? this._editing = false : this._editing = true;
    },

    _editOrCancel(editing) {
      return editing ? 'Cancel' : 'Edit';
    },

    _handleSave() {
      const toSave = {};
      const toRemove = {};

      for (const section of this._sections) {
        toSave[section.id] = section.value;
      }

      // Need to preserve a deep copy before removing data for deletion.
      for (const section of this._originalSections) {
        toRemove[section.id] = this._computeRemoveValue(section);
      }

      this.$.restAPI.setProjectAccessRights(this.project, {
        add: toSave,
        remove: toRemove,
      }).then(() => {
        this._originalSections = JSON.parse(JSON.stringify(this._sections));
      });
    },

    _computeRemoveValue(section) {
      for (const permission of Object.keys(section.value.permissions)) {
        section.value.permissions[permission] = {};
      }
      return section.value;
    },

    _computeShowEditClass(sections) {
      if (!sections.length) { return ''; }
      return 'visible';
    },

    _computeShowSaveClass(editing) {
      if (!editing) { return ''; }
      return 'visible';
    },

    _computeAdminClass(isAdmin) {
      return isAdmin ? 'admin' : '';
    },

    _computeParentHref(projectName) {
      return this.getBaseUrl() +
          `/admin/projects/${this.encodeURL(projectName, true)},access`;
    },
  });
})();