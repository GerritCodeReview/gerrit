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
import '../../../styles/gr-menu-page-styles';
import '../../../styles/gr-subpage-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import '../gr-access-section/gr-access-section';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-repo-access_html';
import {encodeURL, getBaseUrl, singleDecodeURL} from '../../../utils/url-util';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {PermissionArrayItem, toSortedPermissionsArray} from '../../../utils/access-util';
import {customElement, property} from '@polymer/decorators';
import {RepoName, ProjectInfo, LocalAccessSectionInfo, AccessSectionInfo, CapabilityInfoMap, LabelNameToLabelTypeInfoMap, ProjectAccessInput, GitRef} from '../../../types/common';
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';
import {hasOwnProperty} from '../../../utils/common-util';
import {GrButton} from '../../shared/gr-button/gr-button';
import { GrAccessSection } from '../gr-access-section/gr-access-section';

const NOTHING_TO_SAVE = 'No changes to save.';

const MAX_AUTOCOMPLETE_RESULTS = 50;

enum UpdateType {
  Add = 'add',
  Remove = 'remove',
}

export interface GrRepoAccess {
  $: {
    restAPI: RestApiService & Element;
  };
}

export type PermissionAccessSection = PermissionArrayItem<AccessSectionInfo>;

export interface AddRemoveObj {
  [updateType: string]: {[pathItem: string]: AddRemoveUpdate};
}
export type AddRemoveUpdate = {rules: {}} | {permissions: {}} | {} | unknown;

type TreeNode = TreeNodeWithDelete | TreeLeaf;
type TreeLeaf = string | number | undefined;

interface TreeNodeWithDelete {
  [propName: string]: TreeNode;
  deleted?: boolean;
  modified?: boolean;
  added?: boolean;
  updatedId: string;
}

/**
 * Fired when save is a no-op
 *
 * @event show-alert
 */
