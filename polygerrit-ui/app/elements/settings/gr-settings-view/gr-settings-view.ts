/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-input/iron-input';
import '@polymer/paper-toggle-button/paper-toggle-button';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../gr-change-table-editor/gr-change-table-editor';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-diff-preferences/gr-diff-preferences';
import '../../shared/gr-page-nav/gr-page-nav';
import '../../shared/gr-select/gr-select';
import '../../shared/gr-icon/gr-icon';
import '../gr-account-info/gr-account-info';
import '../gr-agreements-list/gr-agreements-list';
import '../gr-edit-preferences/gr-edit-preferences';
import '../gr-email-editor/gr-email-editor';
import '../gr-gpg-editor/gr-gpg-editor';
import '../gr-group-list/gr-group-list';
import '../gr-http-password/gr-http-password';
import '../gr-identities/gr-identities';
import '../gr-menu-editor/gr-menu-editor';
import '../gr-preferences/gr-preferences';
import '../gr-ssh-editor/gr-ssh-editor';
import '../gr-watched-projects-editor/gr-watched-projects-editor';
import '../../shared/gr-dialog/gr-dialog';
import {GrAccountInfo} from '../gr-account-info/gr-account-info';
import {GrWatchedProjectsEditor} from '../gr-watched-projects-editor/gr-watched-projects-editor';
import {GrGroupList} from '../gr-group-list/gr-group-list';
import {GrIdentities} from '../gr-identities/gr-identities';
import {GrDiffPreferences} from '../../shared/gr-diff-preferences/gr-diff-preferences';
import {
  AccountDetailInfo,
  PreferencesInput,
  ServerInfo,
} from '../../../types/common';
import {GrSshEditor} from '../gr-ssh-editor/gr-ssh-editor';
import {GrGpgEditor} from '../gr-gpg-editor/gr-gpg-editor';
import {GrEmailEditor} from '../gr-email-editor/gr-email-editor';
import {fire, fireAlert, fireTitleChange} from '../../../utils/event-util';
import {getAppContext} from '../../../services/app-context';
import {BindValueChangeEvent, ValueChangedEvent} from '../../../types/events';
import {LitElement, css, html} from 'lit';
import {customElement, query, queryAsync, state} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {paperStyles} from '../../../styles/gr-paper-styles';
import {fontStyles} from '../../../styles/gr-font-styles';
import {when} from 'lit/directives/when.js';
import {pageNavStyles} from '../../../styles/gr-page-nav-styles';
import {menuPageStyles} from '../../../styles/gr-menu-page-styles';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {settingsViewModelToken} from '../../../models/views/settings';
import {
  changeTablePrefs,
  userModelToken,
} from '../../../models/user/user-model';
import {modalStyles} from '../../../styles/gr-modal-styles';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {rootUrl} from '../../../utils/url-util';

const HTTP_AUTH = ['HTTP', 'HTTP_LDAP'];

/**
 * This provides an interface to show all settings for a user profile.
 * In most cases a individual module is used per setting to make
 * code more readable. In other cases, it is created within this module.
 */
@customElement('gr-settings-view')
export class GrSettingsView extends LitElement {
  /**
   * Fired with email confirmation text, or when the page reloads.
   *
   * @event show-alert
   */

  @query('#accountInfo', true) accountInfo!: GrAccountInfo;

  @query('#confirm-account-deletion')
  private deleteAccountConfirmationDialog?: HTMLDialogElement;

  @query('#dump-account-state')
  private dumpAccountStateConfirmationDialog?: HTMLDialogElement;

  @query('#watchedProjectsEditor', true)
  watchedProjectsEditor!: GrWatchedProjectsEditor;

  @query('#groupList', true) groupList!: GrGroupList;

  @query('#identities', true) identities!: GrIdentities;

  @query('#diffPrefs') diffPrefs!: GrDiffPreferences;

  @queryAsync('#sshEditor') sshEditorPromise!: Promise<GrSshEditor>;

  @queryAsync('#gpgEditor') gpgEditorPromise!: Promise<GrGpgEditor>;

  @query('#emailEditor', true) emailEditor!: GrEmailEditor;

  @state() prefs: PreferencesInput = {};

  @state() private accountInfoChanged = false;

  // private but used in test
  @state() localChangeTableColumns: string[] = [];

  @state() private loading = true;

  @state() private changeTableChanged = false;

  @state() private diffPrefsChanged = false;

  @state() private watchedProjectsChanged = false;

  @state() private keysChanged = false;

