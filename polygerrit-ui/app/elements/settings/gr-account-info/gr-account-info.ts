/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-autogrow-textarea/gr-autogrow-textarea';
import '../../shared/gr-avatar/gr-avatar';
import '../../shared/gr-date-formatter/gr-date-formatter';
import '../../shared/gr-tooltip-content/gr-tooltip-content';
import '../../../styles/gr-form-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-account-chip/gr-account-chip';
import '../../shared/gr-hovercard-account/gr-hovercard-account-contents';
import {AccountDetailInfo, ServerInfo} from '../../../types/common';
import {EditableAccountField} from '../../../constants/constants';
import {getAppContext} from '../../../services/app-context';
import {fire} from '../../../utils/event-util';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {when} from 'lit/directives/when.js';
import {ValueChangedEvent} from '../../../types/events';
import {formStyles} from '../../../styles/form-styles';
import {getDocUrl} from '../../../utils/url-util';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {GrAutogrowTextarea} from '../../shared/gr-autogrow-textarea/gr-autogrow-textarea';
import '@material/web/textfield/outlined-text-field';
import {materialStyles} from '../../../styles/gr-material-styles';

@customElement('gr-account-info')
export class GrAccountInfo extends LitElement {
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

  @state() private docsBaseUrl = '';

  private readonly restApiService = getAppContext().restApiService;

  private readonly getConfigModel = resolve(this, configModelToken);

  static override get styles() {
    return [
      materialStyles,
      sharedStyles,
      grFormStyles,
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
        gr-hovercard-account-contents {
          display: block;
          max-width: 600px;
          margin-top: var(--spacing-m);
          background: var(--dialog-background-color);
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
          box-shadow: var(--elevation-level-5);
        }
        gr-autogrow-textarea {
          background-color: var(--view-background-color);
          color: var(--primary-text-color);
        }
        gr-autogrow-textarea:focus {
          border: 2px solid var(--input-focus-border-color);
        }
        .lengthCounter {
          font-weight: var(--font-weight-normal);
        }
        p {
          max-width: 65ch;
          margin-bottom: var(--spacing-m);
        }
      `,
    ];
  }

  constructor() {
    super();
    subscribe(
      this,
      () => this.getConfigModel().docsBaseUrl$,
      docsBaseUrl => (this.docsBaseUrl = docsBaseUrl)
    );
  }

  override render() {
    if (!this.account || this.loading) return nothing;
    return html`<div class="gr-form-styles">
      <p>
        All profile fields below may be publicly displayed to others, including
        on changes you are associated with, as well as in search and
        autocompletion.
        <a href=${getDocUrl(this.docsBaseUrl, 'user-privacy.html')}
          >Learn more</a
        >
      </p>
      <gr-endpoint-decorator name="profile"></gr-endpoint-decorator>
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
            <md-outlined-text-field
              id="usernameInput"
              class="showBlueFocusBorder"
              ?disabled=${this.saving}
              @keydown=${this.handleKeydown}
              .value=${this.username ?? ''}
              @input=${(e: InputEvent) => {
                const target = e.target as HTMLInputElement;
                if (this.username === target.value) return;
                this.username = target.value;
                this.hasUsernameChange = true;
              }}
            >
            </md-outlined-text-field>
          </span>`,
          () => html`<span class="value">${this.username}</span>`
        )}
      </section>
      <section id="nameSection">
        <label class="title" for="nameInput">Full name</label>
        ${when(
          this.nameMutable,
          () => html`<span class="value">
            <md-outlined-text-field
              id="nameInput"
              class="showBlueFocusBorder"
              ?disabled=${this.saving}
              @keydown=${this.handleKeydown}
              .value=${this.account?.name ?? ''}
              @input=${(e: InputEvent) => {
                const target = e.target as HTMLInputElement;
                const oldAccount = this.account;
                if (!oldAccount || oldAccount.name === target.value) return;
                this.account = {...oldAccount, name: target.value};
                this.hasNameChange = true;
              }}
            >
            </md-outlined-text-field>
          </span>`,
          () => html` <span class="value">${this.account?.name}</span>`
        )}
      </section>
      <section>
        <label class="title" for="displayNameInput">Display name</label>
        <span class="value">
          <md-outlined-text-field
            id="displayNameInput"
            class="showBlueFocusBorder"
            ?disabled=${this.saving}
            @keydown=${this.handleKeydown}
            .value=${this.account.display_name ?? ''}
            @input=${(e: InputEvent) => {
              const target = e.target as HTMLInputElement;
              const oldAccount = this.account;
              if (!oldAccount || oldAccount.display_name === target.value) {
                return;
              }
              this.account = {...oldAccount, display_name: target.value};
              this.hasDisplayNameChange = true;
            }}
          >
          </md-outlined-text-field>
        </span>
      </section>
      <section>
        <span class="title">
          <label for="statusInput">About me (e.g. employer)</label>
          <div class="lengthCounter">
            ${this.account.status?.length ?? 0}/140
          </div>
        </span>
        <span class="value">
          <gr-autogrow-textarea
            id="statusInput"
            .label=${'statusInput'}
            ?disabled=${this.saving}
            maxlength="140"
            .value=${this.account?.status ?? ''}
            @input=${(e: InputEvent) => {
              const oldAccount = this.account;
              const value = (e.target as GrAutogrowTextarea).value ?? '';
              if (!oldAccount || oldAccount.status === value) return;
              this.account = {...oldAccount, status: value};
              this.hasStatusChange = true;
            }}
          ></gr-autogrow-textarea>
        </span>
      </section>
      <section>
        <span class="title">
          <gr-tooltip-content
            title="This is how you appear to others"
            has-tooltip
            show-icon
          >
            Account preview
          </gr-tooltip-content>
        </span>
        <span class="value">
          <gr-account-chip .account=${this.account}></gr-account-chip>
          <gr-hovercard-account-contents
            .account=${this.account}
          ></gr-hovercard-account-contents>
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
        fire(this, 'account-detail-update', {});
      });
  }

  delete() {
    return this.restApiService.deleteAccount();
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
    if (e.key === 'Enter') {
      e.stopPropagation();
      this.save();
    }
  }
}

declare global {
  interface HTMLElementEventMap {
    'unsaved-changes-changed': ValueChangedEvent<boolean>;
    /** Fired when account details are changed. */
    'account-detail-update': CustomEvent<{}>;
  }
  interface HTMLElementTagNameMap {
    'gr-account-info': GrAccountInfo;
  }
}
