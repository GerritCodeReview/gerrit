/**
@license
Copyright (C) 2018 The Android Open Source Project

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
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../shared/gr-dialog/gr-dialog.js';
import '../../shared/gr-shell-command/gr-shell-command.js';
import '../../../styles/shared-styles.js';

const COMMIT_COMMAND = 'git add . && git commit --amend --no-edit';
const PUSH_COMMAND = 'git push origin HEAD:refs/for/master';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      :host {
        background-color: var(--dialog-background-color);
        display: block;
      }
      .main {
        width: 100%;
      }
      ol {
        margin-left: 1em;
        list-style: decimal;
      }
      p {
        margin-bottom: .75em;
      }
    </style>
    <gr-dialog confirm-label="Done" cancel-label="" on-confirm="_handleCloseTap">
      <div class="header" slot="header">How to update this change:</div>
      <div class="main" slot="main">
        <ol>
          <li>
            <p>
              Checkout this change locally and make your desired modifications to
              the files.
            </p>
          </li>
          <li>
            <p>
              Update the local commit with your modifications using the following
              command.
            </p>
            <gr-shell-command command="[[_commitCommand]]"></gr-shell-command>
            <p>
              Leave the "Change-Id:" line of the commit message as is.
            </p>
          </li>
          <li>
            <p>Push the updated commit to Gerrit.</p>
            <gr-shell-command command="[[_pushCommand]]"></gr-shell-command>
          </li>
          <li>
            <p>Refresh this page to view the the update.</p>
          </li>
        </ol>
      </div>
    </gr-dialog>
`,

  is: 'gr-upload-help-dialog',

  /**
   * Fired when the user presses the close button.
   *
   * @event close
   */

  properties: {
    _commitCommand: {
      type: String,
      value: COMMIT_COMMAND,
      readOnly: true,
    },
    _pushCommand: {
      type: String,
      value: PUSH_COMMAND,
      readOnly: true,
    },
  },

  _handleCloseTap(e) {
    e.preventDefault();
    this.fire('close', null, {bubbles: false});
  }
});
