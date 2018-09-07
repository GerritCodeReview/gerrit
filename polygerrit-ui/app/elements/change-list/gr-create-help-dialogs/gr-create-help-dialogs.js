/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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

  const Commands = {
    CREATE: 'git commit',
    AMEND: 'git commit --amend',
    PUSH_PREFIX: 'git push origin HEAD:refs/for/',
  };

  Polymer({
    is: 'gr-create-help-dialogs',
    properties: {
      _repo: String,
      _branch: String,
      _repoAndBranchSelected: {
        type: Boolean,
        value: false,
        computed: '_computeRepoAndBranchSelected(_repo, _branch)',
      },
      _createNewCommitCommand: {
        type: String,
        readonly: true,
        value: Commands.CREATE,
      },
      _amendExistingCommitCommand: {
        type: String,
        readonly: true,
        value: Commands.AMEND,
      },
      _pushCommand: {
        type: String,
        computed: '_computePushCommand(_branch)',
      },
    },
    open() {
      this._repo = '';
      this._branch = '';
      this.$.createOverlay.open();
    },

    _handleClose() {
      this.$.createOverlay.close();
      this.$.commandsOverlay.close();
    },

    _pickerConfirm() {
      this.$.createOverlay.close();
      this.$.commandsOverlay.open();
    },

    _computeRepoAndBranchSelected(repo, branch) {
      return !!(repo && branch);
    },

    _computePushCommand(_branch) {
      return Commands.PUSH_PREFIX + _branch;
    },
  });
})();
