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

  const Defs = {};

  /**
   * @typedef {{
   *    value: !Object,
   * }}
   */
  Defs.rule;

  /**
   * @typedef {{
   *    rules: !Object<string, Defs.rule>
   * }}
   */
  Defs.permission;

  /**
   * Can be an empty object or consist of permissions.
   *
   * @typedef {{
   *    permissions: !Object<string, Defs.permission>
   * }}
   */
  Defs.permissions;

  /**
   * Can be an empty object or consist of permissions.
   *
   * @typedef {Object<string, Defs.permissions>}
   */
  Defs.sections;

  /**
   * @typedef {{
   *    remove: Defs.sections,
   *    add: Defs.sections,
   * }}
   */
  Defs.projectAccessInput;


  Polymer({
    is: 'gr-repo-access',

    properties: {
      repo: {
        type: String,
        observer: '_repoChanged',
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

    /**
     * Gets an array of gr-acces-section Polymer elements.
     *
     * @return {!Array}
     */
    _getSections() {
      return Polymer.dom(this.root).querySelectorAll('gr-access-section');
    },

    /**
     * Gets an array of the gr-permission polymer elements for a particular
     * access section.
     *
     * @param {!Object} section
     * @return {!Array}
     */
    _getPermissionsForSection(section) {
      return Polymer.dom(section.root).querySelectorAll('gr-permission');
    },

    /**
     * Gets an array of the gr-rule-editor polymer elements for a particular
     * permission (within a section).
     *
     * @param {!Object} permission
     * @return {!Array}
     */
    _getRulesForPermission(permission) {
      return Polymer.dom(permission.root).querySelectorAll('gr-rule-editor');
    },

    /**
     * Gets an array of all rules for the entire project access.
     *
     * @return {!Array}
     */
    _getAllRules() {
      let rules = [];
      for (const section of this._getSections()) {
        for (const permission of this._getPermissionsForSection(section)) {
          rules = rules.concat(this._getRulesForPermission(permission));
        }
      }
      return rules;
    },

    _handleAccessModified() {
      this._modified = true;
    },

    /**
     * @param {string} repo
     * @return {!Promise}
     */
    _repoChanged(repo) {
      if (!repo) { return Promise.resolve(); }
      const promises = [];
      // Always reset sections when a project changes.
      this._sections = [];
      promises.push(this.$.restAPI.getRepoAccessRights(repo).then(res => {
        this._inheritsFrom = res.inherits_from;
        this._local = res.local;
        this._groups = res.groups;
        return this.toSortedArray(this._local);
      }));

      promises.push(this.$.restAPI.getCapabilities().then(res => {
        return res;
      }));

      promises.push(this.$.restAPI.getRepo(repo).then(res => {
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
      this._editing = !this._editing;
    },

    _editOrCancel(editing) {
      return editing ? 'Cancel' : 'Edit';
    },

    /**
     * Returns whether or not a given permission exists in the remove object
     *
     * @param {!Object} remove
     * @param {string} sectionId
     * @param {string} permissionId
     * @return {boolean}
     */
    _permissionInRemove(remove, sectionId, permissionId) {
      return !!(remove[sectionId] &&
          remove[sectionId].permissions[permissionId]);
    },

    /**
     * Returns a projectAccessInput object that contains new permission Objects
     * for a given permission in a section.
     *
     * @param {!Defs.projectAccessInput} addRemoveObj
     * @param {string} sectionId
     * @param {string} permissionId
     *
     * @return {!Defs.projectAccessInput}
     */
    _generatePermissionObject(addRemoveObj, sectionId, permissionId) {
      const permissionObjAdd = {};
      const permissionObjRemove = {};
      permissionObjAdd[permissionId] = {rules: {}};
      permissionObjRemove[permissionId] = {rules: {}};
      addRemoveObj.add[sectionId] = {permissions: permissionObjAdd};
      addRemoveObj.remove[sectionId] = {permissions: permissionObjRemove};
      return addRemoveObj;
    },

    /**
     * Returns an object formatted for saving or submitting access changes for
     * review
     *
     * @return {!Defs.projectAccessInput}
     */
    _computeAddAndRemove() {
      let addRemoveObj = {
        add: {},
        remove: {},
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
            // remove object (don't need to check add, as they are always
            // done to both at the same time). If it doesn't exist yet, it needs
            // to be created.
            if (!this._permissionInRemove(addRemoveObj.remove, sectionId,
                permissionId)) {
              addRemoveObj = this._generatePermissionObject(addRemoveObj,
                  sectionId, permissionId);
            }

            // Remove the rule with a value of null
            addRemoveObj.remove[sectionId].permissions[permissionId]
                .rules[ruleId] = null;
            // Add the rule with a value of the updated rule value.
            addRemoveObj.add[sectionId].permissions[permissionId]
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
      return this.$.restAPI.setProjectAccessRightsForReview(this.project, {
        add: addRemoveObj.add,
        remove: addRemoveObj.remove,
      }).then(change => {
        Gerrit.Nav.navigateToChange(change);
      });
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

    _computeParentHref(repoName) {
      return this.getBaseUrl() +
          `/admin/repos/${this.encodeURL(repoName, true)},access`;
    },
  });
})();