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
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-list-view/gr-list-view';
import '../../shared/gr-overlay/gr-overlay';
import '../gr-create-repo-dialog/gr-create-repo-dialog';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {AppElementAdminParams} from '../../gr-app-types';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {RepoName, ProjectInfoWithName} from '../../../types/common';
import {GrCreateRepoDialog} from '../gr-create-repo-dialog/gr-create-repo-dialog';
import {ProjectState, SHOWN_ITEMS_COUNT} from '../../../constants/constants';
import {fireTitleChange} from '../../../utils/event-util';
import {appContext} from '../../../services/app-context';
import {encodeURL, getBaseUrl} from '../../../utils/url-util';
import {tableStyles} from '../../../styles/gr-table-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, css, html} from 'lit';
import {customElement, property, query, state} from 'lit/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-list': GrRepoList;
  }
}

@customElement('gr-repo-list')
export class GrRepoList extends LitElement {
  @query('#createOverlay')
  createOverlay?: GrOverlay;

  @query('#createNewModal')
  createNewModal?: GrCreateRepoDialog;

  @property({type: Object})
  params?: AppElementAdminParams;

  @state() offset = 0;

  @state() readonly path = '/admin/repos';

  @state() newRepoName = false;

  @state() createNewCapability = false;

  @state() repos: ProjectInfoWithName[] = [];

  @state() reposPerPage = 25;

  @state() loading = true;

  @state() filter = '';

  get shownRepos() {
    return this.repos.slice(0, SHOWN_ITEMS_COUNT);
  }

  private readonly restApiService = appContext.restApiService;

  override connectedCallback() {
    super.connectedCallback();
    this._getCreateRepoCapability();
    fireTitleChange(this, 'Repos');
    this._maybeOpenCreateOverlay(this.params);
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
        @create-clicked=${this._handleCreateClicked}
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
          <tbody class="${this.computeLoadingClass(this.loading)}">
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
          @confirm=${this._handleCreateRepo}
          @cancel=${this._handleCloseCreate}
        >
          <div class="header" slot="header">Create Repository</div>
          <div class="main" slot="main">
            <gr-create-repo-dialog
              id="createNewModal"
              @new-repo-name=${this.handleNewRepoName}
            ></gr-create-repo-dialog>
          </div>
        </gr-dialog>
      </gr-overlay>
    `;
  }

  private renderRepoList() {
    return html`
      ${this.shownRepos.map(
        item => html`
          <tr class="table">
            <td class="name">
              <a href="${this._computeRepoUrl(item.name)}">${item.name}</a>
            </td>
            <td class="repositoryBrowser">${this.renderWebLinks(item)}</td>
            <td class="changesLink">
              <a href="${this._computeChangesLink(item.name)}">view all</a>
            </td>
            <td class="readOnly">${this._readOnly(item)}</td>
            <td class="description">${item.description}</td>
          </tr>
        `
      )}
    `;
  }

  private renderWebLinks(links: ProjectInfoWithName) {
    return html`
      ${this._computeWeblink(links).map(
        link => html`
          <a href="${link.url}" class="webLink" rel="noopener" target="_blank">
            ${link.name}
          </a>
        `
      )}
    `;
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('params')) {
      this._paramsChanged(this.params);
    }
  }

  _paramsChanged(params?: AppElementAdminParams) {
    this.loading = true;
    this.filter = params?.filter ?? '';
    this.offset = Number(params?.offset ?? 0);

    return this._getRepos(this.filter, this.reposPerPage, this.offset);
  }

  /**
   * Opens the create overlay if the route has a hash 'create'
   */
  _maybeOpenCreateOverlay(params?: AppElementAdminParams) {
    if (params?.openCreateModal) {
      this.createOverlay?.open();
    }
  }

  _computeRepoUrl(name: string) {
    return `${getBaseUrl()}${this.path}/${encodeURL(name, true)}`;
  }

  _computeChangesLink(name: string) {
    return GerritNav.getUrlForProjectChanges(name as RepoName);
  }

  _getCreateRepoCapability() {
    return this.restApiService.getAccount().then(account => {
      if (!account) {
        return;
      }
      return this.restApiService
        .getAccountCapabilities(['createProject'])
        .then(capabilities => {
          if (capabilities?.createProject) {
            this.createNewCapability = true;
          }
        });
    });
  }

  _getRepos(filter: string, reposPerPage: number, offset?: number) {
    this.repos = [];
    return this.restApiService
      .getRepos(filter, reposPerPage, offset)
      .then(repos => {
        // Late response.
        if (filter !== this.filter || !repos) {
          return;
        }
        this.repos = repos.filter(repo =>
          repo.name.toLowerCase().includes(filter.toLowerCase())
        );
        this.loading = false;
      });
  }

  _refreshReposList() {
    this.restApiService.invalidateReposCache();
    return this._getRepos(this.filter, this.reposPerPage, this.offset);
  }

  async _handleCreateRepo() {
    await this.createNewModal?.handleCreateRepo();
    this._refreshReposList();
  }

  _handleCloseCreate() {
    this.createOverlay?.close();
  }

  _handleCreateClicked() {
    this.createOverlay?.open().then(() => {
      this.createNewModal?.focus();
    });
  }

  _readOnly(repo: ProjectInfoWithName) {
    return repo.state === ProjectState.READ_ONLY ? 'Y' : '';
  }

  _computeWeblink(repo: ProjectInfoWithName) {
    if (!repo.web_links) {
      return [];
    }
    const webLinks = repo.web_links;
    return webLinks.length ? webLinks : [];
  }

  computeLoadingClass(loading: boolean) {
    return loading ? 'loading' : '';
  }

  private handleNewRepoName() {
    if (!this.createNewModal) return;
    this.newRepoName = this.createNewModal.nameChanged;
  }
}
