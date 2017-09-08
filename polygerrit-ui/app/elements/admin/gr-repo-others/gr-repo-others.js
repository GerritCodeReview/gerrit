// Copyright (C) 2018 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function() {
  'use strict';

  Polymer({
    is: 'gr-repo-others',

    properties: {
      params: Object,
      repo: String,
      _loading: {
        type: Boolean,
        value: true,
      },
      /** @type {?} */
      _repoConfig: Object,
      _canCreate: Boolean,
      _canDelete: Boolean,
    },

    attached() {
      this._loadRepo();

      this.fire('title-change', {title: 'Other Repo Configs'});
    },

    _loadRepo() {
      if (!this.repo) { return Promise.resolve(); }

      return this.$.restAPI.getProjectConfig(this.repo).then(
          config => {
            this._repoConfig = config;
            this._loading = false;
          });
    },

    _computeLoadingClass(loading) {
      return loading ? 'loading' : '';
    },

    _isLoading() {
      return this._loading || this._loading === undefined;
    },

    _createNewDashboard() {
      this.$.createDashboardOverlay.open();
    },

    _handleCreateDashboard() {
      this.$.createNewDashboardModal.handleCreateDashboard();
      this._handleCloseCreateDashboard();
    },

    _handleCloseCreateDashboard() {
      this.$.createDashboardOverlay.close();
    },

    _deleteNewDashboard() {
      this.$.deleteDashboardOverlay.open();
    },

    _handleDeleteDashboard() {
      this.$.deleteNewDashboardModal.handleDeleteDashboard();
      this._handleCloseDeleteDashboard();
    },

    _handleCloseDeleteDashboard() {
      this.$.deleteDashboardOverlay.close();
    },
  });
})();