@customElement('gr-repo-access')
export class GrRepoAccess extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: String, observer: '_repoChanged'})
  repo?: RepoName;

  @property({type: String})
  path?: string;

  @property({type: Boolean})
  _canUpload?: boolean = false; // restAPI can return undefined

  @property({type: String})
  _inheritFromFilter?: RepoName;

  @property({type: Object})
  _query: unknown = this._getInheritFromSuggestions.bind(this);

  @property({type: Array})
  _ownerOf?: GitRef[];

  @property({type: Object})
  _capabilities?: CapabilityInfoMap;

  @property({type: Object})
  _groups?: unknown;

  @property({type: Object})
  _inheritsFrom?: ProjectInfo | null;

  @property({type: Object})
  _labels?: LabelNameToLabelTypeInfoMap;

  @property({type: Object})
  _local?: LocalAccessSectionInfo;

  @property({type: Boolean, observer: '_handleEditingChanged'})
  _editing = false;

  @property({type: Boolean})
  _modified = false;

  @property({type: Array})
  _sections?: PermissionAccessSection[];

  @property({type: Array})
  _weblinks?: string[];

  @property({type: Boolean})
  _loading = true;

  private _originalInheritsFrom?: ProjectInfo | null;

  /** @override */
  created() {
    super.created();
    this.addEventListener('access-modified', () =>
      this._handleAccessModified()
    );
  }

  _handleAccessModified() {
    this._modified = true;
  }

  _repoChanged(repo: RepoName) {
    this._loading = true;

    if (!repo) {
      return Promise.resolve();
    }

    return this._reload(repo);
  }

  _reload(repo: RepoName) {
    const errFn = (response?: Response | null) => {
      this.dispatchEvent(
        new CustomEvent('page-error', {
          detail: {response},
          composed: true,
          bubbles: true,
        })
      );
    };

    this._editing = false;

    // Always reset sections when a project changes.
    this._sections = [];
    const sectionsPromises = this.$.restAPI
      .getRepoAccessRights(repo, errFn)
      .then(res => {
        if (!res) {
          return Promise.resolve(undefined);
        }

        // Keep a copy of the original inherit from values separate from
        // the ones data bound to gr-autocomplete, so the original value
        // can be restored if the user cancels.
        this._inheritsFrom = res.inherits_from
          ? {
              ...res.inherits_from,
            }
          : null;
        this._originalInheritsFrom = res.inherits_from
          ? {
              ...res.inherits_from,
            }
          : null;
        // Initialize the filter value so when the user clicks edit, the
        // current value appears. If there is no parent repo, it is
        // initialized as an empty string.
        this._inheritFromFilter = this._inheritsFrom
          ? this._inheritsFrom.name
          : ('' as RepoName);
        this._local = res.local;
        this._groups = res.groups;
        this._weblinks = res.config_web_links || [];
        this._canUpload = res.can_upload;
        this._ownerOf = res.owner_of || [];
        return toSortedPermissionsArray(this._local);
      });

    const capabilitiesPromises = this.$.restAPI
      .getCapabilities(errFn)
      .then(res => {
        if (!res) {
          return Promise.resolve(undefined);
        }

        return res;
      });

    const labelsPromises = this.$.restAPI.getRepo(repo, errFn).then(res => {
      if (!res) {
        return Promise.resolve(undefined);
      }

      return res.labels;
    });

    return Promise.all([
      sectionsPromises,
      capabilitiesPromises,
      labelsPromises,
    ]).then(([sections, capabilities, labels]) => {
      this._capabilities = capabilities;
      this._labels = labels;
      this._sections = sections;
      this._loading = false;
    });
  }

  _handleUpdateInheritFrom(e) {
    if (!this._inheritsFrom) {
      this._inheritsFrom = {
        id: e.detail.value,
        name: this._inheritFromFilter,
      };
    } else {
      this._inheritsFrom.id = e.detail.value;
      this._inheritsFrom.name = this._inheritFromFilter;
    }
    this._handleAccessModified();
  }

  _getInheritFromSuggestions() {
    return this.$.restAPI
      .getRepos(this._inheritFromFilter, MAX_AUTOCOMPLETE_RESULTS)
      .then(response => {
        const projects = [];
        for (const key in response) {
          if (!hasOwnProperty(response, key)) {
            continue;
          }
          projects.push({
            name: response[key].name,
            value: response[key].id,
          });
        }
        return projects;
      });
  }

  _computeLoadingClass(loading: boolean) {
    return loading ? 'loading' : '';
  }

  _handleEdit() {
    this._editing = !this._editing;
  }

  _editOrCancel(editing: boolean) {
    return editing ? 'Cancel' : 'Edit';
  }

  _computeWebLinkClass(weblinks?: string[]) {
    return weblinks && weblinks.length ? 'show' : '';
  }

  _computeShowInherit(inheritsFrom?: RepoName) {
    return inheritsFrom ? 'show' : '';
  }

  _handleAddedSectionRemoved(e) {
    if (!this._sections) {
      return;
    }
    const index = Number(e.model.index);
    if (isNaN(index)) {
      return;
    }
    this._sections = this._sections
      .slice(0, index)
      .concat(this._sections.slice(index + 1, this._sections.length));
  }

  _handleEditingChanged(editing: boolean, editingOld: boolean) {
    // Ignore when editing gets set initially.
    if (!editingOld || editing) {
      return;
    }
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
   * @param value value to add if the type is 'add'
   * @return
   */
  _updateAddRemoveObj(
    addRemoveObj: {add: {[updateId: string]: TreeNodeWithDelete}; remove: {}},
    path: string[],
    type: UpdateType,
    value?: unknown
  ) {
    let curPos = addRemoveObj[type];
    for (const item of path) {
      if (!curPos[item]) {
        if (item === path[path.length - 1] && type === UpdateType.Remove) {
          if (path[path.length - 2] === 'permissions') {
            curPos[item] = {rules: {}};
          } else if (path.length === 1) {
            curPos[item] = {permissions: {}};
          } else {
            curPos[item] = {};
          }
        } else if (item === path[path.length - 1] && type === UpdateType.Add) {
          curPos[item] = value;
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
  _recursivelyRemoveDeleted(obj: TreeNodeWithDelete) {
    for (const k in obj) {
      if (!hasOwnProperty(obj, k)) {
        continue;
      }
      const node = obj[k];
      if (typeof node === 'object') {
        if (node.deleted) {
          delete obj[k];
          return;
        }
        this._recursivelyRemoveDeleted(node);
      }
    }
  }

  _recursivelyUpdateAddRemoveObj(
    obj: TreeNodeWithDelete,
    addRemoveObj: {add: {[updateId: string]: TreeNodeWithDelete}; remove: {}},
    pathFromRoot: string[] = []
  ) {
    for (const k in obj) {
      if (!hasOwnProperty(obj, k)) {
        continue;
      }
      const node = obj[k];
      if (typeof node === 'object') {
        const updatedId = node.updatedId;
        const ref = updatedId ? updatedId : k;
        if (node.deleted) {
          this._updateAddRemoveObj(
            addRemoveObj,
            pathFromRoot.concat(k),
            UpdateType.Remove
          );
          continue;
        } else if (node.modified) {
          this._updateAddRemoveObj(
            addRemoveObj,
            pathFromRoot.concat(k),
            UpdateType.Remove
          );
          this._updateAddRemoveObj(
            addRemoveObj,
            pathFromRoot.concat(ref),
            UpdateType.Add,
            node
          );
          /* Special case for ref changes because they need to be added and
           removed in a different way. The new ref needs to include all
           changes but also the initial state. To do this, instead of
           continuing with the same recursion, just remove anything that is
           deleted in the current state. */
          if (updatedId && updatedId !== k) {
            this._recursivelyRemoveDeleted(addRemoveObj.add[updatedId]);
          }
          continue;
        } else if (node.added) {
          this._updateAddRemoveObj(
            addRemoveObj,
            pathFromRoot.concat(ref),
            UpdateType.Add,
            node
          );
          /**
           * As add / delete both can happen in the new section,
           * so here to make sure it will remove the deleted ones.
           *
           * @see Issue 11339
           */
          this._recursivelyRemoveDeleted(addRemoveObj.add[k]);
          continue;
        }
        this._recursivelyUpdateAddRemoveObj(
          node,
          addRemoveObj,
          pathFromRoot.concat(k)
        );
      }
    }
  }

  /**
   * Returns an object formatted for saving or submitting access changes for
   * review
   *
   * @return
   */
  _computeAddAndRemove() {
    const addRemoveObj = {
      add: {},
      remove: {},
    };

    const originalInheritsFromId = this._originalInheritsFrom
      ? singleDecodeURL(this._originalInheritsFrom.id)
      : null;
    const inheritsFromId = this._inheritsFrom
      ? singleDecodeURL(this._inheritsFrom.id)
      : null;

    const inheritFromChanged =
      // Inherit from changed
      (originalInheritsFromId && originalInheritsFromId !== inheritsFromId) ||
      // Inherit from added (did not have one initially);
      (!originalInheritsFromId && inheritsFromId);

    this._recursivelyUpdateAddRemoveObj(this._local, addRemoveObj);

    if (inheritFromChanged) {
      addRemoveObj.parent = inheritsFromId;
    }
    return addRemoveObj;
  }

  _handleCreateSection() {
    if (!this._local) {
      return;
    }
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
    // Template already instantiated at this point
    (this.root!.querySelector(
      'gr-access-section:last-of-type'
    ) as GrAccessSection).editReference();
  }

  _getObjforSave(): ProjectAccessInput | undefined {
    const addRemoveObj: ProjectAccessInput = this._computeAddAndRemove();
    // If there are no changes, don't actually save.
    if (
      !Object.keys(addRemoveObj.add).length &&
      !Object.keys(addRemoveObj.remove).length &&
      !addRemoveObj.parent
    ) {
      this.dispatchEvent(
        new CustomEvent('show-alert', {
          detail: {message: NOTHING_TO_SAVE},
          bubbles: true,
          composed: true,
        })
      );
      return;
    }
    const obj: ProjectAccessInput = {
      add: addRemoveObj.add,
      remove: addRemoveObj.remove,
    };
    if (addRemoveObj.parent) {
      obj.parent = addRemoveObj.parent;
    }
    return obj;
  }

  _handleSave(e: Event) {
    const obj = this._getObjforSave();
    if (!obj) {
      return;
    }
    const button = e && (e.target as GrButton);
    if (button) {
      button.loading = true;
    }
    const repo = this.repo;
    if (!repo) {
      return Promise.resolve();
    }
    return this.$.restAPI
      .setRepoAccessRights(repo, obj)
      .then(() => {
        this._reload(repo);
      })
      .finally(() => {
        this._modified = false;
        if (button) {
          button.loading = false;
        }
      });
  }

  _handleSaveForReview(e: Event) {
    const obj = this._getObjforSave();
    if (!obj) {
      return;
    }
    const button = e && (e.target as GrButton);
    if (button) {
      button.loading = true;
    }
    if (!this.repo) {
      return;
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

  _computeSaveReviewBtnClass(canUpload?: boolean) {
    return !canUpload ? 'invisible' : '';
  }

  _computeSaveBtnClass(ownerOf?: GitRef[]) {
    return ownerOf && ownerOf.length === 0 ? 'invisible' : '';
  }

  _computeMainClass(
    ownerOf: GitRef[] | undefined,
    canUpload: boolean,
    editing: boolean
  ) {
    const classList = [];
    if ((ownerOf && ownerOf.length > 0) || canUpload) {
      classList.push('admin');
    }
    if (editing) {
      classList.push('editing');
    }
    return classList.join(' ');
  }

  _computeParentHref(repoName: RepoName) {
    return getBaseUrl() + `/admin/repos/${encodeURL(repoName, true)},access`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-access': GrRepoAccess;
  }
}
