/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-list-view/gr-list-view';
import '../../shared/gr-weblink/gr-weblink';
import '../gr-create-repo-dialog/gr-create-repo-dialog';
import '../gr-create-change-dialog/gr-create-change-dialog';
import {
  BranchName,
  ProjectInfoWithName,
  RepoName,
  WebLinkInfo,
} from '../../../types/common';
import {GrCreateRepoDialog} from '../gr-create-repo-dialog/gr-create-repo-dialog';
import {RepoState} from '../../../constants/constants';
import {fireTitleChange} from '../../../utils/event-util';
import {getAppContext} from '../../../services/app-context';
import {tableStyles} from '../../../styles/gr-table-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {
  AdminChildView,
  AdminViewState,
  createAdminUrl,
} from '../../../models/views/admin';
import {createSearchUrl} from '../../../models/views/search';
import {modalStyles} from '../../../styles/gr-modal-styles';
import {createRepoUrl} from '../../../models/views/repo';
import {userModelToken} from '../../../models/user/user-model';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {assertIsDefined} from '../../../utils/common-util';
import {GrCreateChangeDialog} from '../gr-create-change-dialog/gr-create-change-dialog';
import {BindValueChangeEvent} from '../../../types/events';

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-list': GrRepoList;
  }
}

@customElement('gr-repo-list')
export class GrRepoList extends LitElement {
  @query('#createModal') private createModal?: HTMLDialogElement;

  @query('#createChangeModal') private createChangeModal?: HTMLDialogElement;

  @query('#createNewModal') private createNewModal?: GrCreateRepoDialog;

  @query('#createRepoModal')
  private readonly createRepoModal?: HTMLDialogElement;

  @query('#createNewChangeModal')
  private readonly createNewChangeModal?: GrCreateChangeDialog;

  @property({type: Object})
  params?: AdminViewState;

  @state() offset = 0;

  @state() newRepoName = false;

  @state() createNewCapability = false;

  @state() repos: ProjectInfoWithName[] = [];

  @state() reposPerPage = 25;

  @state() loading = true;

  @state() filter = '';

  @state() private loggedIn = false;

  @state() private canCreateChange = false;

  // Used to create a change
  @state() private repo?: RepoName;

