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
import '../../../styles/gr-font-styles';
import '../../../styles/gr-menu-page-styles';
import '../../../styles/gr-subpage-styles';
import '../../../styles/shared-styles';
import '../gr-access-section/gr-access-section';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-repo-access_html';
import {encodeURL, getBaseUrl, singleDecodeURL} from '../../../utils/url-util';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {toSortedPermissionsArray} from '../../../utils/access-util';
import {customElement, property} from '@polymer/decorators';
import {
  RepoName,
  ProjectInfo,
  CapabilityInfoMap,
  LabelNameToLabelTypeInfoMap,
  ProjectAccessInput,
  GitRef,
  UrlEncodedRepoName,
  ProjectAccessGroups,
} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrAccessSection} from '../gr-access-section/gr-access-section';
import {
  AutocompleteQuery,
  AutocompleteSuggestion,
} from '../../shared/gr-autocomplete/gr-autocomplete';
import {
  EditableLocalAccessSectionInfo,
  PermissionAccessSection,
  PropertyTreeNode,
  PrimitiveValue,
} from './gr-repo-access-interfaces';
import {firePageError, fireAlert} from '../../../utils/event-util';
import {appContext} from '../../../services/app-context';
import {WebLinkInfo} from '../../../types/diff';

const NOTHING_TO_SAVE = 'No changes to save.';

const MAX_AUTOCOMPLETE_RESULTS = 50;

/**
 * Fired when save is a no-op
 *
 * @event show-alert
 */
@customElement('gr-repo-access')
export class GrRepoAccess extends PolymerElement {
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
  _query: AutocompleteQuery;

  @property({type: Array})
  _ownerOf?: GitRef[];

  @property({type: Object})
  _capabilities?: CapabilityInfoMap;

  @property({type: Object})
  _groups?: ProjectAccessGroups;

  @property({type: Object})
  _inheritsFrom?: ProjectInfo;

  @property({type: Object})
  _labels?: LabelNameToLabelTypeInfoMap;

  @property({type: Object})
  _local?: EditableLocalAccessSectionInfo;

  @property({type: Boolean, observer: '_handleEditingChanged'})
  _editing = false;

  @property({type: Boolean})
  _modified = false;

  @property({type: Array})
  _sections?: PermissionAccessSection[];

  @property({type: Array})
  _weblinks?: WebLinkInfo[];

  @property({type: Boolean})
  _loading = true;

  private originalInheritsFrom?: ProjectInfo;

  private readonly restApiService = appContext.restApiService;

  constructor() {
    super();
    this._query = () => this._getInheritFromSuggestions();
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
      firePageError(response);
    };

    this._editing = false;

    // Always reset sections when a project changes.
    this._sections = [];
    const sectionsPromises = this.restApiService
      .getRepoAccessRights(repo, errFn)
      .then(res => {
        if (!res) {
          return Promise.resolve(undefined);
        }

        // Keep a copy of the original inherit from values separate from
        // the ones data bound to gr-autocomplete, so the original value
        // can be restored if the user cancels.
        if (res.inherits_from) {
          this._inheritsFrom = {...res.inherits_from};
          this.originalInheritsFrom = {...res.inherits_from};
        } else {
          this._inheritsFrom = undefined;
          this.originalInheritsFrom = undefined;
        }
        // Initialize the filter value so when the user clicks edit, the
        // current value appears. If there is no parent repo, it is
        // initialized as an empty string.
        this._inheritFromFilter = res.inherits_from
          ? res.inherits_from.name
          : ('' as RepoName);
        // 'as EditableLocalAccessSectionInfo' is required because res.local
        // type doesn't have index signature
        this._local = res.local as EditableLocalAccessSectionInfo;
        this._groups = res.groups;
        this._weblinks = res.config_web_links || [];
        this._canUpload = res.can_upload;
        this._ownerOf = res.owner_of || [];
        return toSortedPermissionsArray(this._local);
      });

    const capabilitiesPromises = this.restApiService
      .getCapabilities(errFn)
      .then(res => {
        if (!res) {
          return Promise.resolve(undefined);
        }

        return res;
      });

