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
    is: 'gr-create-project-dialog',

    properties: {
      params: Object,
      hasNewProjectName: {
        type: Boolean,
        notify: true,
        value: false,
      },

      /** @type {?} */
      _projectConfig: {
        type: Object,
        value: () => {
          // Set default values for dropdowns.
          return {
            create_empty_commit: true,
            permissions_only: false,
          };
        },
      },
      _projectCreated: {
        type: Boolean,
        value: false,
      },

      _query: {
        type: Function,
        value() {
          return this._getProjectSuggestions.bind(this);
        },
      },
    },

    observers: [
      '_updateProjectName(_projectConfig.name)',
    ],

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    _computeProjectUrl(projectName) {
      return this.getBaseUrl() + '/admin/projects/' +
          this.encodeURL(projectName, true);
    },

    _updateProjectName(name) {
      this.hasNewProjectName = !!name;
    },

    handleCreateProject() {
      return this.$.restAPI.createProject(this._projectConfig)
          .then(projectRegistered => {
            if (projectRegistered.status === 201) {
              this._projectCreated = true;
              page.show(this._computeProjectUrl(this._projectConfig.name));
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
