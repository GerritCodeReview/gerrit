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
    is: 'gr-admin-create-project',

    properties: {
      params: Object,

      _configChanged: {
        type: Boolean,
        value: false,
      },
      _loading: {
        type: Boolean,
        value: true,
      },
      _projectConfig: Object,
    },

    observers: [
      '_handleConfigChanged(_projectConfig.*)',
    ],


    attached() {
      this._CreateProject();
    },

    _CreateProject() {
     // if (!this.project) { return Promise.resolve(); }

      const promises = [];
      /*promises.push(this._getLoggedIn().then(loggedIn => {
        this._loggedIn = loggedIn;
        if (loggedIn) {
          this.$.restAPI.getProjectAccess(this.project).then(access => {
            // If the user is not an owner, is_owner is not a property.
            this._readOnly = !access[this.project].is_owner;
          });
        }
      }));

      promises.push(this.$.restAPI.getProjectConfig(this.project).then(
          config => {
            this._projectConfig = config;
            if (!this._projectConfig.state) {
              this._projectConfig.state = STATES.active.value;
            }
            this._loading = false;
          }));*/

      if (typeof this._projectConfig.parent !== 'undefined') {
        this._projectConfig.parent = 'All-Projects';
      }

      if (typeof this._projectConfig.create_empty_commit !== 'undefined') {
        this._projectConfig.create_empty_commit = false;
      }

      if (typeof this._projectConfig.permissions_only !== 'undefined') {
        this._projectConfig.permissions_only = false;
      }

      //return Promise.all(promises);
    },

    _getLoggedIn() {
      return this.$.restAPI.getLoggedIn();
    },

    _formatProjectConfigForSave(p) {
      const configInputObj = {};
      for (const key in p) {
        if (p.hasOwnProperty(key)) {
          if (typeof p[key] === 'object') {
            configInputObj[key] = p[key].configured_value;
          } else {
            configInputObj[key] = p[key];
          }
        }
      }
      return configInputObj;
    },

    _handleCreateProject() {
      return this.$.restAPI.createProject(this._formatProjectConfigForSave(this._projectConfig));
    },
  });
})();
