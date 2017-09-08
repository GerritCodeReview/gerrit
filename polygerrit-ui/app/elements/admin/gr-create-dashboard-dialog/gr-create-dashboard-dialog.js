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

  Polymer({
    is: 'gr-create-dashboard-dialog',

    properties: {
      projectName: String,
      /** @type {?} */
      _serverConfig: Object,
      commitMessage: String,
      idName: String,
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    attached() {
      this.$.restAPI.getConfig().then(config => {
        this._serverConfig = config;
      });
    },

    handleCreateDashboard() {
      const IS_QUERY = this.idName;
      const IS_COMMIT_MESSAGE = this.commitMessage;
      return this.$.restAPI.createDashboard(this.projectName, IS_QUERY,
          IS_COMMIT_MESSAGE)
          .then(dashboardCreated => {
            if (!dashboardCreated) {
              return;
            }
            this.dispatchEvent(new CustomEvent('show-alert',
                {detail: {message: 'Create'}, bubbles: true}));
          });
    },
  });
})();
