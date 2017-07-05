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

    /**
     * Fired when a a project is created.
     *
     * @event create
     */

    /**
     * Fired when the user presses the cancel button.
     *
     * @event cancel
     */


    properties: {
      params: Object,

      _projectConfig: {
        type: Object,
        value: () => { return {}; },
      },
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
      Gerrit.URLEncodingBehavior,
    ],

    _computeProjectUrl(projectName) {
      return this.getBaseUrl() + '/admin/projects/' +
          this.encodeURL(projectName, true);
    },

    _handleCreateProject() {
      return this.$.restAPI.createProject(this._projectConfig)
          .then(projectRegistered => {
            if (projectRegistered.status === 201) {
              this._projectCreated = true;
              this.fire('create', {
                url: this._computeProjectUrl(this._projectConfig.name)},
                  {bubbles: false});
            }
          });
    },

    _handleCancel() {
      this.fire('cancel', {bubbles: false});
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
