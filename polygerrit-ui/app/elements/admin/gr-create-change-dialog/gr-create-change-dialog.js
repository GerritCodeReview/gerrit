/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function() {
  'use strict';

  const SUGGESTIONS_LIMIT = 15;
  const REF_PREFIX = 'refs/heads/';

  Polymer({
    is: 'gr-create-change-dialog',

    properties: {
      repoName: String,
      branch: String,
      /** @type {?} */
      _repoConfig: Object,
      subject: String,
      topic: String,
      baseChange: String,
      baseCommit: String,
      privateByDefault: String,
      canCreate: {
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
      if (!this.repoName) { return; }
      this.$.restAPI.getProjectConfig(this.repoName).then(config => {
        this.privateByDefault = config.private_by_default;
      });
    },

    observers: [
      '_allowCreate(branch, subject)',
    ],

    _computeBranchClass(baseChange) {
      return baseChange ? 'hideBranch' : '';
    },

    _allowCreate(branch, subject) {
      this.canCreate = !!branch && !!subject;
    },

    handleCreateChange() {
      const isPrivate = this.$.privateChangeCheckBox.checked;
      const isWip = true;
      return this.$.restAPI.createChange(this.repoName, this.branch,
          this.subject, this.topic, isPrivate, isWip, this.baseChange,
          this.baseCommit || null)
          .then(changeCreated => {
            if (!changeCreated) { return; }
            Gerrit.Nav.navigateToChange(changeCreated);
          });
    },

    _formatBooleanString(config) {
      if (config && config.configured_value === 'TRUE') {
        return true;
      } else if (config && config.configured_value === 'FALSE') {
        return false;
      } else if (config && config.configured_value === 'INHERIT') {
        if (config && config.inherited_value) {
          return true;
        } else {
          return false;
        }
      } else {
        return false;
      }
    },
  });
})();
