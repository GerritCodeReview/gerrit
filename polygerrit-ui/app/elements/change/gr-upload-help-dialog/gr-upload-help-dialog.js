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

  const COMMIT_COMMAND = 'git add . && git commit --amend --no-edit';
  const PUSH_COMMAND_PREFIX = 'git push origin HEAD:refs/for/';

  Polymer({
    is: 'gr-upload-help-dialog',

    /**
     * Fired when the user presses the close button.
     *
     * @event close
     */

    properties: {
      targetBranch: String,
      _commitCommand: {
        type: String,
        value: COMMIT_COMMAND,
        readOnly: true,
      },
      _pushCommand: {
        type: String,
        computed: '_computePushCommand(targetBranch)',
      },
    },

    _handleCloseTap(e) {
      e.preventDefault();
      this.fire('close', null, {bubbles: false});
    },

    _computePushCommand(targetBranch) {
      return PUSH_COMMAND_PREFIX + targetBranch;
    },
  });
})();