  // Used to create a change
  @state() private branch = '' as BranchName;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getUserModel = resolve(this, userModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getUserModel().capabilities$,
      x => (this.createNewCapability = x?.createProject ?? false)
    );
    subscribe(
      this,
      () => this.getUserModel().loggedIn$,
      x => (this.loggedIn = x)
    );
  }

  override connectedCallback() {
    super.connectedCallback();
    fireTitleChange('Repos');
    this.maybeOpenCreateModal(this.params);
  }

  static override get styles() {
    return [
      tableStyles,
      sharedStyles,
      modalStyles,
      css`
        .genericList tr td:last-of-type {
          text-align: left;
        }
        .genericList tr th:last-of-type {
          text-align: left;
        }
        .readOnly {
          text-align: center;
        }
        .changesLink,
        .name,
        .repositoryBrowser,
        .readOnly {
          white-space: nowrap;
        }
      `,
    ];
  }

  override render() {
    return html`
      <gr-list-view
        .createNew=${this.createNewCapability}
        .filter=${this.filter}
        .itemsPerPage=${this.reposPerPage}
        .items=${this.repos}
        .loading=${this.loading}
        .offset=${this.offset}
        .path=${createAdminUrl({adminView: AdminChildView.REPOS})}
        @create-clicked=${() => this.handleCreateClicked()}
      >
        <gr-button
          id="createChangeButton"
          slot="createNewContainer"
          ?hidden=${!this.loggedIn}
          primary
          link
          @click=${() => this.createNewChange()}
        >
          Create Change
        </gr-button>
        <table id="list" class="genericList">
          <tbody>
            <tr class="headerRow">
              <th class="name topHeader">Repository Name</th>
              <th class="repositoryBrowser topHeader">Repository Browser</th>
              <th class="changesLink topHeader">Changes</th>
              <th class="topHeader readOnly">Read only</th>
              <th class="description topHeader">Repository Description</th>
            </tr>
            <tr
              id="loading"
              class="loadingMsg ${this.computeLoadingClass(this.loading)}"
            >
              <td>Loading...</td>
            </tr>
          </tbody>
          <tbody class=${this.computeLoadingClass(this.loading)}>
            ${this.renderRepoList()}
          </tbody>
        </table>
      </gr-list-view>
      <dialog id="createRepoModal" tabindex="-1">
        <gr-dialog
          id="createDialog"
          class="confirmDialog"
          ?disabled=${!this.newRepoName}
          confirm-label="Create"
          @confirm=${() => this.handleCreateRepo()}
          @cancel=${() => this.handleCloseCreate()}
        >
          <div class="header" slot="header">Create Repository</div>
          <div class="main" slot="main">
            <gr-create-repo-dialog
              id="createNewModal"
              @new-repo-name=${() => this.handleNewRepoName()}
            ></gr-create-repo-dialog>
          </div>
        </gr-dialog>
      </dialog>
      <dialog id="createModal" tabindex="-1">
        <gr-dialog
          confirm-label="Next"
          @confirm=${this.pickerConfirm}
          @cancel=${() => {
            assertIsDefined(this.createModal, 'createModal');
            this.createModal.close();
            this.repo = '' as RepoName;
            this.branch = '' as BranchName;
          }}
          ?disabled=${!(this.repo && this.branch)}
        >
          <div class="header" slot="header">Create change</div>
          <div class="main" slot="main">
            <gr-repo-branch-picker
              .repo=${this.repo}
              .branch=${this.branch}
              @repo-changed=${(e: BindValueChangeEvent) => {
                this.repo = e.detail.value as RepoName;
              }}
              @branch-changed=${(e: BindValueChangeEvent) => {
                this.branch = e.detail.value as BranchName;
              }}
            ></gr-repo-branch-picker>
          </div>
        </gr-dialog>
      </dialog>
      <dialog id="createChangeModal" tabindex="-1">
        <gr-dialog
          id="createChangeDialog"
          confirm-label="Create"
          ?disabled=${!this.canCreateChange}
          @confirm=${() => {
            this.handleCreateChange();
          }}
          @cancel=${() => {
            this.handleCloseCreateChange();
          }}
        >
          <div class="header" slot="header">Create Change</div>
          <div class="main" slot="main">
            <gr-create-change-dialog
              id="createNewChangeModal"
              .repoName=${this.repo}
              .branch=${this.branch}
              @can-create-change=${() => {
                this.handleCanCreateChange();
              }}
            ></gr-create-change-dialog>
          </div>
        </gr-dialog>
      </dialog>
    `;
  }

  private renderRepoList() {
    const shownRepos = this.repos.slice(0, this.reposPerPage);
    return shownRepos.map(item => this.renderRepo(item));
  }

  private renderRepo(item: ProjectInfoWithName) {
    return html`
      <tr class="table">
        <td class="name">
          <a href=${createRepoUrl({repo: item.name})}>${item.name}</a>
        </td>
        <td class="repositoryBrowser">${this.renderWebLinks(item)}</td>
        <td class="changesLink">
          <a href=${createSearchUrl({repo: item.name})}>view all</a>
        </td>
        <td class="readOnly">
          ${item.state === RepoState.READ_ONLY ? 'Y' : ''}
        </td>
        <td class="description">${item.description}</td>
      </tr>
    `;
  }

  private renderWebLinks(links: ProjectInfoWithName) {
    const webLinks = links.web_links ? links.web_links : [];
    return webLinks.map(link => this.renderWebLink(link));
  }

  private renderWebLink(info: WebLinkInfo) {
    return html`<gr-weblink imageAndText .info=${info}></gr-weblink>`;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('params')) {
      this._paramsChanged();
    }
  }

  async _paramsChanged() {
    const params = this.params;
    this.loading = true;
    this.filter = params?.filter ?? '';
    this.offset = Number(params?.offset ?? 0);

    return await this.getRepos();
  }

  /**
   * Opens the create overlay if the route has a hash 'create'.
   *
   * private but used in test
   */
  maybeOpenCreateModal(params?: AdminViewState) {
    if (params?.openCreateModal) {
      this.createModal?.showModal();
    }
  }

  // private but used in test
  async getRepos() {
    this.repos = [];

    // We save the filter before getting the repos
    // and then we check the value hasn't changed aftwards.
    const filter = this.filter;

    const repos = await this.restApiService.getRepos(
      this.filter,
      this.reposPerPage,
      this.offset
    );

    // Late response.
    if (filter !== this.filter || !repos) return;

    this.repos = repos;
    this.loading = false;

    return repos;
  }

  private async refreshReposList() {
    this.restApiService.invalidateReposCache();
    return await this.getRepos();
  }

  // private but used in test
  async handleCreateRepo() {
    await this.createNewModal?.handleCreateRepo();
    await this.refreshReposList();
  }

  // private but used in test
  handleCloseCreate() {
    this.createRepoModal?.close();
  }

  // private but used in test
  handleCreateClicked() {
    this.createRepoModal?.showModal();
    this.createNewModal?.focus();
  }

  // private but used in test
  computeLoadingClass(loading: boolean) {
    return loading ? 'loading' : '';
  }

  private handleNewRepoName() {
    if (!this.createNewModal) return;
    this.newRepoName = this.createNewModal.nameChanged;
  }

  private createNewChange() {
    assertIsDefined(this.createModal, 'createModal');
    assertIsDefined(this.createNewChangeModal, 'createNewChangeModal');
    this.createModal.showModal();
  }

  private handleCreateChange() {
    assertIsDefined(this.createNewChangeModal, 'createNewChangeModal');
    this.createNewChangeModal.handleCreateChange();
    this.handleCloseCreateChange();
  }

  private handleCloseCreateChange() {
    assertIsDefined(this.createChangeModal, 'createChangeModal');
    this.createChangeModal.close();
    this.repo = '' as RepoName;
    this.branch = '' as BranchName;
  }

  private handleCanCreateChange() {
    assertIsDefined(this.createNewChangeModal, 'createNewChangeModal');
    this.canCreateChange =
      !!this.createNewChangeModal.branch && !!this.createNewChangeModal.subject;
  }

  private pickerConfirm = () => {
    assertIsDefined(this.createModal, 'createModal');
    assertIsDefined(this.createChangeModal, 'createChangeModal');
    this.createModal.close();
    this.createChangeModal.showModal();
  };
}
