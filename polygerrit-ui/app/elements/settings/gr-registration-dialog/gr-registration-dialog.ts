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
import '@polymer/iron-input/iron-input';
import '../../../styles/gr-form-styles';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import '../../../styles/shared-styles';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-registration-dialog_html';
import {customElement, property, observe} from '@polymer/decorators';
import {ServerInfo, AccountDetailInfo} from '../../../types/common';
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';
import {EditableAccountField} from '../../../constants/constants';

export interface GrRegistrationDialog {
  $: {
    restAPI: RestApiService & Element;
    name: HTMLInputElement;
    username: HTMLInputElement;
    email: HTMLSelectElement;
  };
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-registration-dialog': GrRegistrationDialog;
  }
}

@customElement('gr-registration-dialog')
export class GrRegistrationDialog extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

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
  @property({type: String})
  settingsUrl?: string;

  @property({type: Object})
  _account: Partial<AccountDetailInfo> = {};

  @property({type: Boolean})
  _loading = true;

  @property({type: Boolean})
  _saving = false;

  @property({type: Object})
  _serverConfig?: ServerInfo;

  @property({
    computed: '_computeUsernameMutable(_serverConfig,_account.username)',
    type: Boolean,
  })
  _usernameMutable = false;

  /** @override */
  ready() {
    super.ready();
    this._ensureAttribute('role', 'dialog');
  }

  _computeUsernameMutable(config?: ServerInfo, username?: string) {
    // Polymer 2: check for undefined
    // username is not being checked for undefined as we want to avoid
    // setting it null explicitly to trigger the computation
    if (config === undefined) {
      return false;
    }

    return (
      config.auth.editable_account_fields.includes(
        EditableAccountField.USER_NAME
      ) && !username
    );
  }

  loadData() {
    this._loading = true;

    const loadAccount = this.$.restAPI.getAccount().then(account => {
      this._account = {...this._account, ...account};
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
      this.dispatchEvent(
        new CustomEvent('account-detail-update', {
          composed: true,
          bubbles: true,
        })
      );
    });
  }

  _handleSave(e: Event) {
    e.preventDefault();
    this._save().then(() => this.close());
  }

  _handleClose(e: Event) {
    e.preventDefault();
    this.close();
  }

  close() {
    this._saving = true; // disable buttons indefinitely
    this.dispatchEvent(
      new CustomEvent('close', {
        composed: true,
        bubbles: true,
      })
    );
  }

  _computeSaveDisabled(name?: string, email?: string, saving?: boolean) {
    return !name || !email || saving;
  }

  _computeUsernameClass(usernameMutable: boolean) {
    return usernameMutable ? '' : 'hide';
  }

  @observe('_loading')
  _loadingChanged() {
    this.classList.toggle('loading', this._loading);
  }
}
