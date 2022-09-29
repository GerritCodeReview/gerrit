/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-input/iron-input';
import '../../../styles/gr-form-styles';
import '../../shared/gr-button/gr-button';
import {ServerInfo, AccountDetailInfo} from '../../../types/common';
import {EditableAccountField} from '../../../constants/constants';
import {getAppContext} from '../../../services/app-context';
import {fireEvent} from '../../../utils/event-util';
import {LitElement, css, html, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {formStyles} from '../../../styles/gr-form-styles';
import {when} from 'lit/directives/when.js';
import {ifDefined} from 'lit/directives/if-defined.js';
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
  @query('#name') nameInput?: HTMLInputElement;

  @query('#username') usernameInput?: HTMLInputElement;

  @query('#displayName') displayName?: HTMLInputElement;

  @property() settingsUrl?: string;

  @state() account: Partial<AccountDetailInfo> = {};

  @state() loading = true;

  @state() saving = false;

  @state() serverConfig?: ServerInfo;

  @state() usernameMutable = false;

  @state() hasUsernameChange?: boolean;

  @state() username?: string;

  @state() nameMutable?: boolean;

  @state() hasNameChange?: boolean;

  @state() hasDisplayNameChange?: boolean;

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
        <gr-endpoint-decorator
          .name=${'registration-text'}
        ></gr-endpoint-decorator>
        <hr />
        <section>
          <span class="title">Full Name</span>
          ${when(
            this.nameMutable,
            () => html`<span class="value">
              <iron-input
                .bindValue=${this.account.name}
                @bind-value-changed=${(e: BindValueChangeEvent) => {
                  const oldAccount = this.account;
                  if (!oldAccount || oldAccount.name === e.detail.value) return;
                  this.account = {...oldAccount, name: e.detail.value};
                  this.hasNameChange = true;
                }}
              >
                <input id="name" ?disabled=${this.saving} />
              </iron-input>
            </span>`,
            () => html`<span class="value">${this.account.name}</span>`
          )}
        </section>
        <section>
          <span class="title">Display Name</span>
          <span class="value">
            <iron-input
              .bindValue=${this.account.display_name}
              @bind-value-changed=${(e: BindValueChangeEvent) => {
                const oldAccount = this.account;
                if (!oldAccount || oldAccount.display_name === e.detail.value) {
                  return;
                }
                this.account = {...oldAccount, display_name: e.detail.value};
                this.hasDisplayNameChange = true;
              }}
            >
              <input id="displayName" ?disabled=${this.saving} />
            </iron-input>
          </span>
        </section>
        ${when(
          this.computeUsernameEditable(),
          () => html`<section>
            <span class="title">Username</span>
            ${when(
              this.usernameMutable,
              () => html` <span class="value">
                <iron-input
                  .bindValue=${this.username}
                  @bind-value-changed=${(e: BindValueChangeEvent) => {
                    if (!this.usernameInput || this.username === e.detail.value)
                      return;
                    this.username = e.detail.value;
                    this.hasUsernameChange = true;
                  }}
                >
                  <input id="username" ?disabled=${this.saving} />
                </iron-input>
              </span>`,
              () => html`<span class="value">${this.username}</span>`
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
          link
          ?disabled=${this.saving}
          @click=${this.handleClose}
          >Close</gr-button
        >
        <gr-button
          id="saveButton"
          primary
          link
          ?disabled=${this.computeSaveDisabled()}
          @click=${this.handleSave}
          >Save</gr-button
        >
      </footer>
    </div>`;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('account')) {
      this.usernameMutable = !this.account.username;
    }
    if (changedProperties.has('serverConfig')) {
      this.nameMutable = this.computeNameMutable();
    }
    if (changedProperties.has('loading')) {
      this.classList.toggle('loading', this.loading);
    }
  }

  loadData() {
    this.loading = true;

    const loadAccount = this.restApiService.getAccount().then(account => {
      if (!account) return;
      this.hasNameChange = false;
      this.hasUsernameChange = false;
      this.hasDisplayNameChange = false;
      // Provide predefined value for username to trigger computation of
      // username mutability.
      account.username = account.username || '';

      this.account = account;
      this.username = account.username;
    });

    const loadConfig = this.restApiService.getConfig().then(config => {
      this.serverConfig = config;
    });

    return Promise.all([loadAccount, loadConfig]).then(() => {
      this.loading = false;
    });
  }

  // private but used in test
  computeUsernameEditable() {
    return !!this.serverConfig?.auth.editable_account_fields.includes(
      EditableAccountField.USER_NAME
    );
  }

  private computeNameMutable() {
    return !!this.serverConfig?.auth.editable_account_fields.includes(
      EditableAccountField.FULL_NAME
    );
  }

  // private but used in test
  save() {
    this.saving = true;

    const promises = [];
    // Note that we are intentionally not acting on this._username being the
    // empty string (which is falsy).
    if (this.hasUsernameChange && this.usernameMutable && this.username) {
      promises.push(this.restApiService.setAccountUsername(this.username));
    }

    if (this.hasNameChange && this.nameMutable && this.account?.name) {
      promises.push(this.restApiService.setAccountName(this.account.name));
    }

    if (this.hasDisplayNameChange && this.account?.display_name) {
      promises.push(
        this.restApiService.setAccountDisplayName(this.account.display_name)
      );
    }

    return Promise.all(promises).then(() => {
      this.saving = false;
      fireEvent(this, 'account-detail-update');
    });
  }

  private handleSave(e: Event) {
    e.preventDefault();
    this.save().then(() => this.close());
  }

  private handleClose(e: Event) {
    e.preventDefault();
    this.close();
  }

  private close() {
    this.saving = true; // disable buttons indefinitely
    fireEvent(this, 'close');
  }

  // private but used in test
  computeSaveDisabled() {
    return (
      this.saving ||
      (!this.account?.display_name && !this.account.name && !this.username)
    );
  }
}
