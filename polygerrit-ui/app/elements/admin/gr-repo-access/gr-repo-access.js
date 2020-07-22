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
import '../../../styles/gr-menu-page-styles.js';
import '../../../styles/gr-subpage-styles.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../gr-access-section/gr-access-section.js';
import {flush, dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-repo-access_html.js';
import {
  encodeURL,
  getBaseUrl,
  singleDecodeURL,
} from '../../../utils/url-util.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {toSortedPermissionsArray} from '../../../utils/access-util.js';

const Defs = {};

const NOTHING_TO_SAVE = 'No changes to save.';

const MAX_AUTOCOMPLETE_RESULTS = 50;

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

/**
 * @extends PolymerElement
 */
class GrRepoAccess extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-repo-access'; }

  static get properties() {
    return {
      repo: {
        type: String,
        observer: '_repoChanged',
      },
      // The current path
      path: String,

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
    };
  }

  /** @override */
  created() {
    super.created();
    this.addEventListener('access-modified',
        () =>
          this._handleAccessModified());
  }

  _handleAccessModified() {
    this._modified = true;
  }

  /**
   * @param {string} repo
   * @return {!Promise}
   */
  _repoChanged(repo) {
    this._loading = true;

    if (!repo) { return Promise.resolve(); }

    return this._reload(repo);
  }

  _reload(repo) {
    const promises = [];

    const errFn = response => {
      this.dispatchEvent(new CustomEvent('page-error', {
        detail: {response},
        composed: true, bubbles: true,
      }));
    };

    this._editing = false;

    // Always reset sections when a project changes.
    this._sections = [];
    promises.push(this.$.restAPI.getRepoAccessRights(repo, errFn)
        .then(res => {
          if (!res) { return Promise.resolve(); }

          // Keep a copy of the original inherit from values separate from
          // the ones data bound to gr-autocomplete, so the original value
          // can be restored if the user cancels.
          this._inheritsFrom = res.inherits_from ? ({
            ...res.inherits_from}) : null;
          this._originalInheritsFrom = res.inherits_from ? ({
            ...res.inherits_from}) : null;
          // Initialize the filter value so when the user clicks edit, the
          // current value appears. If there is no parent repo, it is
          // initialized as an empty string.
          this._inheritFromFilter = res.inherits_from ?
            this._inheritsFrom.name : '';
          this._local = res.local;
          this._groups = res.groups;
          this._weblinks = res.config_web_links || [];
          this._canUpload = res.can_upload;
          this._ownerOf = res.owner_of || [];
          return toSortedPermissionsArray(this._local);
        }));

    promises.push(this.$.restAPI.getCapabilities(errFn)
        .then(res => {
          if (!res) { return Promise.resolve(); }

          return res;
        }));

    promises.push(this.$.restAPI.getRepo(repo, errFn)
        .then(res => {
          if (!res) { return Promise.resolve(); }

          return res.labels;
        }));

    return Promise.all(promises).then(([sections, capabilities, labels]) => {
      this._capabilities = capabilities;
      this._labels = labels;
      this._sections = sections;
      this._loading = false;
    });
  }

  _handleUpdateInheritFrom(e) {
    if (!this._inheritsFrom) {
      this._inheritsFrom = {};
    }
    this._inheritsFrom.id = e.detail.value;
    this._inheritsFrom.name = this._inheritFromFilter;
    this._handleAccessModified();
  }

  _getInheritFromSuggestions() {
    return this.$.restAPI.getRepos(
        this._inheritFromFilter,
        MAX_AUTOCOMPLETE_RESULTS)
        .then(response => {
          const projects = [];
          for (const key in response) {
            if (!response.hasOwnProperty(key)) { continue; }
            projects.push({
              name: response[key].name,
              value: response[key].id,
            });
          }
          return projects;
        });
  }

  _computeLoadingClass(loading) {
    return loading ? 'loading' : '';
  }

  _handleEdit() {
    this._editing = !this._editing;
  }

  _editOrCancel(editing) {
    return editing ? 'Cancel' : 'Edit';
  }

  _computeWebLinkClass(weblinks) {
    return weblinks && weblinks.length ? 'show' : '';
  }

  _computeShowInherit(inheritsFrom) {
    return inheritsFrom ? 'show' : '';
  }

  _handleAddedSectionRemoved(e) {
    const index = e.model.index;
    this._sections = this._sections.slice(0, index)
        .concat(this._sections.slice(index + 1, this._sections.length));
  }

  _handleEditingChanged(editing, editingOld) {
    // Ignore when editing gets set initially.
    if (!editingOld || editing) { return; }
    // Remove any unsaved but added refs.
    if (this._sections) {
      this._sections = this._sections.filter(p => !p.value.added);
    }
    // Restore inheritFrom.
    if (this._inheritsFrom) {
      this._inheritsFrom = {...this._originalInheritsFrom};
      this._inheritFromFilter = this._inheritsFrom.name;
    }
    for (const key of Object.keys(this._local)) {
      if (this._local[key].added) {
        delete this._local[key];
      }
    }
  }

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
  }

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
  }

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
          /**
           * As add / delete both can happen in the new section,
           * so here to make sure it will remove the deleted ones.
           *
           * @see Issue 11339
           */
          this._recursivelyRemoveDeleted(addRemoveObj.add[k]);
          continue;
        }
        this._recursivelyUpdateAddRemoveObj(obj[k], addRemoveObj,
            path.concat(k));
      }
    }
  }

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

    const originalInheritsFromId = this._originalInheritsFrom ?
      singleDecodeURL(this._originalInheritsFrom.id) : null;
    const inheritsFromId = this._inheritsFrom ?
      singleDecodeURL(this._inheritsFrom.id) : null;

    const inheritFromChanged =
        // Inherit from changed
        (originalInheritsFromId &&
            originalInheritsFromId !== inheritsFromId) ||
        // Inherit from added (did not have one initially);
        (!originalInheritsFromId && inheritsFromId);

    this._recursivelyUpdateAddRemoveObj(this._local, addRemoveObj);

    if (inheritFromChanged) {
      addRemoveObj.parent = inheritsFromId;
    }
    return addRemoveObj;
  }

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
    flush();
    dom(this.root).querySelector('gr-access-section:last-of-type')
        .editReference();
  }

  _getObjforSave() {
    const addRemoveObj = this._computeAddAndRemove();
    // If there are no changes, don't actually save.
    if (!Object.keys(addRemoveObj.add).length &&
        !Object.keys(addRemoveObj.remove).length &&
        !addRemoveObj.parent) {
      this.dispatchEvent(new CustomEvent('show-alert', {
        detail: {message: NOTHING_TO_SAVE},
        bubbles: true,
        composed: true,
      }));
      return;
    }
    const obj = {
      add: addRemoveObj.add,
      remove: addRemoveObj.remove,
    };
    if (addRemoveObj.parent) {
      obj.parent = addRemoveObj.parent;
    }
    return obj;
  }

  _handleSave(e) {
    const obj = this._getObjforSave();
    if (!obj) { return; }
    const button = e && e.target;
    if (button) {
      button.loading = true;
    }
    return this.$.restAPI.setRepoAccessRights(this.repo, obj)
        .then(() => {
          this._reload(this.repo);
        })
        .finally(() => {
          this._modified = false;
          if (button) {
            button.loading = false;
          }
        });
  }

  _handleSaveForReview(e) {
    const obj = this._getObjforSave();
    if (!obj) { return; }
    const button = e && e.target;
    if (button) {
      button.loading = true;
    }
    return this.$.restAPI
        .setRepoAccessRightsForReview(this.repo, obj)
        .then(change => {
          GerritNav.navigateToChange(change);
        })
        .finally(() => {
          this._modified = false;
          if (button) {
            button.loading = false;
          }
        });
  }

  _computeSaveReviewBtnClass(canUpload) {
    return !canUpload ? 'invisible' : '';
  }

  _computeSaveBtnClass(ownerOf) {
    return ownerOf && ownerOf.length === 0 ? 'invisible' : '';
  }

  _computeMainClass(ownerOf, canUpload, editing) {
    const classList = [];
    if (ownerOf && ownerOf.length > 0 || canUpload) {
      classList.push('admin');
    }
    if (editing) {
      classList.push('editing');
    }
    return classList.join(' ');
  }

  _computeParentHref(repoName) {
    return getBaseUrl() +
        `/admin/repos/${encodeURL(repoName, true)},access`;
  }
}

customElements.define(GrRepoAccess.is, GrRepoAccess);