    const labelsPromises = this.restApiService
      .getRepo(repo, errFn)
      .then(res => {
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

  _handleUpdateInheritFrom(e: CustomEvent<{value: string}>) {
    this._inheritsFrom = {
      ...(this._inheritsFrom ?? {}),
      id: e.detail.value as UrlEncodedRepoName,
      name: this._inheritFromFilter,
    };
    this._handleAccessModified();
  }

  _getInheritFromSuggestions(): Promise<AutocompleteSuggestion[]> {
    return this.restApiService
      .getRepos(this._inheritFromFilter, MAX_AUTOCOMPLETE_RESULTS)
      .then(response => {
        const projects: AutocompleteSuggestion[] = [];
        if (!response) {
          return projects;
        }
        for (const item of response) {
          projects.push({
            name: item.name,
            value: item.id,
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

  _computeShowInherit(inheritsFrom?: ProjectInfo) {
    return inheritsFrom?.id?.length ? 'show' : '';
  }

  // TODO(TS): Unclear what is model here, provide a better explanation
  _handleAddedSectionRemoved(e: CustomEvent & {model: {index: string}}) {
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
      this._inheritsFrom = this.originalInheritsFrom
        ? {...this.originalInheritsFrom}
        : undefined;
      this._inheritFromFilter = this.originalInheritsFrom?.name;
    }
    if (!this._local) {
      return;
    }
    for (const key of Object.keys(this._local)) {
      if (this._local[key].added) {
        delete this._local[key];
      }
    }
  }

  _updateRemoveObj(addRemoveObj: {remove: PropertyTreeNode}, path: string[]) {
    let curPos: PropertyTreeNode = addRemoveObj.remove;
    for (const item of path) {
      if (!curPos[item]) {
        if (item === path[path.length - 1]) {
          if (path[path.length - 2] === 'permissions') {
            curPos[item] = {rules: {}};
          } else if (path.length === 1) {
            curPos[item] = {permissions: {}};
          } else {
            curPos[item] = {};
          }
        } else {
          curPos[item] = {};
        }
      }
      // The last item can be a PrimitiveValue, but we don't use it
      // All intermediate items are PropertyTreeNode
      // TODO(TS): rewrite this loop and process the last item explicitly
      curPos = curPos[item] as PropertyTreeNode;
    }
    return addRemoveObj;
  }

  _updateAddObj(
    addRemoveObj: {add: PropertyTreeNode},
    path: string[],
    value: PropertyTreeNode | PrimitiveValue
  ) {
    let curPos: PropertyTreeNode = addRemoveObj.add;
    for (const item of path) {
      if (!curPos[item]) {
        if (item === path[path.length - 1]) {
          curPos[item] = value;
        } else {
          curPos[item] = {};
        }
      }
      // The last item can be a PrimitiveValue, but we don't use it
      // All intermediate items are PropertyTreeNode
      // TODO(TS): rewrite this loop and process the last item explicitly
      curPos = curPos[item] as PropertyTreeNode;
    }
    return addRemoveObj;
  }

  /**
   * Used to recursively remove any objects with a 'deleted' bit.
   */
  _recursivelyRemoveDeleted(obj?: PropertyTreeNode) {
    if (!obj) return;
    for (const k of Object.keys(obj)) {
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
    obj: PropertyTreeNode | undefined,
    addRemoveObj: {
      add: PropertyTreeNode;
      remove: PropertyTreeNode;
    },
    path: string[] = []
  ) {
    if (!obj) return;
    for (const k of Object.keys(obj)) {
      const node = obj[k];
      if (typeof node === 'object') {
        const updatedId = node.updatedId;
        const ref = updatedId ? updatedId : k;
        if (node.deleted) {
          this._updateRemoveObj(addRemoveObj, path.concat(k));
          continue;
        } else if (node.modified) {
          this._updateRemoveObj(addRemoveObj, path.concat(k));
          this._updateAddObj(addRemoveObj, path.concat(ref), node);
          /* Special case for ref changes because they need to be added and
           removed in a different way. The new ref needs to include all
           changes but also the initial state. To do this, instead of
           continuing with the same recursion, just remove anything that is
           deleted in the current state. */
          if (updatedId && updatedId !== k) {
            this._recursivelyRemoveDeleted(
              addRemoveObj.add[updatedId] as PropertyTreeNode
            );
          }
          continue;
        } else if (node.added) {
          this._updateAddObj(addRemoveObj, path.concat(ref), node);
          /**
           * As add / delete both can happen in the new section,
           * so here to make sure it will remove the deleted ones.
           *
           * @see Issue 11339
           */
          this._recursivelyRemoveDeleted(
            addRemoveObj.add[k] as PropertyTreeNode
          );
          continue;
        }
        this._recursivelyUpdateAddRemoveObj(node, addRemoveObj, path.concat(k));
      }
    }
  }

  /**
   * Returns an object formatted for saving or submitting access changes for
   * review
   */
  _computeAddAndRemove() {
    const addRemoveObj: {
      add: PropertyTreeNode;
      remove: PropertyTreeNode;
      parent?: string | null;
    } = {
      add: {},
      remove: {},
    };

    const originalInheritsFromId = this.originalInheritsFrom
      ? singleDecodeURL(this.originalInheritsFrom.id)
      : undefined;
    const inheritsFromId = this._inheritsFrom
      ? singleDecodeURL(this._inheritsFrom.id)
      : undefined;

    const inheritFromChanged =
      // Inherit from changed
      (originalInheritsFromId && originalInheritsFromId !== inheritsFromId) ||
      // Inherit from added (did not have one initially);
      (!originalInheritsFromId && inheritsFromId);

    if (!this._local) {
      return addRemoveObj;
    }

    this._recursivelyUpdateAddRemoveObj(
      this._local as unknown as PropertyTreeNode,
      addRemoveObj
    );

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
    (
      this.root!.querySelector(
        'gr-access-section:last-of-type'
      ) as GrAccessSection
    ).editReference();
  }

  _getObjforSave(): ProjectAccessInput | undefined {
    const addRemoveObj = this._computeAddAndRemove();
    // If there are no changes, don't actually save.
    if (
      !Object.keys(addRemoveObj.add).length &&
      !Object.keys(addRemoveObj.remove).length &&
      !addRemoveObj.parent
    ) {
      fireAlert(this, NOTHING_TO_SAVE);
      return;
    }
    const obj: ProjectAccessInput = {
      add: addRemoveObj.add,
      remove: addRemoveObj.remove,
    } as unknown as ProjectAccessInput;
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
    return this.restApiService
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
    return this.restApiService
      .setRepoAccessRightsForReview(this.repo, obj)
      .then(change => {
        GerritNav.navigateToChange(change, {});
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
