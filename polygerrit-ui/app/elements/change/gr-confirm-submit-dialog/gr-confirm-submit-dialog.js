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

import '../../core/gr-navigation/gr-navigation.js';
import '../../shared/gr-dialog/gr-dialog.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../../styles/shared-styles.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      #dialog {
        min-width: 40em;
      }
      p {
        margin-bottom: 1em;
      }
      @media screen and (max-width: 50em) {
        #dialog {
          min-width: inherit;
          width: 100%;
        }
      }
    </style>
    <gr-dialog id="dialog" confirm-label="Continue" confirm-on-enter="" on-cancel="_handleCancelTap" on-confirm="_handleConfirmTap">
      <div class="header" slot="header">
        [[action.label]]
      </div>
      <div class="main" slot="main">
        <gr-endpoint-decorator name="confirm-submit-change">
          <p>
            Ready to submit “<strong>[[change.subject]]</strong>”?
          </p>
          <gr-endpoint-param name="change" value="[[change]]"></gr-endpoint-param>
          <gr-endpoint-param name="action" value="[[action]]"></gr-endpoint-param>
        </gr-endpoint-decorator>
      </div>
    </gr-dialog>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-confirm-submit-dialog',

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
    /**
     * @type {{
     *    subject: string,
     *  }}
     */
    change: Object,

    /**
     * @type {{
     *    label: string,
     *  }}
     */
    action: Object,
  },

  resetFocus(e) {
    this.$.dialog.resetFocus();
  },

  _handleConfirmTap(e) {
    e.preventDefault();
    this.dispatchEvent(new CustomEvent('confirm', {bubbles: false}));
  },

  _handleCancelTap(e) {
    e.preventDefault();
    this.dispatchEvent(new CustomEvent('cancel', {bubbles: false}));
  }
});
