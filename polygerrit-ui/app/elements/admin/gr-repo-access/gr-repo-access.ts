/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-access-section/gr-access-section';
import {singleDecodeURL} from '../../../utils/url-util';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {toSortedPermissionsArray} from '../../../utils/access-util';
import {
  RepoName,
  ProjectInfo,
  CapabilityInfoMap,
  LabelNameToLabelTypeInfoMap,
  ProjectAccessInput,
  GitRef,
  UrlEncodedRepoName,
  RepoAccessGroups,
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
import {getAppContext} from '../../../services/app-context';
import {WebLinkInfo} from '../../../types/diff';
import {fontStyles} from '../../../styles/gr-font-styles';
import {menuPageStyles} from '../../../styles/gr-menu-page-styles';
import {subpageStyles} from '../../../styles/gr-subpage-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, css, html} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {assertIsDefined} from '../../../utils/common-util';
import {ValueChangedEvent} from '../../../types/events';
import {ifDefined} from 'lit/directives/if-defined.js';
import {resolve} from '../../../models/dependency';
import {createChangeUrl} from '../../../models/views/change';
import {throwingErrorCallback} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {createRepoUrl, RepoDetailView} from '../../../models/views/repo';

const NOTHING_TO_SAVE = 'No changes to save.';

const MAX_AUTOCOMPLETE_RESULTS = 50;

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-access': GrRepoAccess;
  }
}

/**
 * Fired when save is a no-op
 *
 * @event show-alert
 */
@customElement('gr-repo-access')
export class GrRepoAccess extends LitElement {
  @query('gr-access-section:last-of-type') accessSection?: GrAccessSection;

  @property({type: String})
  repo?: RepoName;

  // private but used in test
  @state() canUpload?: boolean = false; // restAPI can return undefined

  // private but used in test
  @state() inheritFromFilter?: RepoName;

  // private but used in test
  @state() ownerOf?: GitRef[];

  // private but used in test
  @state() capabilities?: CapabilityInfoMap;

  // private but used in test
  @state() groups?: RepoAccessGroups;

  // private but used in test
  @state() inheritsFrom?: ProjectInfo;

  // private but used in test
  @state() labels?: LabelNameToLabelTypeInfoMap;

  // private but used in test
  @state() local?: EditableLocalAccessSectionInfo;

  // private but used in test
  @state() editing = false;

  // private but used in test
  @state() modified = false;

  // private but used in test
  @state() sections?: PermissionAccessSection[];

  @state() private weblinks?: WebLinkInfo[];

  // private but used in test
  @state() loading = true;

  // private but used in the tests
  originalInheritsFrom?: ProjectInfo;

  private readonly query: AutocompleteQuery;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getNavigation = resolve(this, navigationToken);

  constructor() {
    super();
    this.query = () => this.getInheritFromSuggestions();
    this.addEventListener('access-modified', () =>
      this._handleAccessModified()
    );
  }

  static override get styles() {
    return [
      fontStyles,
      menuPageStyles,
      subpageStyles,
      sharedStyles,
      css`
        gr-button,
        #inheritsFrom,
        #editInheritFromInput,
        .editing #inheritFromName,
        .weblinks,
        .editing .invisible {
          display: none;
        }
        #inheritsFrom.show {
          display: flex;
          min-height: 2em;
          align-items: center;
        }
        .weblink {
          margin-right: var(--spacing-xs);
        }
        gr-access-section {
          margin-top: var(--spacing-l);
        }
        .weblinks.show,
        .referenceContainer {
          display: block;
        }
        .rightsText {
          margin-right: var(--spacing-s);
        }

        .editing gr-button,
        .admin #editBtn {
          display: inline-block;
          margin: var(--spacing-l) 0;
        }
        .editing #editInheritFromInput {
          display: inline-block;
        }
      `,
    ];
  }

  override render() {
    return html`
      <div class="main ${this.computeMainClass()}">
        <div id="loading" class=${this.loading ? 'loading' : ''}>
          Loading...
        </div>
        <div id="loadedContent" class=${this.loading ? 'loading' : ''}>
          <h3
            id="inheritsFrom"
            class="heading-3 ${this.editing || this.inheritsFrom?.id?.length
              ? 'show'
              : ''}"
          >
            <span class="rightsText">Rights Inherit From</span>
            <a
              id="inheritFromName"
              href=${this.computeParentHref()}
              rel="noopener"
            >
              ${this.inheritsFrom?.name}</a
            >
            <gr-autocomplete
              id="editInheritFromInput"
              .text=${this.inheritFromFilter}
              .query=${this.query}
              @commit=${(e: ValueChangedEvent) => {
                this.handleUpdateInheritFrom(e);
              }}
              @bind-value-changed=${(e: ValueChangedEvent) => {
                this.handleUpdateInheritFrom(e);
              }}
              @text-changed=${(e: ValueChangedEvent) => {
                this.handleEditInheritFromTextChanged(e);
              }}
            ></gr-autocomplete>
          </h3>
          <div class="weblinks ${this.weblinks?.length ? 'show' : ''}">
            History:
            ${this.weblinks?.map(webLink => this.renderWebLinks(webLink))}
          </div>
          ${this.sections?.map((section, index) =>
            this.renderPermissionSections(section, index)
          )}
          <div class="referenceContainer">
            <gr-button
              id="addReferenceBtn"
              @click=${() => this.handleCreateSection()}
              >Add Reference</gr-button
            >
          </div>
          <div>
            <gr-button
              id="editBtn"
              @click=${() => {
                this.handleEdit();
              }}
              >${this.editing ? 'Cancel' : 'Edit'}</gr-button
            >
            <gr-button
              id="saveBtn"
              class=${this.ownerOf && this.ownerOf.length === 0
                ? 'invisible'
                : ''}
              primary
              ?disabled=${!this.modified}
              @click=${this.handleSave}
              >Save</gr-button
            >
            <gr-button
              id="saveReviewBtn"
              class=${!this.canUpload ? 'invisible' : ''}
              primary
              ?disabled=${!this.modified}
              @click=${this.handleSaveForReview}
              >Save for review</gr-button
            >
          </div>
        </div>
      </div>
    `;
  }

