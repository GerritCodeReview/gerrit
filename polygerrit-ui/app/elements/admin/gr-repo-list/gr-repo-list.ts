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
import '../../../styles/gr-table-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-list-view/gr-list-view';
import '../../shared/gr-overlay/gr-overlay';
import '../gr-create-repo-dialog/gr-create-repo-dialog';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-repo-list_html';
import {ListViewMixin} from '../../../mixins/gr-list-view-mixin/gr-list-view-mixin';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {customElement, property, observe, computed} from '@polymer/decorators';
import {AppElementAdminParams} from '../../gr-app-types';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {RepoName, ProjectInfoWithName} from '../../../types/common';
import {GrCreateRepoDialog} from '../gr-create-repo-dialog/gr-create-repo-dialog';
import {ProjectState} from '../../../constants/constants';
import {fireTitleChange} from '../../../utils/event-util';
import {appContext} from '../../../services/app-context';

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-list': GrRepoList;
  }
}

export interface GrRepoList {
  $: {
    createOverlay: GrOverlay;
    createNewModal: GrCreateRepoDialog;
  };
}

@customElement('gr-repo-list')
export class GrRepoList extends ListViewMixin(PolymerElement) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object})
  params?: AppElementAdminParams;

  @property({type: Number})
  _offset?: number;

  @property({type: String})
  readonly _path = '/admin/repos';

  @property({type: Boolean})
  _hasNewRepoName = false;

  @property({type: Boolean})
  _createNewCapability = false;

  @property({type: Array})
  _repos: ProjectInfoWithName[] = [];

  @property({type: Number})
  _reposPerPage = 25;

  @property({type: Boolean})
  _loading = true;

  @property({type: String})
  _filter = '';

  @computed('_repos')
  get _shownRepos() {
    return this.computeShownItems(this._repos);
  }

  private readonly restApiService = appContext.restApiService;

  /** @override */
  connectedCallback() {
    super.connectedCallback();
    this._getCreateRepoCapability();
    fireTitleChange(this, 'Repos');
    this._maybeOpenCreateOverlay(this.params);
  }

  @observe('params')
  _paramsChanged(params: AppElementAdminParams) {
    this._loading = true;
    this._filter = this.getFilterValue(params);
    this._offset = this.getOffsetValue(params);

    return this._getRepos(this._filter, this._reposPerPage, this._offset);
  }

  /**
   * Opens the create overlay if the route has a hash 'create'
   */
  _maybeOpenCreateOverlay(params?: AppElementAdminParams) {
    if (params?.openCreateModal) {
      this.$.createOverlay.open();
    }
  }

  _computeRepoUrl(name: string) {
    return this.getUrl(this._path + '/', name);
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
            this._createNewCapability = true;
          }
        });
    });
  }

  _getRepos(filter: string, reposPerPage: number, offset?: number) {
    this._repos = [];
    return this.restApiService
      .getRepos(filter, reposPerPage, offset)
      .then(repos => {
        // Late response.
        if (filter !== this._filter || !repos) {
          return;
        }
        this._loading = false;
      });
  }

  _refreshReposList() {
    this.restApiService.invalidateReposCache();
    return this._getRepos(this._filter, this._reposPerPage, this._offset);
  }

  _handleCreateRepo() {
    this.$.createNewModal.handleCreateRepo().then(() => {
      this._refreshReposList();
    });
  }

  _handleCloseCreate() {
    this.$.createOverlay.close();
  }

  _handleCreateClicked() {
    this.$.createOverlay.open().then(() => {
      this.$.createNewModal.focus();
    });
  }

  _readOnly(repo: ProjectInfoWithName) {
    return repo.state === ProjectState.READ_ONLY ? 'Y' : '';
  }

  _computeWeblink(repo: ProjectInfoWithName) {
    if (!repo.web_links) {
      return '';
    }
    const webLinks = repo.web_links;
    return webLinks.length ? webLinks : null;
  }
}
