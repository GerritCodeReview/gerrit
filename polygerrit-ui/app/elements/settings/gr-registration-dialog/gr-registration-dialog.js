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
import '../../core/gr-navigation/gr-navigation.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../../styles/shared-styles.js';

Polymer({
  _template: Polymer.html`
    <style include="gr-form-styles"></style>
    <style include="shared-styles">
      :host {
        display: block;
      }
      main {
        max-width: 46em;
      }
      :host(.loading) main {
        display: none;
      }
      .loadingMessage {
        display: none;
        font-style: italic;
      }
      :host(.loading) .loadingMessage {
        display: block;
      }
      hr {
        margin-top: 1em;
        margin-bottom: 1em;
      }
      header {
        border-bottom: 1px solid var(--border-color);
        font-family: var(--font-family-bold);
        margin-bottom: 1em;
      }
      .container {
        padding: .5em 1.5em;
      }
      footer {
        display: flex;
        justify-content: flex-end;
      }
      footer gr-button {
        margin-left: 1em;
      }
      input {
        width: 20em;
      }
      section.hide {
        display: none;
      }
    </style>
    <div class="container gr-form-styles">
      <header>Please confirm your contact information</header>
      <div class="loadingMessage">Loading...</div>
      <main>
        <p>
          The following contact information was automatically obtained when you
          signed in to the site. This information is used to display who you are
          to others, and to send updates to code reviews you have either started
          or subscribed to.
        </p>
        <hr>
        <section>
          <div class="title">Full Name</div>
          <input is="iron-input" id="name" bind-value="{{_account.name}}" disabled="[[_saving]]">
        </section>
        <section class\$="[[_computeUsernameClass(_usernameMutable)]]">
          <div class="title">Username</div>
          <input is="iron-input" id="username" bind-value="{{_account.username}}" disabled="[[_saving]]">
        </section>
        <section>
          <div class="title">Preferred Email</div>
          <select id="email" disabled="[[_saving]]">
            <option value="[[_account.email]]">[[_account.email]]</option>
            <template is="dom-repeat" items="[[_account.secondary_emails]]">
              <option value="[[item]]">[[item]]</option>
            </template>
          </select>
        </section>
        <hr>
        <p>
          More configuration options for Gerrit may be found in the
          <a on-tap="close" href\$="[[settingsUrl]]">settings</a>.
        </p>
      </main>
      <footer>
        <gr-button id="closeButton" link="" disabled="[[_saving]]" on-tap="_handleClose">Close</gr-button>
        <gr-button id="saveButton" primary="" link="" disabled="[[_computeSaveDisabled(_account.name, _account.email, _saving)]]" on-tap="_handleSave">Save</gr-button>
      </footer>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-registration-dialog',

  /**
   * Fired when account details are changed.
   *
   * @event account-detail-update
   */

  /**
   * Fired when the close button is pressed.
   *
   * @event close
   */

  properties: {
    settingsUrl: String,
    /** @type {?} */
    _account: {
      type: Object,
      value: () => {
        // Prepopulate possibly undefined fields with values to trigger
        // computed bindings.
        return {email: null, name: null, username: null};
      },
    },
    _usernameMutable: {
      type: Boolean,
      computed: '_computeUsernameMutable(_serverConfig, _account.username)',
    },
    _loading: {
      type: Boolean,
      value: true,
      observer: '_loadingChanged',
    },
    _saving: {
      type: Boolean,
      value: false,
    },
    _serverConfig: Object,
  },

  hostAttributes: {
    role: 'dialog',
  },

  loadData() {
    this._loading = true;

    const loadAccount = this.$.restAPI.getAccount().then(account => {
      // Using Object.assign here allows preservation of the default values
      // supplied in the value generating function of this._account, unless
      // they are overridden by properties in the account from the response.
      this._account = Object.assign({}, this._account, account);
    });

    const loadConfig = this.$.restAPI.getConfig().then(config => {
      this._serverConfig = config;
    });

    return Promise.all([loadAccount, loadConfig]).then(() => {
      this._loading = false;
    });
  },

  _save() {
    this._saving = true;
    const promises = [
      this.$.restAPI.setAccountName(this.$.name.value),
      this.$.restAPI.setPreferredAccountEmail(this.$.email.value || ''),
    ];

    if (this._usernameMutable) {
      promises.push(this.$.restAPI.setAccountUsername(this.$.username.value));
    }

    return Promise.all(promises).then(() => {
      this._saving = false;
      this.fire('account-detail-update');
    });
  },

  _handleSave(e) {
    e.preventDefault();
    this._save().then(this.close.bind(this));
  },

  _handleClose(e) {
    e.preventDefault();
    this.close();
  },

  close() {
    this._saving = true; // disable buttons indefinitely
    this.fire('close');
  },

  _computeSaveDisabled(name, email, saving) {
    return !name || !email || saving;
  },

  _computeUsernameMutable(config, username) {
    return config.auth.editable_account_fields.includes('USER_NAME') &&
        !username;
  },

  _computeUsernameClass(usernameMutable) {
    return usernameMutable ? '' : 'hide';
  },

  _loadingChanged() {
    this.classList.toggle('loading', this._loading);
  }
});
