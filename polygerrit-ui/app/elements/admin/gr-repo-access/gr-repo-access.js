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

  const NOTHING_TO_SAVE = 'No changes to save.';

  /**
   * Fired when save is a no-op
   *
   * @event show-alert
   */

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
     * @param {!Defs.projectAccessInput} addRemoveObj
     * @param {!Array} path
     * @param {string} type add or remove
     * @param {!Object=} opt_value value to add if the type is 'add'
     * @return {!Defs.projectAccessInput}
     */
    _updateAddRemoveObj(addRemoveObj, path, type, opt_value) {
      let curPos = addRemoveObj[type];
      for (const item of path) {
        if (!curPos[item]) {
          if (item === path[path.length - 1] && type === 'remove') {
            if (path[path.length - 2] === 'permissions') {
              curPos[item] = {rules: {}};
            } else if (path.length === 1) {
              curPos[item] = {permissions: {}};
            } else {
              curPos[item] = {};
            }
          } else if (item === path[path.length - 1] && type === 'add') {
            curPos[item] = opt_value;
          } else {
            curPos[item] = {};
          }
        }
        curPos = curPos[item];
      }
      return addRemoveObj;
    },

    /**
     * Used to recursively remove any objects with a 'deleted' bit.
     */
    _recursivelyRemoveDeleted(obj) {
      for (const k in obj) {
        if (!obj.hasOwnProperty(k)) { return; }
        if (typeof obj[k] == 'object') {
          if (obj[k].deleted) {
            delete obj[k];
            return;
          }
          this._recursivelyRemoveDeleted(obj[k]);
        }
      }
    },

    _recursivelyUpdateAddRemoveObj(obj, addRemoveObj, path = []) {
      for (const k in obj) {
        if (!obj.hasOwnProperty(k)) { return; }
        if (typeof obj[k] == 'object') {
          if (obj[k].deleted) {
            this._updateAddRemoveObj(addRemoveObj,
                path.concat(k), 'remove');
            continue;
          } else if (obj[k].modified) {
            this._updateAddRemoveObj(addRemoveObj,
                path.concat(k), 'remove');

            const updatedId = obj[k].updatedId;
            const ref = updatedId ? updatedId : k;
            this._updateAddRemoveObj(addRemoveObj, path.concat(ref), 'add',
                obj[k]);
            /* Special case for ref changes because they need to be added and
             removed in a different way. The new ref needs to include all changes
             but also the initial state. To do this, instead of continuing with
             the same recursion, just remove anything that is deleted in the
             current state. */
            if (updatedId && updatedId !== k) {
              this._recursivelyRemoveDeleted(addRemoveObj.add[updatedId]);
            }
            continue;
          } else if (obj[k].added) {
            this._updateAddRemoveObj(addRemoveObj,
                path.concat(k), 'add', obj[k]);
            continue;
          }
          this._recursivelyUpdateAddRemoveObj(obj[k], addRemoveObj,
              path.concat(k));
        }
      }
    },

    /**
     * Returns an object formatted for saving or submitting access changes for
     * review
     *
     * @return {!Defs.projectAccessInput}
     */
    _computeAddAndRemove() {
      const addRemoveObj = {
        add: {},
        remove: {},
      };

      this._recursivelyUpdateAddRemoveObj(this._local, addRemoveObj);
      return addRemoveObj;
    },

    _handleSaveForReview() {
      const addRemoveObj = this._computeAddAndRemove();

      // If there are no changes, don't actually save.
      if (!Object.keys(addRemoveObj.add).length &&
          !Object.keys(addRemoveObj.remove).length) {
        this.dispatchEvent(new CustomEvent('show-alert',
            {detail: {message: NOTHING_TO_SAVE}, bubbles: true}));
        return;
      }
      return this.$.restAPI.setProjectAccessRightsForReview(this.repo, {
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