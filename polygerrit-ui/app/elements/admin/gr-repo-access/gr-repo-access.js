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

  const Defs = {};

  const NOTHING_TO_SAVE = 'No changes to save.';

  const MAX_AUTOCOMPLETE_RESULTS = 20;

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
   * @typedef {!Object<string, Defs.permissions>}
   */
  Defs.sections;

  /**
   * @typedef {{
   *    remove: !Defs.sections,
   *    add: !Defs.sections,
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
      _canUpload: {
        type: Boolean,
        value: false,
      },
      _inheritFromFilter: String,
      _query: {
        type: Function,
        value() {
          return this._getInheritFromSuggestions.bind(this);
        },
      },
      _ownerOf: Array,
      _capabilities: Object,
      _groups: Object,
      /** @type {?} */
      _inheritsFrom: Object,
      _labels: Object,
      _roles: Object,
      _local: Object,
      _editing: {
        type: Boolean,
        value: false,
        observer: '_handleEditingChanged',
      },
      _modified: {
        type: Boolean,
        value: false,
      },
      _sections: Array,
      _weblinks: Array,
      _loading: {
        type: Boolean,
        value: true,
      },
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

      const errFn = response => {
        this.fire('page-error', {response});
      };

      // Always reset sections when a project changes.
      this._sections = [];
      promises.push(this.$.restAPI.getRepoAccessRights(repo, errFn)
          .then(res => {
            if (!res) { return Promise.resolve(); }

            // Keep a copy of the original inherit from values separate from
            // the ones data bound to gr-autocomplete, so the original value
            // can be restored if the user cancels.
            this._inheritsFrom = res.inherits_from ? Object.assign({},
                res.inherits_from) : null;
            this._originalInheritsFrom = res.inherits_from ? Object.assign({},
                res.inherits_from) : null;
            // Initialize the filter value so when the user clicks edit, the
            // current value appears. If there is no parent repo, it is
            // initialized as an empty string.
            this._inheritFromFilter = res.inherits_from ?
                this._inheritsFrom.name : '';
            this._local = res.local;
            this._groups = res.groups;
            this._weblinks = res.config_web_links || [];
            this._canUpload = res.can_upload;
            return this.toSortedArray(this._local);
          }));

      promises.push(this.$.restAPI.getCapabilities(errFn)
          .then(res => {
            if (!res) { return Promise.resolve(); }

            return res;
          }));

      promises.push(this.$.restAPI.getRepo(repo, errFn)
          .then(res => {
            if (!res) { return Promise.resolve(); }
            return res;
          }));

      promises.push(this.$.restAPI.getIsAdmin().then(isAdmin => {
        this._isAdmin = isAdmin;
      }));

      return Promise.all(promises).then(([sections, capabilities, repo]) => {
        this._capabilities = capabilities;
        this._labels = repo.labels;
        this._roles = repo.roles || {};
        this._sections = sections;
        this._loading = false;
      });
    },

    _handleUpdateInheritFrom(e) {
      const projectId = decodeURIComponent(e.detail.value);
      if (!this._inheritsFrom) {
        this._inheritsFrom = {};
      }
      this._inheritsFrom.id = projectId;
      this._inheritsFrom.name = this._inheritFromFilter;
      this._handleAccessModified();
    },

    _getInheritFromSuggestions() {
      return this.$.restAPI.getRepos(
          this._inheritFromFilter,
          MAX_AUTOCOMPLETE_RESULTS)
          .then(response => {
            const projects = [];
            for (const key in response) {
              if (!response.hasOwnProperty(key)) { continue; }
              projects.push({
                name: key,
                value: response[key].id,
              });
            }
            return projects;
          });
    },

    _computeLoadingClass(loading) {
      return loading ? 'loading' : '';
    },

    _handleEdit() {
      this._editing = !this._editing;
    },

    _editOrCancel(editing) {
      return editing ? 'Cancel' : 'Edit';
    },

    _computeWebLinkClass(weblinks) {
      return weblinks.length ? 'show' : '';
    },

    _computeShowInherit(inheritsFrom) {
      return inheritsFrom ? 'show' : '';
    },

    _handleEditingChanged(editing, editingOld) {
      // Ignore when editing gets set initially.
      if (!editingOld || editing) { return; }
      // Remove any unsaved but added refs.
      if (this._sections) {
        this._sections = this._sections.filter(p => !p.value.added);
      }
      // Restore inheritFrom.
      if (this._inheritsFrom) {
        this._inheritsFrom = Object.assign({}, this._originalInheritsFrom);
        this._inheritFromFilter = this._inheritsFrom.name;
      }
      for (const key of Object.keys(this._local)) {
        if (this._local[key].added) {
          delete this._local[key];
        }
      }
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
        if (!obj.hasOwnProperty(k)) { continue; }

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
        if (!obj.hasOwnProperty(k)) { continue; }
        if (typeof obj[k] == 'object') {
          const updatedId = obj[k].updatedId;
          const ref = updatedId ? updatedId : k;
          if (obj[k].deleted) {
            this._updateAddRemoveObj(addRemoveObj,
                path.concat(k), 'remove');
            continue;
          } else if (obj[k].modified) {
            this._updateAddRemoveObj(addRemoveObj,
                path.concat(k), 'remove');
            this._updateAddRemoveObj(addRemoveObj, path.concat(ref), 'add',
                obj[k]);
            /* Special case for ref changes because they need to be added and
             removed in a different way. The new ref needs to include all
             changes but also the initial state. To do this, instead of
             continuing with the same recursion, just remove anything that is
             deleted in the current state. */
            if (updatedId && updatedId !== k) {
              this._recursivelyRemoveDeleted(addRemoveObj.add[updatedId]);
            }
            continue;
          } else if (obj[k].added) {
            this._updateAddRemoveObj(addRemoveObj,
                path.concat(ref), 'add', obj[k]);
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

      const inheritFromChanged =
          // Inherit from changed
          (this._originalInheritsFrom &&
          this._originalInheritsFrom.id !== this._inheritsFrom.id) ||
          // Inherit froma dded (did not have one initially);
          (!this._originalInheritsFrom && this._inheritsFrom
              && this._inheritsFrom.id);

      this._recursivelyUpdateAddRemoveObj(this._local, addRemoveObj);

      if (inheritFromChanged) {
        addRemoveObj.parent = this._inheritsFrom.id;
      }
      return addRemoveObj;
    },

    _handleCreateSection() {
      let newRef = 'refs/for/*';
      // Avoid using an already used key for the placeholder, since it
      // immediately gets added to an object.
      while (this._local[newRef]) {
        newRef = `${newRef}*`;
      }
      const section = {permissions: {}, added: true};
      this.push('_sections', {id: newRef, value: section});
      this.set(['_local', newRef], section);
      Polymer.dom.flush();
      Polymer.dom(this.root).querySelector('gr-access-section:last-of-type')
          .editReference();
    },

    _handleSaveForReview() {
      const addRemoveObj = this._computeAddAndRemove();
      // If there are no changes, don't actually save.
      if (!Object.keys(addRemoveObj.add).length &&
          !Object.keys(addRemoveObj.remove).length &&
          !addRemoveObj.parent) {
        this.dispatchEvent(new CustomEvent('show-alert',
            {detail: {message: NOTHING_TO_SAVE}, bubbles: true}));
        return;
      }
      const obj = {
        add: addRemoveObj.add,
        remove: addRemoveObj.remove,
      };
      if (addRemoveObj.parent) {
        obj.parent = addRemoveObj.parent;
      }
      return this.$.restAPI.setProjectAccessRightsForReview(this.repo, obj)
          .then(change => {
            Gerrit.Nav.navigateToChange(change);
          });
    },

    _computeShowSaveClass(editing) {
      if (!editing) { return ''; }
      return 'visible';
    },

    _computeEditingClass(editing) {
      return editing ? 'editing': '';
    },

    _computeMainClass(isAdmin, canUpload, editing) {
      const classList = [];
      if (isAdmin || canUpload) {
        classList.push('admin');
      }
      if (editing) {
        classList.push('editing');
      }
      return classList.join(' ');
    },

    _computeParentHref(repoName) {
      return this.getBaseUrl() +
          `/admin/repos/${this.encodeURL(repoName, true)},access`;
    },
  });
})();
