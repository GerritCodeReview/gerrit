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
import '../../shared/gr-avatar/gr-avatar';
import '../../shared/gr-date-formatter/gr-date-formatter';
import '../../../styles/gr-form-styles';
import '../../../styles/shared-styles';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-account-info_html';
import {customElement, property, observe} from '@polymer/decorators';
import {AccountDetailInfo, ServerInfo} from '../../../types/common';
import {EditableAccountField} from '../../../constants/constants';
import {appContext} from '../../../services/app-context';
import {fireEvent} from '../../../utils/event-util';

@customElement('gr-account-info')
export class GrAccountInfo extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when account details are changed.
   *
   * @event account-detail-update
   */

  @property({
    type: Boolean,
    notify: true,
    computed: '_computeUsernameMutable(_serverConfig, _account.username)',
  })
  usernameMutable?: boolean;

  @property({
    type: Boolean,
    notify: true,
    computed: '_computeNameMutable(_serverConfig)',
  })
  nameMutable?: boolean;

  @property({
    type: Boolean,
    notify: true,
    computed: '_computeDisplayNameMutable(_serverConfig)',
  })
  displayNameMutable?: boolean;

  @property({
    type: Boolean,
    notify: true,
    computed:
      '_computeHasUnsavedChanges(_hasNameChange, ' +
      '_hasUsernameChange, _hasStatusChange, _hasDisplayNameChange)',
  })
  hasUnsavedChanges?: boolean;

  @property({type: Boolean})
  _hasNameChange?: boolean;

  @property({type: Boolean})
  _hasUsernameChange?: boolean;

  @property({type: Boolean})
  _hasDisplayNameChange?: boolean;

  @property({type: Boolean})
  _hasStatusChange?: boolean;

  @property({type: Boolean})
  _loading = false;

  @property({type: Boolean})
  _saving = false;

  @property({type: Object})
  _account?: AccountDetailInfo;

  @property({type: Object})
  _serverConfig?: ServerInfo;

  @property({type: String, observer: '_usernameChanged'})
  _username?: string;

  @property({type: String})
  _avatarChangeUrl = '';

  private readonly restApiService = appContext.restApiService;

  loadData() {
    const promises = [];

    this._loading = true;

    promises.push(
      this.restApiService.getConfig().then(config => {
        this._serverConfig = config;
      })
    );

    promises.push(this.restApiService.invalidateAccountsDetailCache());

    promises.push(
      this.restApiService.getAccount().then(account => {
        if (!account) return;
        this._hasNameChange = false;
        this._hasUsernameChange = false;
        this._hasDisplayNameChange = false;
        this._hasStatusChange = false;
        // Provide predefined value for username to trigger computation of
        // username mutability.
        account.username = account.username || '';
        this._account = account;
        this._username = account.username;
      })
    );

    promises.push(
      this.restApiService.getAvatarChangeUrl().then(url => {
        this._avatarChangeUrl = url || '';
      })
    );

    return Promise.all(promises).then(() => {
      this._loading = false;
    });
  }

  save() {
    if (!this.hasUnsavedChanges) {
      return Promise.resolve();
    }

    this._saving = true;
    // Set only the fields that have changed.
    // Must be done in sequence to avoid race conditions (@see Issue 5721)
    return this._maybeSetName()
      .then(() => this._maybeSetUsername())
      .then(() => this._maybeSetDisplayName())
      .then(() => this._maybeSetStatus())
      .then(() => {
        this._hasNameChange = false;
        this._hasDisplayNameChange = false;
        this._hasStatusChange = false;
        this._saving = false;
        fireEvent(this, 'account-detail-update');
      });
  }

  _maybeSetName() {
    // Note that we are intentionally not acting on this._account.name being the
    // empty string (which is falsy).
    return this._hasNameChange && this.nameMutable && this._account?.name
      ? this.restApiService.setAccountName(this._account.name)
      : Promise.resolve();
  }

  _maybeSetUsername() {
    // Note that we are intentionally not acting on this._username being the
    // empty string (which is falsy).
    return this._hasUsernameChange && this.usernameMutable && this._username
      ? this.restApiService.setAccountUsername(this._username)
      : Promise.resolve();
  }

  _maybeSetDisplayName() {
    return this._hasDisplayNameChange &&
      this._account?.display_name !== undefined
      ? this.restApiService.setAccountDisplayName(this._account.display_name)
      : Promise.resolve();
  }

  _maybeSetStatus() {
    return this._hasStatusChange && this._account?.status !== undefined
      ? this.restApiService.setAccountStatus(this._account.status)
      : Promise.resolve();
  }

  _computeHasUnsavedChanges(
    nameChanged: boolean,
    usernameChanged: boolean,
    statusChanged: boolean,
    displayNameChanged: boolean
  ) {
    return (
      nameChanged || usernameChanged || statusChanged || displayNameChanged
    );
  }

  _computeUsernameMutable(config: ServerInfo, username?: string) {
    // Polymer 2: check for undefined
    if ([config, username].includes(undefined)) {
      return undefined;
    }

    // Username may not be changed once it is set.
    return (
      config.auth.editable_account_fields.includes(
        EditableAccountField.USER_NAME
      ) && !username
    );
  }

  _computeNameMutable(config: ServerInfo) {
    return config.auth.editable_account_fields.includes(
      EditableAccountField.FULL_NAME
    );
  }

  _computeDisplayNameMutable(config: ServerInfo) {
    return config.auth.editable_account_fields.includes(
      EditableAccountField.DISPLAY_NAME
    );
  }

  @observe('_account.status')
  _statusChanged() {
    if (this._loading) {
      return;
    }
    this._hasStatusChange = true;
  }

  @observe('_account.display_name')
  _displayNameChanged() {
    if (this._loading) {
      return;
    }
    this._hasDisplayNameChange = true;
  }

  _usernameChanged() {
    if (this._loading || !this._account) {
      return;
    }
    this._hasUsernameChange =
      (this._account.username || '') !== (this._username || '');
  }

  @observe('_account.name')
  _nameChanged() {
    if (this._loading) {
      return;
    }
    this._hasNameChange = true;
  }

  _handleKeydown(e: KeyboardEvent) {
    if (e.keyCode === 13) {
      // Enter
      e.stopPropagation();
      this.save();
    }
  }

  _hideAvatarChangeUrl(avatarChangeUrl: string) {
    if (!avatarChangeUrl) {
      return 'hide';
    }

    return '';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-account-info': GrAccountInfo;
  }
}
