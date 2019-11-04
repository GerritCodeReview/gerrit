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

  Polymer({
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

    _computeChangesLink(name) {
      return Gerrit.Nav.getUrlForProjectChanges(name);
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

    _refreshReposList() {
      this.$.restAPI.invalidateReposCache();
      return this._getRepos(this._filter, this._reposPerPage,
          this._offset);
    },

    _handleCreateRepo() {
      this.$.createNewModal.handleCreateRepo().then(() => {
        this._refreshReposList();
      });
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
    },
  });
})();
