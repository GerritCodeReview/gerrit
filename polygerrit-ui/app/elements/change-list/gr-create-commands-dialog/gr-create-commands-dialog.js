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

  class GrCreateCommandsDialog extends Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element)) {
    static get is() { return 'gr-create-commands-dialog'; }

    static get properties() {
      return {
        branch: String,
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
          computed: '_computePushCommand(branch)',
        },
      };
    }

    open() {
      this.$.commandsOverlay.open();
    }

    _handleClose() {
      this.$.commandsOverlay.close();
    }

    _computePushCommand(branch) {
      return Commands.PUSH_PREFIX + branch;
    }
  }

  customElements.define(GrCreateCommandsDialog.is, GrCreateCommandsDialog);
})();