  @state() private gpgKeysChanged = false;

  // private but used in test
  @state() newEmail?: string;

  // private but used in test
  @state() addingEmail = false;

  // private but used in test
  @state() lastSentVerificationEmail?: string | null = null;

  // private but used in test
  @state() serverConfig?: ServerInfo;

  @state() private emailsChanged = false;

  // private but used in test
  @state() emailToken?: string;

  // private but used in test
  @state() showNumber?: boolean;

  @state() account?: AccountDetailInfo;

  @state() isDeletingAccount = false;

  @state() accountState?: string;

  // private but used in test
  public _testOnly_loadingPromise?: Promise<void>;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getUserModel = resolve(this, userModelToken);

  // private but used in test
  readonly flagsService = getAppContext().flagsService;

  private readonly getViewModel = resolve(this, settingsViewModelToken);

  private readonly getNavigation = resolve(this, navigationToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getViewModel().emailToken$,
      x => {
        this.emailToken = x;
        this.confirmEmail();
      }
    );
    subscribe(
      this,
      () => this.getUserModel().account$,
      acc => {
        this.account = acc;
      }
    );
    subscribe(
      this,
      () => this.getUserModel().preferences$,
      prefs => {
        if (!prefs) {
          throw new Error('getPreferences returned undefined');
        }
        this.prefs = prefs;
        this.showNumber = !!prefs.legacycid_in_change_table;
        this.localChangeTableColumns = changeTablePrefs(prefs);
      }
    );
  }

  // private, but used in tests
  async confirmEmail() {
    if (!this.emailToken) return;
    const message = await this.restApiService.confirmEmail(this.emailToken);
    if (message) fireAlert(this, message);
    this.getViewModel().clearToken();
    await this.getUserModel().loadEmails(true);
  }

  override connectedCallback() {
    super.connectedCallback();
    // Polymer 2: anchor tag won't work on shadow DOM
    // we need to manually calling scrollIntoView when hash changed
    document.addEventListener('location-change', this.handleLocationChange);
    fireTitleChange('Settings');
  }

  private async getAccountState() {
    const state = await this.restApiService.getAccountState();
    if (state) {
      this.accountState = JSON.stringify(state, null, 2);
    } else {
      this.accountState = 'ERROR: failed to get account state';
    }
  }

  override firstUpdated() {
    const promises: Array<Promise<unknown>> = [
      this.accountInfo.loadData(),
      this.watchedProjectsEditor.loadData(),
      this.groupList.loadData(),
      this.identities.loadData(),
    ];

    promises.push(
      this.restApiService.getConfig().then(config => {
        this.serverConfig = config;
        const configPromises: Array<Promise<void>> = [];

        if (this.serverConfig?.sshd) {
          configPromises.push(
            this.sshEditorPromise.then(sshEditor => sshEditor.loadData())
          );
        }

        if (this.serverConfig?.receive?.enable_signed_push) {
          configPromises.push(
            this.gpgEditorPromise.then(gpgEditor => gpgEditor.loadData())
          );
        }

        return Promise.all(configPromises);
      })
    );

    this._testOnly_loadingPromise = Promise.all(promises).then(() => {
      this.loading = false;

      // Handle anchor tag for initial load
      this.handleLocationChange();
    });
  }

  static override get styles() {
    return [
      sharedStyles,
      paperStyles,
      fontStyles,
      grFormStyles,
      modalStyles,
      menuPageStyles,
      pageNavStyles,
      css`
        :host {
          color: var(--primary-text-color);
          overflow: auto;
        }
        h2 {
          font-family: var(--header-font-family);
          font-size: var(--font-size-h2);
          font-weight: var(--font-weight-h2);
          line-height: var(--line-height-h2);
        }
        .newEmailInput {
          width: 20em;
        }
        #email {
          margin-bottom: var(--spacing-l);
        }
        .filters p {
          margin-bottom: var(--spacing-l);
        }
        .queryExample em {
          color: violet;
        }
        .toggle {
          align-items: center;
          display: flex;
          margin-bottom: var(--spacing-l);
          margin-right: var(--spacing-l);
        }
        .account-button {
          margin-left: var(--spacing-l);
        }
        .account-state-output {
          width: 100vh;
          max-width: calc(100% - var(--spacing-xl));
          height: 50vh;
          margin-bottom: var(--spacing-l);
        }
        .account-state-note {
          width: 100vh;
          max-width: calc(100% - var(--spacing-xl));
        }
        .confirm-account-deletion-main ul {
          list-style: disc inside;
          margin-left: var(--spacing-l);
        }
      `,
    ];
  }

  override render() {
    const isLoading = this.loading || this.loading === undefined;
    return html`<div class="loading" ?hidden=${!isLoading}>Loading...</div>
      <div ?hidden=${isLoading}>
        <gr-page-nav class="navStyles">
          <ul>
            <li><a href="#Profile">Profile</a></li>
            <li><a href="#Preferences">Preferences</a></li>
            <li><a href="#DiffPreferences">Diff Preferences</a></li>
            <li><a href="#EditPreferences">Edit Preferences</a></li>
            <li><a href="#Menu">Menu</a></li>
            <li><a href="#ChangeTableColumns">Change Table Columns</a></li>
            <li><a href="#Notifications">Notifications</a></li>
            <li><a href="#EmailAddresses">Email Addresses</a></li>
            ${when(
              this.showHttpAuth(),
              () =>
                html`<li><a href="#HTTPCredentials">HTTP Credentials</a></li>`
            )}
            ${when(
              this.serverConfig?.sshd,
              () => html`<li><a href="#SSHKeys"> SSH Keys </a></li>`
            )}
            ${when(
              this.serverConfig?.receive?.enable_signed_push,
              () => html`<li><a href="#GPGKeys"> GPG Keys </a></li>`
            )}
            <li><a href="#Groups">Groups</a></li>
            <li><a href="#Identities">Identities</a></li>
            ${when(
              this.serverConfig?.auth.use_contributor_agreements,
              () => html`<li><a href="#Agreements">Agreements</a></li>`
            )}
            <gr-endpoint-decorator name="settings-menu-item">
            </gr-endpoint-decorator>
          </ul>
        </gr-page-nav>
        <div class="main gr-form-styles">
          <h1 class="heading-1">User Settings</h1>
          <h2
            id="Profile"
            class=${this.computeHeaderClass(this.accountInfoChanged)}
          >
            Profile
          </h2>
          <fieldset id="profile">
            <gr-account-info
              id="accountInfo"
              ?hasUnsavedChanges=${this.accountInfoChanged}
              @unsaved-changes-changed=${(e: ValueChangedEvent<boolean>) => {
                this.accountInfoChanged = e.detail.value;
              }}
              @account-detail-update=${() => {
                fire(this, 'account-detail-update', {});
              }}
            ></gr-account-info>
            <gr-button
              @click=${() => {
                this.accountInfo.save();
              }}
              ?disabled=${!this.accountInfoChanged}
              >Save changes</gr-button
            >
            <gr-button
              class="account-button"
              @click=${() => {
                this.confirmDeleteAccount();
              }}
              >Delete Account</gr-button
            >
            <gr-button
              class="account-button"
              @click=${() => {
                this.dumpAccountState();
              }}
              >Dump Account State</gr-button
            >
            <dialog id="confirm-account-deletion">
              <gr-dialog
                @cancel=${() => this.deleteAccountConfirmationDialog?.close()}
                @confirm=${() => this.deleteAccount()}
                .loading=${this.isDeletingAccount}
                .loadingLabel=${'Deleting account'}
                .confirmLabel=${'Delete account'}
              >
                <div class="confirm-account-deletion-header" slot="header">
                  Are you sure you wish to delete your account?
                </div>
                <div class="confirm-account-deletion-main" slot="main">
                  <ul>
                    <li>Deleting your account is not reversible.</li>
                    <li>Deleting your account will not delete your changes.</li>
                  </ul>
                </div>
              </gr-dialog>
            </dialog>
            <dialog id="dump-account-state">
              <gr-dialog
                cancel-label=""
                @confirm=${() =>
                  this.dumpAccountStateConfirmationDialog?.close()}
                confirm-label="OK"
                confirm-on-enter=""
              >
                <div slot="header">Account State:</div>
                <div slot="main">
                  <textarea class="account-state-output" readonly>
${this.accountState}</textarea
                  >
                  <p class="account-state-note">
                    Note: The account state may contain sensitive data (e.g.
                    deadnames). Share it with others only on a need to know
                    basis (e.g. for debugging account or permission issues).
                  </p>
                </div>
              </gr-dialog>
            </dialog>
          </fieldset>
          <gr-preferences id="preferences"></gr-preferences>
          <h2
            id="DiffPreferences"
            class=${this.computeHeaderClass(this.diffPrefsChanged)}
          >
            Diff Preferences
          </h2>
          <fieldset id="diffPreferences">
            <gr-diff-preferences
              id="diffPrefs"
              @has-unsaved-changes-changed=${(
                e: ValueChangedEvent<boolean>
              ) => {
                this.diffPrefsChanged = e.detail.value;
              }}
            ></gr-diff-preferences>
            <gr-button
              id="saveDiffPrefs"
              @click=${() => {
                this.diffPrefs.save();
              }}
              ?disabled=${!this.diffPrefsChanged}
              >Save changes</gr-button
            >
          </fieldset>
          <gr-edit-preferences id="EditPreferences"></gr-edit-preferences>
          <gr-menu-editor id="Menu"></gr-menu-editor>
          <h2
            id="ChangeTableColumns"
            class=${this.computeHeaderClass(this.changeTableChanged)}
          >
            Change Table Columns
          </h2>
          <fieldset id="changeTableColumns">
            <gr-change-table-editor
              .showNumber=${this.showNumber}
              @show-number-changed=${(e: ValueChangedEvent<boolean>) => {
                this.showNumber = e.detail.value;
                this.changeTableChanged = true;
              }}
              .displayedColumns=${this.localChangeTableColumns}
              @displayed-columns-changed=${(e: ValueChangedEvent<string[]>) => {
                this.localChangeTableColumns = e.detail.value;
                this.changeTableChanged = true;
              }}
            >
            </gr-change-table-editor>
            <gr-button
              id="saveChangeTable"
              @click=${this.handleSaveChangeTable}
              ?disabled=${!this.changeTableChanged}
              >Save changes</gr-button
            >
          </fieldset>
          <h2
            id="Notifications"
            class=${this.computeHeaderClass(this.watchedProjectsChanged)}
          >
            Notifications
          </h2>
          <fieldset id="watchedProjects">
            <gr-watched-projects-editor
              @has-unsaved-changes-changed=${(
                e: ValueChangedEvent<boolean>
              ) => {
                this.watchedProjectsChanged = e.detail.value;
              }}
              id="watchedProjectsEditor"
            ></gr-watched-projects-editor>
            <gr-button
              @click=${() => {
                this.watchedProjectsEditor.save();
              }}
              ?disabled=${!this.watchedProjectsChanged}
              id="_handleSaveWatchedProjects"
              >Save changes</gr-button
            >
          </fieldset>
          <h2
            id="EmailAddresses"
            class=${this.computeHeaderClass(this.emailsChanged)}
          >
            Email Addresses
          </h2>
          <fieldset id="email">
            <gr-email-editor
              id="emailEditor"
              @has-unsaved-changes-changed=${(
                e: ValueChangedEvent<boolean>
              ) => {
                this.emailsChanged = e.detail.value;
              }}
            ></gr-email-editor>
            <gr-button
              @click=${async () => {
                await this.emailEditor.save();
              }}
              ?disabled=${!this.emailsChanged}
              >Save changes</gr-button
            >
          </fieldset>
          <fieldset id="newEmail">
            <section>
              <span class="title">New email address</span>
              <span class="value">
                <iron-input
                  class="newEmailInput"
                  .bindValue=${this.newEmail}
                  @bind-value-changed=${(e: BindValueChangeEvent) => {
                    this.newEmail = e.detail.value;
                  }}
                  @keydown=${this.handleNewEmailKeydown}
                >
                  <input
                    class="newEmailInput"
                    type="text"
                    ?disabled=${this.addingEmail}
                    @keydown=${this.handleNewEmailKeydown}
                    placeholder="email@example.com"
                  />
                </iron-input>
              </span>
            </section>
            <section
              id="verificationSentMessage"
              ?hidden=${!this.lastSentVerificationEmail}
            >
              <p>
                A verification email was sent to
                <em>${this.lastSentVerificationEmail}</em>. Please check your
                inbox.
              </p>
            </section>
            <gr-button
              ?disabled=${!this.computeAddEmailButtonEnabled()}
              @click=${this.handleAddEmailButton}
              >Send verification</gr-button
            >
          </fieldset>
          ${when(
            this.showHttpAuth(),
            () => html` <div>
              <h2 id="HTTPCredentials">HTTP Credentials</h2>
              <fieldset>
                <gr-http-password id="httpPass"></gr-http-password>
              </fieldset>
            </div>`
          )}
          ${when(
            this.serverConfig?.sshd,
            () => html`<h2
                id="SSHKeys"
                class=${this.computeHeaderClass(this.keysChanged)}
              >
                SSH keys
              </h2>
              <gr-ssh-editor
                id="sshEditor"
                ?hasUnsavedChanges=${this.keysChanged}
                @has-unsaved-changes-changed=${(
                  e: ValueChangedEvent<boolean>
                ) => {
                  this.keysChanged = e.detail.value;
                }}
              ></gr-ssh-editor>`
          )}
          ${when(
            this.serverConfig?.receive?.enable_signed_push,
            () => html`<div>
              <h2
                id="GPGKeys"
                class=${this.computeHeaderClass(this.gpgKeysChanged)}
              >
                GPG keys
              </h2>
              <gr-gpg-editor
                id="gpgEditor"
                ?hasUnsavedChanges=${this.gpgKeysChanged}
                @has-unsaved-changes-changed=${(
                  e: ValueChangedEvent<boolean>
                ) => {
                  this.gpgKeysChanged = e.detail.value;
                }}
              ></gr-gpg-editor>
            </div>`
          )}
          <h2 id="Groups">Groups</h2>
          <fieldset>
            <gr-group-list id="groupList"></gr-group-list>
          </fieldset>
          <h2 id="Identities">Identities</h2>
          <fieldset>
            <gr-identities
              id="identities"
              .serverConfig=${this.serverConfig}
            ></gr-identities>
          </fieldset>
          ${when(
            this.serverConfig?.auth.use_contributor_agreements,
            () => html`<h2 id="Agreements">Agreements</h2>
              <fieldset>
                <gr-agreements-list id="agreementsList"></gr-agreements-list>
              </fieldset>`
          )}
          <gr-endpoint-decorator name="settings-screen">
          </gr-endpoint-decorator>
        </div>
      </div>`;
  }

  override disconnectedCallback() {
    document.removeEventListener('location-change', this.handleLocationChange);
    super.disconnectedCallback();
  }

  private readonly handleLocationChange = () => {
    // Handle anchor tag after dom attached
    const urlHash = window.location.hash;
    if (urlHash) {
      // Use shadowRoot for Polymer 2
      const elem = (this.shadowRoot || document).querySelector(urlHash);
      if (elem) {
        setTimeout(() => elem.scrollIntoView(), 0);
      }
    }
  };

  reloadAccountDetail() {
    Promise.all([this.accountInfo.loadData()]);
  }

  // private but used in test
  async handleSaveChangeTable() {
    this.prefs.change_table = this.localChangeTableColumns;
    this.prefs.legacycid_in_change_table = this.showNumber;
    await this.getUserModel().updatePreferences(this.prefs);
    this.changeTableChanged = false;
  }

  private computeHeaderClass(changed?: boolean) {
    return changed ? 'edited' : '';
  }

  // private but used in test
  handleNewEmailKeydown(e: KeyboardEvent) {
    if (e.key === 'Enter') {
      e.stopPropagation();
      this.handleAddEmailButton();
    }
  }

  // private but used in test
  isNewEmailValid(newEmail?: string): newEmail is string {
    return !!newEmail && newEmail.includes('@');
  }

  // private but used in test
  computeAddEmailButtonEnabled() {
    return this.isNewEmailValid(this.newEmail) && !this.addingEmail;
  }

  // private but used in test
  handleAddEmailButton() {
    if (!this.isNewEmailValid(this.newEmail)) return;

    this.addingEmail = true;
    this.restApiService.addAccountEmail(this.newEmail).then(async response => {
      this.addingEmail = false;

      // If it was unsuccessful.
      if (response.status < 200 || response.status >= 300) {
        return;
      }

      this.lastSentVerificationEmail = this.newEmail;
      this.newEmail = '';

      await this.getUserModel().loadEmails(true);
    });
  }

  private confirmDeleteAccount() {
    this.deleteAccountConfirmationDialog?.showModal();
  }

  private async deleteAccount() {
    this.isDeletingAccount = true;
    await this.accountInfo.delete();
    this.isDeletingAccount = false;
    this.deleteAccountConfirmationDialog?.close();
    this.getNavigation().setUrl(rootUrl());
  }

  private async dumpAccountState() {
    await this.getAccountState();
    this.dumpAccountStateConfirmationDialog?.showModal();
  }

  // private but used in test
  showHttpAuth() {
    if (this.serverConfig?.auth?.git_basic_auth_policy) {
      return HTTP_AUTH.includes(
        this.serverConfig.auth.git_basic_auth_policy.toUpperCase()
      );
    }

    return false;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-settings-view': GrSettingsView;
  }
}
