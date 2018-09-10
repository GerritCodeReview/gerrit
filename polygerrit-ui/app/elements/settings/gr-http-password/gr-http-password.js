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
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../styles/gr-form-styles.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-overlay/gr-overlay.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../../styles/shared-styles.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      .password {
        font-family: var(--monospace-font-family);
      }
      #generatedPasswordOverlay {
        padding: 2em;
        width: 50em;
      }
      #generatedPasswordDisplay {
        margin: 1em 0;
      }
      #generatedPasswordDisplay .value {
        font-family: var(--monospace-font-family);
      }
      #passwordWarning {
        font-style: italic;
        text-align: center;
      }
      .closeButton {
        bottom: 2em;
        position: absolute;
        right: 2em;
      }
    </style>
    <style include="gr-form-styles"></style>
    <div class="gr-form-styles">
      <div hidden\$="[[_passwordUrl]]">
        <section>
          <span class="title">Username</span>
          <span class="value">[[_username]]</span>
        </section>
        <gr-button id="generateButton" on-tap="_handleGenerateTap">Generate new password</gr-button>
      </div>
      <span hidden\$="[[!_passwordUrl]]">
        <a href\$="[[_passwordUrl]]" target="_blank" rel="noopener">
          Obtain password</a>
        (opens in a new tab)
      </span>
    </div>
    <gr-overlay id="generatedPasswordOverlay" on-iron-overlay-closed="_generatedPasswordOverlayClosed" with-backdrop="">
      <div class="gr-form-styles">
        <section id="generatedPasswordDisplay">
          <span class="title">New Password:</span>
          <span class="value">[[_generatedPassword]]</span>
        </section>
        <section id="passwordWarning">
          This password will not be displayed again.<br>
          If you lose it, you will need to generate a new one.
        </section>
        <gr-button link="" class="closeButton" on-tap="_closeOverlay">Close</gr-button>
      </div>
    </gr-overlay>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-http-password',

  properties: {
    _username: String,
    _generatedPassword: String,
    _passwordUrl: String,
  },

  loadData() {
    const promises = [];

    promises.push(this.$.restAPI.getAccount().then(account => {
      this._username = account.username;
    }));

    promises.push(this.$.restAPI.getConfig().then(info => {
      this._passwordUrl = info.auth.http_password_url || null;
    }));

    return Promise.all(promises);
  },

  _handleGenerateTap() {
    this._generatedPassword = 'Generating...';
    this.$.generatedPasswordOverlay.open();
    this.$.restAPI.generateAccountHttpPassword().then(newPassword => {
      this._generatedPassword = newPassword;
    });
  },

  _closeOverlay() {
    this.$.generatedPasswordOverlay.close();
  },

  _generatedPasswordOverlayClosed() {
    this._generatedPassword = '';
  }
});
