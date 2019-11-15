/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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

  /**
    * @appliesMixin Gerrit.FireMixin
    */
  class GrRegistrationDialog extends Polymer.mixinBehaviors( [
    Gerrit.FireBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-registration-dialog'; }
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

    static get properties() {
      return {
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
      };
    }

    ready() {
      super.ready();
      this._ensureAttribute('role', 'dialog');
    }

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
    }

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
    }

    _handleSave(e) {
      e.preventDefault();
      this._save().then(this.close.bind(this));
    }

    _handleClose(e) {
      e.preventDefault();
      this.close();
    }

    close() {
      this._saving = true; // disable buttons indefinitely
      this.fire('close');
    }

    _computeSaveDisabled(name, email, saving) {
      return !name || !email || saving;
    }

    _computeUsernameMutable(config, username) {
      // Polymer 2: check for undefined
      if ([
        config,
        username,
      ].some(arg => arg === undefined)) {
        return undefined;
      }

      return config.auth.editable_account_fields.includes('USER_NAME') &&
          !username;
    }

    _computeUsernameClass(usernameMutable) {
      return usernameMutable ? '' : 'hide';
    }

    _loadingChanged() {
      this.classList.toggle('loading', this._loading);
    }
  }

  customElements.define(GrRegistrationDialog.is, GrRegistrationDialog);
})();
