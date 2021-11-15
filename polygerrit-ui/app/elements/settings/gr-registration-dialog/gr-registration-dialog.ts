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
import '../../../styles/shared-styles';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-registration-dialog_html';
import {customElement, property, observe} from '@polymer/decorators';
import {ServerInfo, AccountDetailInfo} from '../../../types/common';
import {EditableAccountField} from '../../../constants/constants';
import {getAppContext} from '../../../services/app-context';
import {fireEvent} from '../../../utils/event-util';

export interface GrRegistrationDialog {
  $: {
    name: HTMLInputElement;
    username: HTMLInputElement;
    displayName: HTMLInputElement;
  };
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-registration-dialog': GrRegistrationDialog;
  }
}

@customElement('gr-registration-dialog')
export class GrRegistrationDialog extends PolymerElement {
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
    computed: '_computeUsernameMutable(_account.username)',
    type: Boolean,
  })
  _usernameMutable = false;

  @property({type: Boolean})
  _hasUsernameChange?: boolean;

  @property({type: String, observer: '_usernameChanged'})
  _username?: string;

  @property({
    type: Boolean,
    notify: true,
    computed: '_computeNameMutable(_serverConfig)',
  })
  _nameMutable?: boolean;

  @property({type: Boolean})
  _hasNameChange?: boolean;

  @property({type: Boolean})
  _hasDisplayNameChange?: boolean;

  private readonly restApiService = getAppContext().restApiService;

  override ready() {
    super.ready();
    this._ensureAttribute('role', 'dialog');
  }

  loadData() {
    this._loading = true;

    const loadAccount = this.restApiService.getAccount().then(account => {
      if (!account) return;
      this._hasNameChange = false;
      this._hasUsernameChange = false;
      this._hasDisplayNameChange = false;
      // Provide predefined value for username to trigger computation of
      // username mutability.
      account.username = account.username || '';

      this._account = account;
      this._username = account.username;
    });

    const loadConfig = this.restApiService.getConfig().then(config => {
      this._serverConfig = config;
    });

    return Promise.all([loadAccount, loadConfig]).then(() => {
      this._loading = false;
    });
  }

  _usernameChanged() {
    if (this._loading || !this._account) {
      return;
    }
    this._hasUsernameChange =
      (this._account.username || '') !== (this._username || '');
  }

  @observe('_account.display_name')
  _displayNameChanged() {
    if (this._loading || !this._account) {
      return;
    }
    this._hasDisplayNameChange = true;
  }

  @observe('_account.name')
  _nameChanged() {
    if (this._loading || !this._account) {
      return;
    }
    this._hasNameChange = true;
  }

  _computeUsernameMutable(username?: string) {
    // Username may not be changed once it is set.
    return !username;
  }

  _computeUsernameEditable(config?: ServerInfo) {
    return !!config?.auth.editable_account_fields.includes(
      EditableAccountField.USER_NAME
    );
  }

  _computeNameMutable(config: ServerInfo) {
    return config.auth.editable_account_fields.includes(
      EditableAccountField.FULL_NAME
    );
  }

  _save() {
    this._saving = true;

    const promises = [];
    // Note that we are intentionally not acting on this._username being the
    // empty string (which is falsy).
    if (this._hasUsernameChange && this._usernameMutable && this._username) {
      promises.push(this.restApiService.setAccountUsername(this._username));
    }

    if (this._hasNameChange && this._nameMutable && this._account?.name) {
      promises.push(this.restApiService.setAccountName(this._account.name));
    }

    if (this._hasDisplayNameChange && this._account?.display_name) {
      promises.push(
        this.restApiService.setAccountDisplayName(this._account.display_name)
      );
    }

    return Promise.all(promises).then(() => {
      this._saving = false;
      fireEvent(this, 'account-detail-update');
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
    fireEvent(this, 'close');
  }

  _computeSaveDisabled(
    displayName?: string,
    name?: string,
    username?: string,
    saving?: boolean
  ) {
    return saving || (!displayName && !name && !username);
  }

  @observe('_loading')
  _loadingChanged() {
    this.classList.toggle('loading', this._loading);
  }
}
