/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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

  // private but used in test
  @state() nameMutable?: boolean;

  @property({type: Boolean}) hasUnsavedChanges = false;

  // private but used in test
  @state() hasNameChange = false;

  // private but used in test
  @state() hasUsernameChange = false;

  // private but used in test
  @state() hasDisplayNameChange = false;

  // private but used in test
  @state() hasStatusChange = false;

  // private but used in test
  @state() loading = false;

  @state() private saving = false;

  // private but used in test
  @state() account?: AccountDetailInfo;

  // private but used in test
  @state() serverConfig?: ServerInfo;

  // private but used in test
  @state() username?: string;

  @state() private avatarChangeUrl = '';

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
    if (!this.account || this.loading) return nothing;
    return html`<div class="gr-form-styles">
      <section>
        <span class="title"></span>
        <span class="value">
          <gr-avatar .account=${this.account} imageSize="120"></gr-avatar>
        </span>
      </section>
      ${when(
        this.avatarChangeUrl,
        () => html` <section>
          <span class="title"></span>
          <span class="value">
            <a href=${this.avatarChangeUrl}> Change avatar </a>
          </span>
        </section>`
      )}
      <section>
        <span class="title">ID</span>
        <span class="value">${this.account._account_id}</span>
      </section>
      <section>
        <span class="title">Email</span>
        <span class="value">${this.account.email}</span>
      </section>
      <section>
        <span class="title">Registered</span>
        <span class="value">
          <gr-date-formatter
            withTooltip
            .dateStr=${this.account.registered_on}
          ></gr-date-formatter>
        </span>
      </section>
      <section id="usernameSection">
        <span class="title">Username</span>
        ${when(
          this.computeUsernameEditable(),
          () => html`<span class="value">
            <iron-input
              @keydown=${this.handleKeydown}
              .bindValue=${this.username}
              @bind-value-changed=${(e: BindValueChangeEvent) => {
                if (this.username === e.detail.value) return;
                this.username = e.detail.value;
                this.hasUsernameChange = true;
              }}
              id="usernameIronInput"
            >
              <input
                id="usernameInput"
                ?disabled=${this.saving}
                @keydown=${this.handleKeydown}
              />
            </iron-input>
          </span>`,
          () => html`<span class="value">${this.username}</span>`
        )}
      </section>
      <section id="nameSection">
        <label class="title" for="nameInput">Full name</label>
        ${when(
          this.nameMutable,
          () => html`<span class="value">
            <iron-input
              @keydown=${this.handleKeydown}
              .bindValue=${this.account?.name}
              @bind-value-changed=${(e: BindValueChangeEvent) => {
                const oldAccount = this.account;
                if (!oldAccount || oldAccount.name === e.detail.value) return;
                this.account = {...oldAccount, name: e.detail.value};
                this.hasNameChange = true;
              }}
              id="nameIronInput"
            >
              <input
                id="nameInput"
                ?disabled=${this.saving}
                @keydown=${this.handleKeydown}
              />
            </iron-input>
          </span>`,
          () => html` <span class="value">${this.account?.name}</span>`
        )}
      </section>
      <section>
        <label class="title" for="displayNameInput">Display name</label>
        <span class="value">
          <iron-input
            @keydown=${this.handleKeydown}
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
            <input
              id="displayNameInput"
              ?disabled=${this.saving}
              @keydown=${this.handleKeydown}
            />
          </iron-input>
        </span>
      </section>
      <section>
        <label class="title" for="statusInput">About me (e.g. employer)</label>
        <span class="value">
          <iron-input
            id="statusIronInput"
            @keydown=${this.handleKeydown}
            .bindValue=${this.account?.status}
            @bind-value-changed=${(e: BindValueChangeEvent) => {
              const oldAccount = this.account;
              if (!oldAccount || oldAccount.status === e.detail.value) return;
              this.account = {...oldAccount, status: e.detail.value};
              this.hasStatusChange = true;
            }}
          >
            <input
              id="statusInput"
              ?disabled=${this.saving}
              @keydown=${this.handleKeydown}
            />
          </iron-input>
        </span>
      </section>
    </div>`;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('serverConfig')) {
      this.nameMutable = this.computeNameMutable();
    }

    if (
      changedProperties.has('hasNameChange') ||
      changedProperties.has('hasUsernameChange') ||
      changedProperties.has('hasStatusChange') ||
      changedProperties.has('hasDisplayNameChange')
    ) {
      this.hasUnsavedChanges = this.computeHasUnsavedChanges();
    }
    if (changedProperties.has('hasUnsavedChanges')) {
      fire(this, 'unsaved-changes-changed', {
        value: this.hasUnsavedChanges,
      });
    }
  }

  loadData() {
    const promises = [];

    this.loading = true;

    promises.push(
      this.restApiService.getConfig().then(config => {
        this.serverConfig = config;
      })
    );

    this.restApiService.invalidateAccountsDetailCache();

    promises.push(
      this.restApiService.getAccount().then(account => {
        if (!account) return;
        this.hasNameChange = false;
        this.hasUsernameChange = false;
        this.hasDisplayNameChange = false;
        this.hasStatusChange = false;
        // Provide predefined value for username to trigger computation of
        // username mutability.
        account.username = account.username || '';
        this.account = account;
        this.username = account.username;
      })
    );

    promises.push(
      this.restApiService.getAvatarChangeUrl().then(url => {
        this.avatarChangeUrl = url || '';
      })
    );

    return Promise.all(promises).then(() => {
      this.loading = false;
    });
  }

  save() {
    if (!this.hasUnsavedChanges) {
      return Promise.resolve();
    }

    this.saving = true;
    // Set only the fields that have changed.
    // Must be done in sequence to avoid race conditions (@see Issue 5721)
    return this.maybeSetName()
      .then(() => this.maybeSetUsername())
      .then(() => this.maybeSetDisplayName())
      .then(() => this.maybeSetStatus())
      .then(() => {
        this.hasNameChange = false;
        this.hasUsernameChange = false;
        this.hasDisplayNameChange = false;
        this.hasStatusChange = false;
        this.saving = false;
        fireEvent(this, 'account-detail-update');
      });
  }

  private maybeSetName() {
    // Note that we are intentionally not acting on this._account.name being the
    // empty string (which is falsy).
    return this.hasNameChange && this.nameMutable && this.account?.name
      ? this.restApiService.setAccountName(this.account.name)
      : Promise.resolve();
  }

  private maybeSetUsername() {
    // Note that we are intentionally not acting on this._username being the
    // empty string (which is falsy).
    return this.hasUsernameChange &&
      this.computeUsernameEditable() &&
      this.username
      ? this.restApiService.setAccountUsername(this.username)
      : Promise.resolve();
  }

  private maybeSetDisplayName() {
    return this.hasDisplayNameChange && this.account?.display_name !== undefined
      ? this.restApiService.setAccountDisplayName(this.account.display_name)
      : Promise.resolve();
  }

  private maybeSetStatus() {
    return this.hasStatusChange && this.account?.status !== undefined
      ? this.restApiService.setAccountStatus(this.account.status)
      : Promise.resolve();
  }

  private computeHasUnsavedChanges() {
    return (
      this.hasNameChange ||
      this.hasUsernameChange ||
      this.hasStatusChange ||
      this.hasDisplayNameChange
    );
  }

  // private but used in test
  computeUsernameEditable() {
    return (
      !!this.serverConfig?.auth.editable_account_fields.includes(
        EditableAccountField.USER_NAME
      ) && !this.account?.username
    );
  }

  private computeNameMutable() {
    return !!this.serverConfig?.auth.editable_account_fields.includes(
      EditableAccountField.FULL_NAME
    );
  }

  private handleKeydown(e: KeyboardEvent) {
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
