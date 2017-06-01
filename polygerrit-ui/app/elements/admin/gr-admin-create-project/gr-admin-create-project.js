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

      _projectConfig: Object,
      _projectCreated: {
        type: Object,
        value: false,
      },

      _query: {
        type: Function,
        value() {
          return this._getProjectSuggestions.bind(this);
        },
      },
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.KeyboardShortcutBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    attached() {
      this._createProject();
    },

    _createProject() {
      this._projectConfig = [];
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

    _redirect(projectName) {
      return this.getBaseUrl() + '/admin/projects/' + projectName;
    },

    _handleCreateProject() {
      const config = this._formatProjectConfigForSave(this._projectConfig);
      return this.$.restAPI.createProject(config)
          .then(projectRegistered => {
            if (projectRegistered.status === 201) {
              this._projectCreated = true;
              this.$.redirect.click();
            }
          });
    },

    _getProjectSuggestions(input) {
      return this.$.restAPI.getSuggestedProjects(input)
          .then(response => {
            const projects = [];
            for (const key in response) {
              if (!response.hasOwnProperty(key)) { continue; }
              projects.push({
                name: key,
                value: response[key],
              });
            }
            return projects;
          });
    },
  });
})();
