/**
@license
Copyright (C) 2017 The Android Open Source Project

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
import '../../../styles/shared-styles.js';
import '../../shared/gr-autocomplete/gr-autocomplete.js';
import '../../shared/gr-dialog/gr-dialog.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';

const SUGGESTIONS_LIMIT = 15;

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      :host {
        display: block;
        width: 30em;
      }
      :host([disabled]) {
        opacity: .5;
        pointer-events: none;
      }
      label {
        cursor: pointer;
      }
      iron-autogrow-textarea {
        padding: 0;
      }
      .main {
        display: flex;
        flex-direction: column;
        width: 100%;
      }
      .main label,
      .main input[type="text"] {
        display: block;
        font: inherit;
        width: 100%;
      }
      .main .message {
        border: groove;
        width: 100%;
      }
      .warning {
        color: var(--error-text-color);
      }
    </style>
    <gr-dialog confirm-label="Move Change" on-confirm="_handleConfirmTap" on-cancel="_handleCancelTap">
      <div class="header" slot="header">Move Change to Another Branch</div>
      <div class="main" slot="main">
        <p class="warning">
          Warning: moving a change will not change its parents.
        </p>
        <label for="branchInput">
          Move change to branch
        </label>
        <gr-autocomplete id="branchInput" text="{{branch}}" query="[[_query]]" placeholder="Destination branch">
        </gr-autocomplete>
        <label for="messageInput">
          Move Change Message
        </label>
        <iron-autogrow-textarea id="messageInput" class="message" autocomplete="on" rows="4" max-rows="15" bind-value="{{message}}"></iron-autogrow-textarea>
      </div>
    </gr-dialog>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-confirm-move-dialog',

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
    branch: String,
    message: String,
    project: String,
    _query: {
      type: Function,
      value() {
        return this._getProjectBranchesSuggestions.bind(this);
      },
    },
  },

  _handleConfirmTap(e) {
    e.preventDefault();
    this.fire('confirm', null, {bubbles: false});
  },

  _handleCancelTap(e) {
    e.preventDefault();
    this.fire('cancel', null, {bubbles: false});
  },

  _getProjectBranchesSuggestions(input) {
    if (input.startsWith('refs/heads/')) {
      input = input.substring('refs/heads/'.length);
    }
    return this.$.restAPI.getRepoBranches(
        input, this.project, SUGGESTIONS_LIMIT).then(response => {
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
  }
});
