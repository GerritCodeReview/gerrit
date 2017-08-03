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
  const REF_PREFIX = 'refs/heads/';

  Polymer({
    is: 'gr-create-change-dialog',

    properties: {
      projectName: String,
      branch: String,
      /** @type {?} */
      _serverConfig: Object,
      subject: String,
      topic: String,
      is_private: {
        type: Boolean,
        value: false,
      },
      work_in_progress: {
        type: Boolean,
        value: false,
      },
      _query: {
        type: Function,
        value() {
          return this._getProjectBranchesSuggestions.bind(this);
        },
      },
      checkedPrivate: {
        type: Boolean,
        value: true,
      },
      hasNewItemName: {
        type: Boolean,
        notify: true,
        value: false,
      },
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

    observers: [
      '_updateItemName(branch, subject)',
    ],

    _updateItemName(branch, subject) {
      this.hasNewItemName = !!branch && !!subject;
    },

    handleCreateChange() {
      if (this.$.privateChangeCheckBox.checked) {
        this.is_private = true;
      }
      if (this.$.wipChangeCheckBox.checked) {
        this.work_in_progress = true;
      }
      return this.$.restAPI.createChange(this.projectName, this.branch,
          this.subject, this.topic, this.is_private, this.work_in_progress)
          .then(changeCreated => {
            if (!changeCreated) {
              return '';
            }
            Gerrit.Nav.navigateToChange(changeCreated);
          });
    },

    _getProjectBranchesSuggestions(input) {
      if (input.startsWith(REF_PREFIX)) {
        input = input.substring(REF_PREFIX.length);
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
