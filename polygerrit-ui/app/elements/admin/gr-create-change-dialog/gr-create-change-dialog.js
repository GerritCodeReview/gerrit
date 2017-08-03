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

  const SUGGESTIONS_LIMIT = 15;

  Polymer({
    is: 'gr-create-change-dialog',

    properties: {
      projectName: String,
      _createChangeConfigs: {
        type: Object,
        value: [],
      },
      _query: {
        type: Function,
        value() {
          return this._getProjectBranchesSuggestions.bind(this);
        },
      },
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    observers: [
      '_updateItemName(_itemName)',
    ],

    _updateItemName(name) {
      this.hasNewItemName = !!name;
    },

    _computeItemUrl(changeNum) {
      return this.getBaseUrl() + '/c/' + changeNum;
    },

    handleCreateChange() {
      if (!this._createChangeConfigs.project) {
        this._createChangeConfigs.project = this.projectName;
      }
      if (!this._createChangeConfigs.subject) {
        this._createChangeConfigs.subject = 'Insert the description of the change.';
      }
      return this.$.restAPI.createChange(this._createChangeConfigs)
          .then(changeCreated => {
            if (changeCreated.status === 200) {
              page.show(this._computeItemUrl(changeCreated._number));
            }
          });
    },

    _getProjectBranchesSuggestions(input) {
      if (input.startsWith('refs/heads/')) {
        input = input.substring('refs/heads/'.length);
      }
      return this.$.restAPI.getProjectBranches(
          input, this.projectName, SUGGESTIONS_LIMIT).then(response => {
            const branches = [];
            let branch;
            for (const key in response) {
              if (!response.hasOwnProperty(key)) { continue; }
              if (response[key].ref.startsWith('refs/heads/')) {
                branch = response[key].ref.substring('refs/heads/'.length);
              } else {
                branch = response[key].ref;
              }
              branches.push({
                name: branch,
              });
            }
            return branches;
          });
    },
  });
})();
