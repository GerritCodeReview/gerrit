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

      _query: {
        type: Function,
        value() {
          return this._getProjectSuggestions.bind(this);
        },
      },
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    attached() {
      this._CreateProject();
    },

    _CreateProject() {
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

    _handleCreateProject() {
      return this.$.restAPI.createProject(
          this._formatProjectConfigForSave(this._projectConfig));
    },

    _handleInputCommit(e) {
      this._preventDefaultAndNavigateToInputVal(e);
    },

    _preventDefaultAndNavigateToInputVal(e) {
      e.preventDefault();
      const target = Polymer.dom(e).rootTarget;
      // If the target is the #searchInput or has a sub-input component, that
      // is what holds the focus as opposed to the target from the DOM event.
      if (target.$.input) {
        target.$.input.blur();
      } else {
        target.blur();
      }
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
