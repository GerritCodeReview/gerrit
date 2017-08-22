// Copyright (C) 2017 The Android Open Source Project
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

  const GC_MESSAGE = 'Garbage collection completed successfully.';

  Polymer({
    is: 'gr-project-commands',

    properties: {
      params: Object,
      project: String,
      _loading: {
        type: Boolean,
        value: true,
      },
      /** @type {?} */
      _projectConfig: Object,
      _canCreate: Boolean,
    },

    attached() {
      this._loadProject();

      this.fire('title-change', {title: 'Project Commands'});
    },

    _loadProject() {
      if (!this.project) { return Promise.resolve(); }

      return this.$.restAPI.getProjectConfig(this.project).then(
          config => {
            this._projectConfig = config;
            this._loading = false;
          });
    },

    _computeLoadingClass(loading) {
      return loading ? 'loading' : '';
    },

    _isLoading() {
      return this._loading || this._loading === undefined;
    },

    _handleRunningGC() {
      return this.$.restAPI.runProjectGC(this.project).then(response => {
        if (response.status === 200) {
          this.dispatchEvent(new CustomEvent('show-alert',
              {detail: {message: GC_MESSAGE}, bubbles: true}));
        }
      });
    },

    _createNewChange() {
      this.$.createChangeOverlay.open();
    },

    _handleCreateChange() {
      this.$.createNewChangeModal.handleCreateChange();
      this._handleCloseCreateChange();
    },

    _handleCloseCreateChange() {
      this.$.createChangeOverlay.close();
    },
  });
})();
