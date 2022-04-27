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
import {ServerInfo, AccountDetailInfo} from '../../../types/common';
import {EditableAccountField} from '../../../constants/constants';
import {getAppContext} from '../../../services/app-context';
import {fireEvent} from '../../../utils/event-util';
import {LitElement, css, html, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators';
import {sharedStyles} from '../../../styles/shared-styles';
import {formStyles} from '../../../styles/gr-form-styles';
import {when} from 'lit/directives/when';
import {ifDefined} from 'lit/directives/if-defined';
import {BindValueChangeEvent} from '../../../types/events';

declare global {
  interface HTMLElementTagNameMap {
    'gr-registration-dialog': GrRegistrationDialog;
  }
}

@customElement('gr-registration-dialog')
export class GrRegistrationDialog extends LitElement {
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
  @query('#name') name?: HTMLInputElement;

  @query('#username') username?: HTMLInputElement;

  @query('#displayName') displayName?: HTMLInputElement;

  @property() settingsUrl?: string;

  @state() _account: Partial<AccountDetailInfo> = {};

  @state() _loading = true;

  @state() _saving = false;

  @state() _serverConfig?: ServerInfo;

  @state() _usernameMutable = false;

  @state() _hasUsernameChange?: boolean;

  @state() _username?: string;

  @state() _nameMutable?: boolean;

  @state() _hasNameChange?: boolean;

  @state() _hasDisplayNameChange?: boolean;

  private readonly restApiService = getAppContext().restApiService;

  override connectedCallback() {
    super.connectedCallback();
    if (!this.getAttribute('role')) {
      this.setAttribute('role', 'dialog');
    }
  }

  static override styles = [
    sharedStyles,
    formStyles,
    css`
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
        margin-top: var(--spacing-l);
        margin-bottom: var(--spacing-l);
      }
      header {
        border-bottom: 1px solid var(--border-color);
        font-weight: var(--font-weight-bold);
        margin-bottom: var(--spacing-l);
      }
      .container {
        padding: var(--spacing-m) var(--spacing-xl);
      }
      footer {
        display: flex;
        justify-content: flex-end;
      }
      footer gr-button {
        margin-left: var(--spacing-l);
      }
      input {
        width: 20em;
      }
    `,
  ];

  override render() {
    return html`<div class="container gr-form-styles">
      <header>Please confirm your contact information</header>
      <div class="loadingMessage">Loading...</div>
      <main>
        <p>
          The following contact information was automatically obtained when you
          signed in to the site. This information is used to display who you are
          to others, and to send updates to code reviews you have either started
          or subscribed to.
        </p>
        <hr />
        <section>
          <span class="title">Full Name</span>
          ${when(
            this._nameMutable,
            () => html`<span class="value">${this._account.name}</span>`,
            () => html`<span class="value">
              <iron-input
                .bindValue=${this._account.name}
                @bind-value-changed=${(e: BindValueChangeEvent) => {
                  const oldAccount = this._account;
                  if (!oldAccount || oldAccount.name === e.detail.value) return;
                  this._account = {...oldAccount, name: e.detail.value};
                  this._hasNameChange = true;
                }}
              >
                <input id="name" ?disabled=${this._saving} />
              </iron-input>
            </span>`
          )}
        </section>
        <section>
          <span class="title">Display Name</span>
          <span class="value">
            <iron-input
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
              <input id="displayName" ?disabled=${this._saving} />
            </iron-input>
          </span>
        </section>
        ${when(
          this._computeUsernameEditable(),
          () => html`<section>
            <span class="title">Username</span>
            ${when(
              this._usernameMutable,
              () => html`<span class="value">${this._username}</span>`,
              () => html` <span class="value">
                <iron-input
                  .bindValue=${this._username}
                  @bind-value-changed=${(e: BindValueChangeEvent) => {
                    if (!this.username || this._username === e.detail.value)
                      return;
                    this._username = e.detail.value;
                    this._hasUsernameChange = true;
                  }}
                >
                  <input id="username" ?disabled=${this._saving} />
                </iron-input>
              </span>`
            )}
          </section>`
        )}
        <hr />
        <p>
          More configuration options for Gerrit may be found in the
          <a @click=${this.close} href=${ifDefined(this.settingsUrl)}
            >settings</a
          >.
        </p>
      </main>
      <footer>
        <gr-button
          id="closeButton"
          link=""
          ?disabled=${this._saving}
          @click=${this._handleClose}
          >Close</gr-button
        >
        <gr-button
          id="saveButton"
          primary=""
          link=""
          ?disabled=${this._computeSaveDisabled()}
          @click=${this._handleSave}
          >Save</gr-button
        >
      </footer>
    </div>`;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('_account')) {
      this._usernameMutable = !this._account.username;
    }
    if (changedProperties.has('_serverConfig')) {
      this._nameMutable = this._computeNameMutable();
    }
    if (changedProperties.has('_loading')) {
      this.classList.toggle('loading', this._loading);
    }
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

  _computeUsernameEditable() {
    return !!this._serverConfig?.auth.editable_account_fields.includes(
      EditableAccountField.USER_NAME
    );
  }

  _computeNameMutable() {
    if (!this._serverConfig) return false;
    return this._serverConfig.auth.editable_account_fields.includes(
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

  _computeSaveDisabled() {
    return (
      this._saving ||
      (!this._account?.display_name && !this._account.name && !this._username)
    );
  }
}
