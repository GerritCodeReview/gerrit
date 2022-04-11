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
import {AccountDetailInfo, ServerInfo} from '../../../types/common';
import {EditableAccountField} from '../../../constants/constants';
import {getAppContext} from '../../../services/app-context';
import {fire, fireEvent} from '../../../utils/event-util';
import {LitElement, css, html, nothing, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators';
import {sharedStyles} from '../../../styles/shared-styles';
import {formStyles} from '../../../styles/gr-form-styles';
import {when} from 'lit/directives/when';
import {BindValueChangeEvent, ValueChangedEvent} from '../../../types/events';

@customElement('gr-account-info')
export class GrAccountInfo extends LitElement {
  /**
   * Fired when account details are changed.
   *
   * @event account-detail-update
   */

  @state() usernameMutable?: boolean;

  @state() nameMutable?: boolean;

  @property({type: Boolean}) hasUnsavedChanges = false;

  @state() _hasNameChange = false;

  @state() _hasUsernameChange = false;

  @state() _hasDisplayNameChange = false;

  @state() _hasStatusChange = false;

  @state() _loading = false;

  @state() _saving = false;

  @state() _account?: AccountDetailInfo;

  @state() _serverConfig?: ServerInfo;

  @state() _username?: string;

  @state() _avatarChangeUrl = '';

  private readonly restApiService = getAppContext().restApiService;

  static override styles = [
    sharedStyles,
    formStyles,
    css`
      gr-avatar {
        height: 120px;
        width: 120px;
        margin-right: var(--spacing-xs);
        vertical-align: -0.25em;
      }
      div section.hide {
        display: none;
      }
    `,
  ];

  override render() {
    if (!this._account || this._loading) return nothing;
    return html`<div class="gr-form-styles">
      <section>
        <span class="title"></span>
        <span class="value">
          <gr-avatar .account=${this._account} imageSize="120"></gr-avatar>
        </span>
      </section>
      ${when(
        this._avatarChangeUrl,
        () => html` <section>
          <span class="title"></span>
          <span class="value">
            <a href=${this._avatarChangeUrl}> Change avatar </a>
          </span>
        </section>`
      )}
      <section>
        <span class="title">ID</span>
        <span class="value">${this._account._account_id}</span>
      </section>
      <section>
        <span class="title">Email</span>
        <span class="value">${this._account.email}</span>
      </section>
      <section>
        <span class="title">Registered</span>
        <span class="value">
          <gr-date-formatter
            withTooltip
            .dateStr=${this._account.registered_on}
          ></gr-date-formatter>
        </span>
      </section>
      <section id="usernameSection">
        <span class="title">Username</span>
        ${when(
          this.usernameMutable,
          () => html`<span class="value">
            <iron-input
              @keydown=${this._handleKeydown}
              .bindValue=${this._username}
              @bind-value-changed=${(e: BindValueChangeEvent) => {
                if (!this._username || this._username === e.detail.value)
                  return;
                this._username = e.detail.value;
                this._hasUsernameChange = true;
              }}
              id="usernameIronInput"
            >
              <input
                id="usernameInput"
                ?disabled=${this._saving}
                @keydown=${this._handleKeydown}
              />
            </iron-input>
          </span>`,
          () => html`<span class="value">${this._username}</span>`
        )}
      </section>
      <section id="nameSection">
        <label class="title" for="nameInput">Full name</label>
        ${when(
          this.nameMutable,
          () => html`<span class="value">
            <iron-input
              @keydown=${this._handleKeydown}
              .bindValue=${this._account?.name}
              @bind-value-changed=${(e: BindValueChangeEvent) => {
                const oldAccount = this._account;
                if (!oldAccount || oldAccount.name === e.detail.value) return;
                this._account = {...oldAccount, name: e.detail.value};
                this._hasNameChange = true;
              }}
              id="nameIronInput"
            >
              <input
                id="nameInput"
                ?disabled=${this._saving}
                @keydown=${this._handleKeydown}
              />
            </iron-input>
          </span>`,
          () => html` <span class="value">${this._account?.name}</span>`
        )}
      </section>
      <section>
        <label class="title" for="displayNameInput">Display name</label>
        <span class="value">
          <iron-input
            @keydown=${this._handleKeydown}
            .bindValue=${this._account.display_name}
            @bind-value-changed=${(e: BindValueChangeEvent) => {
              const oldAccount = this._account;
              if (!oldAccount || oldAccount.display_name === e.detail.value) {
                return;
              }
              this._account = {...oldAccount, display_name: e.detail.value};
              this._hasDisplayNameChange = true;
            }}
          >
            <input
              id="displayNameInput"
              ?disabled=${this._saving}
              @keydown=${this._handleKeydown}
            />
          </iron-input>
        </span>
      </section>
      <section>
        <label class="title" for="statusInput">About me (e.g. employer)</label>
        <span class="value">
          <iron-input
            @keydown=${this._handleKeydown}
            .bindValue=${this._account?.status}
            @bind-value-changed=${(e: BindValueChangeEvent) => {
              const oldAccount = this._account;
              if (!oldAccount || oldAccount.status === e.detail.value) return;
              this._account = {...oldAccount, status: e.detail.value};
              this._hasStatusChange = true;
            }}
          >
            <input
              id="statusInput"
              ?disabled=${this._saving}
              @keydown=${this._handleKeydown}
            />
          </iron-input>
        </span>
      </section>
    </div>`;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('_serverConfig')) {
      this.usernameMutable = this._computeUsernameMutable();
      this.nameMutable = this._computeNameMutable();
    }
    if (
      changedProperties.has('_hasNameChange') ||
      changedProperties.has('_hasUsernameChange') ||
      changedProperties.has('_hasStatusChange') ||
      changedProperties.has('_hasDisplayNameChange')
    ) {
      this.hasUnsavedChanges = this._computeHasUnsavedChanges();
    }
    if (changedProperties.has('hasUnsavedChanges')) {
      fire(this, 'unsaved-changes-changed', {
        value: this.hasUnsavedChanges,
      });
    }
  }

  loadData() {
    const promises = [];

    this._loading = true;

    promises.push(
      this.restApiService.getConfig().then(config => {
        this._serverConfig = config;
      })
    );

    this.restApiService.invalidateAccountsDetailCache();

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

  _computeHasUnsavedChanges() {
    return (
      this._hasNameChange ||
      this._hasUsernameChange ||
      this._hasStatusChange ||
      this._hasDisplayNameChange
    );
  }

  _computeUsernameMutable() {
    if (!this._serverConfig) return false;
    // Username may not be changed once it is set.
    return (
      this._serverConfig.auth.editable_account_fields.includes(
        EditableAccountField.USER_NAME
      ) && !this._account?.username
    );
  }

  _computeNameMutable() {
    if (!this._serverConfig) return false;
    return this._serverConfig.auth.editable_account_fields.includes(
      EditableAccountField.FULL_NAME
    );
  }

  _handleKeydown(e: KeyboardEvent) {
    if (e.keyCode === 13) {
      // Enter
      e.stopPropagation();
      this.save();
    }
  }
}

declare global {
  interface HTMLElementEventMap {
    'unsaved-changes-changed': ValueChangedEvent<boolean>;
  }
  interface HTMLElementTagNameMap {
    'gr-account-info': GrAccountInfo;
  }
}
