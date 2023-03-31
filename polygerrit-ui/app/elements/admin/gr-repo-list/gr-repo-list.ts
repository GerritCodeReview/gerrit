/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-list-view/gr-list-view';
import '../../shared/gr-overlay/gr-overlay';
import '../gr-create-repo-dialog/gr-create-repo-dialog';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {
  RepoName,
  ProjectInfoWithName,
  WebLinkInfo,
} from '../../../types/common';
import {GrCreateRepoDialog} from '../gr-create-repo-dialog/gr-create-repo-dialog';
import {ProjectState, SHOWN_ITEMS_COUNT} from '../../../constants/constants';
import {fireTitleChange} from '../../../utils/event-util';
import {getAppContext} from '../../../services/app-context';
import {encodeURL, getBaseUrl} from '../../../utils/url-util';
import {tableStyles} from '../../../styles/gr-table-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, css, html} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {AdminViewState} from '../../../models/views/admin';
import {createSearchUrl} from '../../../models/views/search';

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-list': GrRepoList;
  }
}

@customElement('gr-repo-list')
export class GrRepoList extends LitElement {
  readonly path = '/admin/repos';

  @query('#createOverlay') private createOverlay?: GrOverlay;

  @query('#createNewModal') private createNewModal?: GrCreateRepoDialog;

  @property({type: Object})
  params?: AdminViewState;

  // private but used in test
  @state() offset = 0;

  @state() private newRepoName = false;

  @state() private createNewCapability = false;

  // private but used in test
  @state() repos: ProjectInfoWithName[] = [];

  // private but used in test
  @state() reposPerPage = 25;

  // private but used in test
  @state() loading = true;

  // private but used in test
  @state() filter = '';

  private readonly restApiService = getAppContext().restApiService;

  override async connectedCallback() {
    super.connectedCallback();
    await this.getCreateRepoCapability();
    fireTitleChange(this, 'Repos');
    this.maybeOpenCreateOverlay(this.params);
  }

  static override get styles() {
    return [
      tableStyles,
      sharedStyles,
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
        .path=${this.path}
        @create-clicked=${() => this.handleCreateClicked()}
      >
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
      <gr-overlay id="createOverlay" with-backdrop>
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
      </gr-overlay>
    `;
  }

  private renderRepoList() {
    const shownRepos = this.repos.slice(0, SHOWN_ITEMS_COUNT);
    return shownRepos.map(item => this.renderRepo(item));
  }

  private renderRepo(item: ProjectInfoWithName) {
    return html`
      <tr class="table">
        <td class="name">
          <a href=${this.computeRepoUrl(item.name)}>${item.name}</a>
        </td>
        <td class="repositoryBrowser">${this.renderWebLinks(item)}</td>
        <td class="changesLink">
          <a href=${this.computeChangesLink(item.name)}>view all</a>
        </td>
        <td class="readOnly">
          ${item.state === ProjectState.READ_ONLY ? 'Y' : ''}
        </td>
        <td class="description">${item.description}</td>
      </tr>
    `;
  }

  private renderWebLinks(links: ProjectInfoWithName) {
    const webLinks = links.web_links ? links.web_links : [];
    return webLinks.map(link => this.renderWebLink(link));
  }

  private renderWebLink(link: WebLinkInfo) {
    return html`
      <a href=${link.url} class="webLink" rel="noopener" target="_blank">
        ${link.name}
      </a>
    `;
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
  maybeOpenCreateOverlay(params?: AdminViewState) {
    if (params?.openCreateModal) {
      this.createOverlay?.open();
    }
  }

  private computeRepoUrl(name: string) {
    return `${getBaseUrl()}${this.path}/${encodeURL(name, true)}`;
  }

  private computeChangesLink(name: string) {
    return createSearchUrl({project: name as RepoName});
  }

  private async getCreateRepoCapability() {
    const account = await this.restApiService.getAccount();

    if (!account) return;

    const accountCapabilities =
      await this.restApiService.getAccountCapabilities(['createProject']);
    if (accountCapabilities?.createProject) {
      this.createNewCapability = true;
    }

    return account;
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
    this.createOverlay?.close();
  }

  // private but used in test
  handleCreateClicked() {
    this.createOverlay?.open().then(() => {
      this.createNewModal?.focus();
    });
  }

  // private but used in test
  computeLoadingClass(loading: boolean) {
    return loading ? 'loading' : '';
  }

  private handleNewRepoName() {
    if (!this.createNewModal) return;
    this.newRepoName = this.createNewModal.nameChanged;
  }
}
