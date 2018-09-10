/**
@license
Copyright (C) 2016 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../../@polymer/iron-autogrow-textarea/iron-autogrow-textarea.js';

import '../../../../@polymer/polymer/polymer-legacy.js';
import '../../shared/gr-dialog/gr-dialog.js';
import '../../../styles/shared-styles.js';

const ERR_COMMIT_NOT_FOUND =
    'Unable to find the commit hash of this change.';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      :host {
        display: block;
      }
      :host([disabled]) {
        opacity: .5;
        pointer-events: none;
      }
      label {
        cursor: pointer;
        display: block;
        width: 100%;
      }
      iron-autogrow-textarea {
        font-family: var(--monospace-font-family);
        padding: 0;
        width: 73ch; /* Add a char to account for the border. */

        --iron-autogrow-textarea {
          border: 1px solid var(--border-color);
          box-sizing: border-box;
          font-family: var(--monospace-font-family);
        }
      }
    </style>
    <gr-dialog confirm-label="Revert" on-confirm="_handleConfirmTap" on-cancel="_handleCancelTap">
      <div class="header" slot="header">Revert Merged Change</div>
      <div class="main" slot="main">
        <label for="messageInput">
          Revert Commit Message
        </label>
        <iron-autogrow-textarea id="messageInput" class="message" autocomplete="on" max-rows="15" bind-value="{{message}}"></iron-autogrow-textarea>
      </div>
    </gr-dialog>
`,

  is: 'gr-confirm-revert-dialog',

  /**
   * Fired when the confirm button is pressed.
   *
   * @event confirm
   */

  /**
   * Fired when the cancel button is pressed.
   *
   * @event cancel
   */

  properties: {
    message: String,
  },

  populateRevertMessage(message, commitHash) {
    // Figure out what the revert title should be.
    const originalTitle = message.split('\n')[0];
    const revertTitle = `Revert "${originalTitle}"`;
    if (!commitHash) {
      this.fire('show-alert', {message: ERR_COMMIT_NOT_FOUND});
      return;
    }
    const revertCommitText = `This reverts commit ${commitHash}.`;

    this.message = `${revertTitle}\n\n${revertCommitText}\n\n` +
        `Reason for revert: <INSERT REASONING HERE>\n`;
  },

  _handleConfirmTap(e) {
    e.preventDefault();
    this.fire('confirm', null, {bubbles: false});
  },

  _handleCancelTap(e) {
    e.preventDefault();
    this.fire('cancel', null, {bubbles: false});
  }
});
