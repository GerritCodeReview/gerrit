/**
@license
Copyright (C) 2017 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../behaviors/gr-list-view-behavior/gr-list-view-behavior.js';
import '../../../../@polymer/iron-input/iron-input.js';
import '../../../styles/gr-table-styles.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-dialog/gr-dialog.js';
import '../../shared/gr-list-view/gr-list-view.js';
import '../../shared/gr-overlay/gr-overlay.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../gr-create-repo-dialog/gr-create-repo-dialog.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles"></style>
    <style include="gr-table-styles"></style>
    <gr-list-view create-new="[[_createNewCapability]]" filter="[[_filter]]" items-per-page="[[_reposPerPage]]" items="[[_repos]]" loading="[[_loading]]" offset="[[_offset]]" on-create-clicked="_handleCreateClicked" path="[[_path]]">
      <table id="list" class="genericList">
        <tbody><tr class="headerRow">
          <th class="name topHeader">Repository Name</th>
          <th class="description topHeader">Repository Description</th>
          <th class="repositoryBrowser topHeader">Repository Browser</th>
          <th class="readOnly topHeader">Read only</th>
        </tr>
        <tr id="loading" class\$="loadingMsg [[computeLoadingClass(_loading)]]">
          <td>Loading...</td>
        </tr>
        </tbody><tbody class\$="[[computeLoadingClass(_loading)]]">
          <template is="dom-repeat" items="[[_shownRepos]]">
            <tr class="table">
              <td class="name">
                <a href\$="[[_computeRepoUrl(item.name)]]">[[item.name]]</a>
              </td>
              <td class="description">[[item.description]]</td>
              <td class="repositoryBrowser">
                <template is="dom-repeat" items="[[_computeWeblink(item)]]" as="link">
                  <a href\$="[[link.url]]" class="webLink" rel="noopener" target="_blank">
                    ([[link.name]])
                  </a>
                </template>
              </td>
              <td class="readOnly">[[_readOnly(item)]]</td>
            </tr>
          </template>
        </tbody>
      </table>
    </gr-list-view>
    <gr-overlay id="createOverlay" with-backdrop="">
      <gr-dialog id="createDialog" class="confirmDialog" disabled="[[!_hasNewRepoName]]" confirm-label="Create" on-confirm="_handleCreateRepo" on-cancel="_handleCloseCreate">
        <div class="header" slot="header">
          Create Repository
        </div>
        <div class="main" slot="main">
          <gr-create-repo-dialog has-new-repo-name="{{_hasNewRepoName}}" params="[[params]]" id="createNewModal"></gr-create-repo-dialog>
        </div>
      </gr-dialog>
    </gr-overlay>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-repo-list',

  properties: {
    /**
     * URL params passed from the router.
     */
    params: {
      type: Object,
      observer: '_paramsChanged',
    },

    /**
     * Offset of currently visible query results.
     */
    _offset: Number,
    _path: {
      type: String,
      readOnly: true,
      value: '/admin/repos',
    },
    _hasNewRepoName: Boolean,
    _createNewCapability: {
      type: Boolean,
      value: false,
    },
    _repos: Array,

    /**
     * Because  we request one more than the projectsPerPage, _shownProjects
     * maybe one less than _projects.
     * */
    _shownRepos: {
      type: Array,
      computed: 'computeShownItems(_repos)',
    },

    _reposPerPage: {
      type: Number,
      value: 25,
    },

    _loading: {
      type: Boolean,
      value: true,
    },
    _filter: {
      type: String,
      value: '',
    },
  },

  behaviors: [
    Gerrit.ListViewBehavior,
  ],

  attached() {
    this._getCreateRepoCapability();
    this.fire('title-change', {title: 'Repos'});
    this._maybeOpenCreateOverlay(this.params);
  },

  _paramsChanged(params) {
    this._loading = true;
    this._filter = this.getFilterValue(params);
    this._offset = this.getOffsetValue(params);

    return this._getRepos(this._filter, this._reposPerPage,
        this._offset);
  },

  /**
   * Opens the create overlay if the route has a hash 'create'
   * @param {!Object} params
   */
  _maybeOpenCreateOverlay(params) {
    if (params && params.openCreateModal) {
      this.$.createOverlay.open();
    }
  },

  _computeRepoUrl(name) {
    return this.getUrl(this._path + '/', name);
  },

  _getCreateRepoCapability() {
    return this.$.restAPI.getAccount().then(account => {
      if (!account) { return; }
      return this.$.restAPI.getAccountCapabilities(['createProject'])
          .then(capabilities => {
            if (capabilities.createProject) {
              this._createNewCapability = true;
            }
          });
    });
  },

  _getRepos(filter, reposPerPage, offset) {
    this._repos = [];
    return this.$.restAPI.getRepos(filter, reposPerPage, offset)
        .then(repos => {
          // Late response.
          if (filter !== this._filter || !repos) { return; }
          this._repos = repos;
          this._loading = false;
        });
  },

  _handleCreateRepo() {
    this.$.createNewModal.handleCreateRepo();
  },

  _handleCloseCreate() {
    this.$.createOverlay.close();
  },

  _handleCreateClicked() {
    this.$.createOverlay.open();
  },

  _readOnly(item) {
    return item.state === 'READ_ONLY' ? 'Y' : '';
  },

  _computeWeblink(repo) {
    if (!repo.web_links) { return ''; }
    const webLinks = repo.web_links;
    return webLinks.length ? webLinks : null;
  }
});