  private renderWebLinks(webLink: WebLinkInfo) {
    return html`
      <a
        class="weblink"
        href=${webLink.url}
        rel="noopener"
        target=${ifDefined(webLink.target)}
      >
        ${webLink.name}
      </a>
    `;
  }

  private renderPermissionSections(
    section: PermissionAccessSection,
    index: number
  ) {
    return html`
      <gr-access-section
        .capabilities=${this.capabilities}
        .section=${section}
        .labels=${this.labels}
        .canUpload=${this.canUpload}
        .editing=${this.editing}
        .ownerOf=${this.ownerOf}
        .groups=${this.groups}
        .repo=${this.repo}
        @added-section-removed=${() => {
          this.handleAddedSectionRemoved(index);
        }}
        @section-changed=${(e: ValueChangedEvent<PermissionAccessSection>) => {
          this.handleAccessSectionChanged(e, index);
        }}
      ></gr-access-section>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('repo')) {
      this._repoChanged(this.repo);
    }

    if (changedProperties.has('editing')) {
      this.handleEditingChanged(changedProperties.get('editing') as boolean);
      this.requestUpdate();
    }
  }

  _handleAccessModified() {
    this.modified = true;
  }

  _repoChanged(repo?: RepoName) {
    this.loading = true;

    if (!repo) {
      return Promise.resolve();
    }

    return this.reload(repo);
  }

  private reload(repo: RepoName) {
    const errFn = (response?: Response | null) => {
      firePageError(response);
    };

    this.editing = false;

    // Always reset sections when a repo changes.
    this.sections = [];
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
          this.inheritsFrom = {...res.inherits_from};
          this.originalInheritsFrom = {...res.inherits_from};
        } else {
          this.inheritsFrom = undefined;
          this.originalInheritsFrom = undefined;
        }
        // Initialize the filter value so when the user clicks edit, the
        // current value appears. If there is no parent repo, it is
        // initialized as an empty string.
        this.inheritFromFilter = res.inherits_from
          ? res.inherits_from.name
          : ('' as RepoName);
        // 'as EditableLocalAccessSectionInfo' is required because res.local
        // type doesn't have index signature
        this.local = res.local as EditableLocalAccessSectionInfo;
        this.groups = res.groups;
        this.weblinks = res.config_web_links || [];
        this.canUpload = res.can_upload;
        this.ownerOf = res.owner_of || [];
        return toSortedPermissionsArray(this.local);
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
      this.capabilities = capabilities;
      this.labels = labels;
      this.sections = sections;
      this.loading = false;
    });
  }

  // private but used in test
  handleUpdateInheritFrom(e: ValueChangedEvent) {
    this.inheritsFrom = {
      ...(this.inheritsFrom ?? {}),
      id: e.detail.value as UrlEncodedRepoName,
      name: this.inheritFromFilter,
    };
    this._handleAccessModified();
  }

  private getInheritFromSuggestions(): Promise<AutocompleteSuggestion[]> {
    return this.restApiService
      .getRepos(
        this.inheritFromFilter,
        MAX_AUTOCOMPLETE_RESULTS,
        /* offset=*/ undefined,
        throwingErrorCallback
      )
      .then(response => {
        const repos: AutocompleteSuggestion[] = [];
        if (!response) {
          return repos;
        }
        for (const item of response) {
          repos.push({
            name: item.name,
            value: item.id,
          });
        }
        return repos;
      });
  }

  private handleEdit() {
    this.editing = !this.editing;
  }

  // private but used in tests
  handleAddedSectionRemoved(index: number) {
    if (!this.sections) return;
    assertIsDefined(this.local, 'local');
    delete this.local[this.sections[index].id];
    this.sections = this.sections
      .slice(0, index)
      .concat(this.sections.slice(index + 1, this.sections.length));
  }

  private handleEditingChanged(editingOld: boolean) {
    // Ignore when editing gets set initially.
    if (!editingOld || this.editing) {
      return;
    }
    // Remove any unsaved but added refs.
    if (this.sections) {
      this.sections = this.sections.filter(p => !p.value.added);
    }
    // Restore inheritFrom.
    if (this.inheritsFrom) {
      this.inheritsFrom = this.originalInheritsFrom
        ? {...this.originalInheritsFrom}
        : undefined;
      this.inheritFromFilter = this.originalInheritsFrom?.name;
    }
    if (!this.local) {
      return;
    }
    for (const key of Object.keys(this.local)) {
      if (this.local[key].added) {
        delete this.local[key];
      }
    }
  }

  private updateRemoveObj(
    addRemoveObj: {remove: PropertyTreeNode},
    path: string[]
  ) {
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

  private updateAddObj(
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
   *
   * private but used in test
   */
  recursivelyRemoveDeleted(obj?: PropertyTreeNode) {
    if (!obj) return;
    for (const k of Object.keys(obj)) {
      const node = obj[k];
      if (typeof node === 'object') {
        if (node.deleted) {
          delete obj[k];
          return;
        }
        this.recursivelyRemoveDeleted(node);
      }
    }
  }

  // private but used in test
  recursivelyUpdateAddRemoveObj(
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
          this.updateRemoveObj(addRemoveObj, path.concat(k));
          continue;
        } else if (node.modified) {
          this.updateRemoveObj(addRemoveObj, path.concat(k));
          this.updateAddObj(addRemoveObj, path.concat(ref), node);
          /* Special case for ref changes because they need to be added and
          removed in a different way. The new ref needs to include all
          changes but also the initial state. To do this, instead of
          continuing with the same recursion, just remove anything that is
          deleted in the current state. */
          if (updatedId && updatedId !== k) {
            this.recursivelyRemoveDeleted(
              addRemoveObj.add[updatedId] as PropertyTreeNode
            );
          }
          continue;
        } else if (node.added) {
          this.updateAddObj(addRemoveObj, path.concat(ref), node);
          /**
           * As add / delete both can happen in the new section,
           * so here to make sure it will remove the deleted ones.
           *
           * @see Issue 11339
           */
          this.recursivelyRemoveDeleted(
            addRemoveObj.add[k] as PropertyTreeNode
          );
          continue;
        }
        this.recursivelyUpdateAddRemoveObj(node, addRemoveObj, path.concat(k));
      }
    }
  }

  /**
   * Returns an object formatted for saving or submitting access changes for
   * review
   *
   * private but used in test
   */
  computeAddAndRemove() {
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
    const inheritsFromId = this.inheritsFrom
      ? singleDecodeURL(this.inheritsFrom.id)
      : undefined;

    const inheritFromChanged =
      // Inherit from changed
      (originalInheritsFromId && originalInheritsFromId !== inheritsFromId) ||
      // Inherit from added (did not have one initially);
      (!originalInheritsFromId && inheritsFromId);

    if (!this.local) {
      return addRemoveObj;
    }

    this.recursivelyUpdateAddRemoveObj(
      this.local as unknown as PropertyTreeNode,
      addRemoveObj
    );

    if (inheritFromChanged) {
      addRemoveObj.parent = inheritsFromId;
    }
    return addRemoveObj;
  }

  private handleCreateSection() {
    if (!this.local) return;
    let newRef = 'refs/for/*';
    // Avoid using an already used key for the placeholder, since it
    // immediately gets added to an object.
    while (this.local[newRef]) {
      newRef = `${newRef}*`;
    }
    const section = {permissions: {}, added: true};
    this.sections!.push({id: newRef as GitRef, value: section});
    this.local[newRef] = section;
    this.requestUpdate();
    assertIsDefined(this.accessSection, 'accessSection');
    // Template already instantiated at this point
    this.accessSection.editReference();
  }

  private getObjforSave(): ProjectAccessInput | undefined {
    const addRemoveObj = this.computeAddAndRemove();
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

  // private but used in test
  handleSave(e: Event) {
    const obj = this.getObjforSave();
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
        this.reload(repo);
      })
      .finally(() => {
        this.modified = false;
        if (button) {
          button.loading = false;
        }
      });
  }

  // private but used in test
  handleSaveForReview(e: Event) {
    const obj = this.getObjforSave();
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
        this.getNavigation().setUrl(createChangeUrl({change}));
      })
      .finally(() => {
        this.modified = false;
        if (button) {
          button.loading = false;
        }
      });
  }

  // private but used in test
  computeMainClass() {
    const classList = [];
    if ((this.ownerOf && this.ownerOf.length > 0) || this.canUpload) {
      classList.push('admin');
    }
    if (this.editing) {
      classList.push('editing');
    }
    return classList.join(' ');
  }

  computeParentHref() {
    if (!this.inheritsFrom?.name) return '';
    return createRepoUrl({
      repo: this.inheritsFrom.name,
      detail: RepoDetailView.ACCESS,
    });
  }

  private handleEditInheritFromTextChanged(e: ValueChangedEvent) {
    this.inheritFromFilter = e.detail.value as RepoName;
  }

  private handleAccessSectionChanged(
    e: ValueChangedEvent<PermissionAccessSection>,
    index: number
  ) {
    this.sections![index] = e.detail.value;
    this.requestUpdate();
  }
}
