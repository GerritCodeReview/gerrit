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
      _modified: {
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

    listeners: {
      'access-modified': '_handleAccessModified',
    },

    _getSections() {
      return Polymer.dom(this.root).querySelectorAll('gr-access-section');
    },

    _getPermissionsForSection(section) {
      return Polymer.dom(section.root).querySelectorAll('gr-permission');
    },

    _getRulesForPermission(permission) {
      return Polymer.dom(permission.root).querySelectorAll('gr-rule-editor');
    },

    _getAllRules() {
      let rules = [];
      for (const section of this._getSections()) {
        for (const permission of this._getPermissionsForSection(section)) {
          rules = rules.concat(rules, this._getRulesForPermission(permission));
        }
      }
      return rules;
    },

    _handleAccessModified() {
      this._modified = true;
    },

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
      });
    },

    _handleEdit() {
      return this._editing ? this._editing = false : this._editing = true;
    },

    _editOrCancel(editing) {
      return editing ? 'Cancel' : 'Edit';
    },

    _permissionInRemove(toRemove, sectionId, permissionId) {
      return toRemove[sectionId] && toRemove[sectionId][permissionId];
    },

    _generatePermissionObject(addRemoveObj, sectionId, permissionId) {
      const permissionObjAdd = {};
      const permissionObjRemove = {};
      permissionObjAdd[permissionId] = {rules: {}};
      permissionObjRemove[permissionId] = {rules: {}};
      addRemoveObj.toSave[sectionId] = {permissions: permissionObjAdd};
      addRemoveObj.toRemove[sectionId] = {permissions: permissionObjRemove};
      return addRemoveObj;
    },

    _computeAddAndRemove() {
      let addRemoveObj = {
        toSave: {},
        toRemove: {},
      };
      const sections = this._getSections();
      for (const section of sections) {
        const sectionId = section.section.id;
        const permissions = this._getPermissionsForSection(section);
        for (const permission of permissions) {
          const permissionId = permission.permission.id;
          const rules = this._getRulesForPermission(permission);
          for (const rule of rules) {
            // Find all rules that are changed. In the event that it has been
            // modified.
            if (!rule._modified) { continue; }
            const ruleId = rule.rule.id;
            const ruleValue = rule.rule.value;

            // If the rule's parent permission has already been added to the
            // toRemove object (don't need to check toSave, asn they are always
            // done to both at the same time). If it doesn't exist yet, it needs
            // to be created.
            if (!this._permissionInRemove(addRemoveObj.toRemove, sectionId,
                permissionId)) {
              addRemoveObj = this._generatePermissionObject(addRemoveObj,
                  sectionId, permissionId);
            }

            // Remove the rule with a value of null
            addRemoveObj.toRemove[sectionId].permissions[permissionId]
                .rules[ruleId] = null;
            // Add the rule with a value of the updated rule value.
            addRemoveObj.toSave[sectionId].permissions[permissionId]
                .rules[ruleId] = ruleValue;
          }
        }
      }
      return addRemoveObj;
    },

    _handleSaveForReview() {
      // Use saving rather than editing here because rules have to handle
      // save prior to toggling editing.
      const addRemoveObj = this._computeAddAndRemove();
      this.$.restAPI.setProjectAccessRightsForReview(this.project, {
        add: addRemoveObj.toSave,
        remove: addRemoveObj.toRemove,
      }).then(change => {
        Gerrit.Nav.navigateToChange(change);
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