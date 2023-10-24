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
import {fireAlert, fireTitleChange} from '../../../utils/event-util';
import {getAppContext} from '../../../services/app-context';
import {
  DateFormat,
  DefaultBase,
  DiffViewMode,
  EmailFormat,
  EmailStrategy,
  AppTheme,
  TimeFormat,
} from '../../../constants/constants';
import {BindValueChangeEvent, ValueChangedEvent} from '../../../types/events';
import {LitElement, css, html, nothing} from 'lit';
import {customElement, query, queryAsync, state} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {paperStyles} from '../../../styles/gr-paper-styles';
import {fontStyles} from '../../../styles/gr-font-styles';
import {when} from 'lit/directives/when.js';
import {pageNavStyles} from '../../../styles/gr-page-nav-styles';
import {menuPageStyles} from '../../../styles/gr-menu-page-styles';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {KnownExperimentId} from '../../../services/flags/flags';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {settingsViewModelToken} from '../../../models/views/settings';
import {areNotificationsEnabled} from '../../../utils/worker-util';
import {
  changeTablePrefs,
  userModelToken,
} from '../../../models/user/user-model';
import {modalStyles} from '../../../styles/gr-modal-styles';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {getDocUrl, rootUrl} from '../../../utils/url-util';
import {configModelToken} from '../../../models/config/config-model';

const HTTP_AUTH = ['HTTP', 'HTTP_LDAP'];

enum CopyPrefsDirection {
  PrefsToLocalPrefs,
  LocalPrefsToPrefs,
}

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

  @query('#watchedProjectsEditor', true)
  watchedProjectsEditor!: GrWatchedProjectsEditor;

  @query('#groupList', true) groupList!: GrGroupList;

  @query('#identities', true) identities!: GrIdentities;

  @query('#diffPrefs') diffPrefs!: GrDiffPreferences;

  @queryAsync('#sshEditor') sshEditorPromise!: Promise<GrSshEditor>;

  @queryAsync('#gpgEditor') gpgEditorPromise!: Promise<GrGpgEditor>;

  @query('#emailEditor', true) emailEditor!: GrEmailEditor;

  @query('#insertSignedOff') insertSignedOff!: HTMLInputElement;

  @query('#workInProgressByDefault') workInProgressByDefault!: HTMLInputElement;

  @query('#showSizeBarsInFileList') showSizeBarsInFileList!: HTMLInputElement;

  @query('#publishCommentsOnPush') publishCommentsOnPush!: HTMLInputElement;

  @query('#allowBrowserNotifications')
  allowBrowserNotifications?: HTMLInputElement;

  @query('#disableKeyboardShortcuts')
  disableKeyboardShortcuts!: HTMLInputElement;

  @query('#disableTokenHighlighting')
  disableTokenHighlighting!: HTMLInputElement;

  @query('#relativeDateInChangeTable')
  relativeDateInChangeTable!: HTMLInputElement;

  @query('#changesPerPageSelect') changesPerPageSelect!: HTMLInputElement;

  @query('#dateTimeFormatSelect') dateTimeFormatSelect!: HTMLInputElement;

  @query('#timeFormatSelect') timeFormatSelect!: HTMLInputElement;

  @query('#emailNotificationsSelect')
  emailNotificationsSelect!: HTMLInputElement;

  @query('#emailFormatSelect') emailFormatSelect!: HTMLInputElement;

  @query('#defaultBaseForMergesSelect')
  defaultBaseForMergesSelect!: HTMLInputElement;

  @query('#diffViewSelect') diffViewSelect!: HTMLInputElement;

  @query('#themeSelect') themeSelect!: HTMLInputElement;

  @state() prefs: PreferencesInput = {};

  @state() private accountInfoChanged = false;

  // private but used in test
  @state() localPrefs: PreferencesInput = {};

  // private but used in test
  @state() localChangeTableColumns: string[] = [];

  @state() private loading = true;

  @state() private changeTableChanged = false;

  // private but used in test
  @state() prefsChanged = false;

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

  @state() private docsBaseUrl = '';

  // private but used in test
  public _testOnly_loadingPromise?: Promise<void>;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getUserModel = resolve(this, userModelToken);

  // private but used in test
  readonly flagsService = getAppContext().flagsService;

  private readonly getViewModel = resolve(this, settingsViewModelToken);

  private readonly getNavigation = resolve(this, navigationToken);

  private readonly getConfigModel = resolve(this, configModelToken);

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
        this.copyPrefs(CopyPrefsDirection.PrefsToLocalPrefs);
        this.prefsChanged = false;
        this.localChangeTableColumns = changeTablePrefs(prefs);
      }
    );
    subscribe(
      this,
      () => this.getConfigModel().docsBaseUrl$,
      docsBaseUrl => (this.docsBaseUrl = docsBaseUrl)
    );
  }

  // private, but used in tests
  async confirmEmail() {
    if (!this.emailToken) return;
    const message = await this.restApiService.confirmEmail(this.emailToken);
    if (message) fireAlert(this, message);
    this.getViewModel().clearToken();
    await this.emailEditor.loadData();
  }

  override connectedCallback() {
    super.connectedCallback();
    // Polymer 2: anchor tag won't work on shadow DOM
    // we need to manually calling scrollIntoView when hash changed
    document.addEventListener('location-change', this.handleLocationChange);
    fireTitleChange('Settings');
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

    promises.push(this.emailEditor.loadData());

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
        .delete-account-button {
          margin-left: var(--spacing-l);
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
            ></gr-account-info>
            <gr-button
              @click=${() => {
                this.accountInfo.save();
              }}
              ?disabled=${!this.accountInfoChanged}
              >Save changes</gr-button
            >
            <gr-button
              class="delete-account-button"
              @click=${() => {
                this.confirmDeleteAccount();
              }}
              >Delete Account</gr-button
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
          </fieldset>
          <h2
            id="Preferences"
            class=${this.computeHeaderClass(this.prefsChanged)}
          >
            Preferences
          </h2>
          <fieldset id="preferences">
            ${this.renderTheme()} ${this.renderChangesPerPages()}
            ${this.renderDateTimeFormat()} ${this.renderEmailNotification()}
            ${this.renderEmailFormat()} ${this.renderBrowserNotifications()}
            ${this.renderDefaultBaseForMerges()}
            ${this.renderRelativeDateInChangeTable()} ${this.renderDiffView()}
            ${this.renderShowSizeBarsInFileList()}
            ${this.renderPublishCommentsOnPush()}
            ${this.renderWorkInProgressByDefault()}
            ${this.renderDisableKeyboardShortcuts()}
            ${this.renderDisableTokenHighlighting()}
            ${this.renderInsertSignedOff()}
            <gr-button
              id="savePrefs"
              @click=${this.handleSavePreferences}
              ?disabled=${!this.prefsChanged}
              >Save changes</gr-button
            >
          </fieldset>
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
              ?hasUnsavedChanges=${this.watchedProjectsChanged}
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
              ?hasUnsavedChanges=${this.emailsChanged}
              @has-unsaved-changes-changed=${(
                e: ValueChangedEvent<boolean>
              ) => {
                this.emailsChanged = e.detail.value;
              }}
            ></gr-email-editor>
            <gr-button
              @click=${() => {
                this.emailEditor.save();
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

  private renderTheme() {
    return html`
      <section>
        <label class="title" for="themeSelect">Theme</label>
        <span class="value">
          <gr-select
            .bindValue=${this.localPrefs.theme ?? AppTheme.AUTO}
            @change=${() => {
              this.localPrefs.theme = this.themeSelect.value as AppTheme;
              this.prefsChanged = true;
            }}
          >
            <select id="themeSelect">
              <option value="AUTO">Auto (based on OS prefs)</option>
              <option value="LIGHT">Light</option>
              <option value="DARK">Dark</option>
            </select>
          </gr-select>
        </span>
      </section>
    `;
  }

  private renderChangesPerPages() {
    return html`
      <section>
        <label class="title" for="changesPerPageSelect">Changes per page</label>
        <span class="value">
          <gr-select
            .bindValue=${this.convertToString(this.localPrefs.changes_per_page)}
            @change=${() => {
              this.localPrefs.changes_per_page = Number(
                this.changesPerPageSelect.value
              ) as 10 | 25 | 50 | 100;
              this.prefsChanged = true;
            }}
          >
            <select id="changesPerPageSelect">
              <option value="10">10 rows per page</option>
              <option value="25">25 rows per page</option>
              <option value="50">50 rows per page</option>
              <option value="100">100 rows per page</option>
            </select>
          </gr-select>
        </span>
      </section>
    `;
  }

  private renderDateTimeFormat() {
    return html`
      <section>
        <label class="title" for="dateTimeFormatSelect">Date/time format</label>
        <span class="value">
          <gr-select
            .bindValue=${this.convertToString(this.localPrefs.date_format)}
            @change=${() => {
              this.localPrefs.date_format = this.dateTimeFormatSelect
                .value as DateFormat;
              this.prefsChanged = true;
            }}
          >
            <select id="dateTimeFormatSelect">
              <option value="STD">Jun 3 ; Jun 3, 2016</option>
              <option value="US">06/03 ; 06/03/16</option>
              <option value="ISO">06-03 ; 2016-06-03</option>
              <option value="EURO">3. Jun ; 03.06.2016</option>
              <option value="UK">03/06 ; 03/06/2016</option>
            </select>
          </gr-select>
          <gr-select
            .bindValue=${this.convertToString(this.localPrefs.time_format)}
            aria-label="Time Format"
            @change=${() => {
              this.localPrefs.time_format = this.timeFormatSelect
                .value as TimeFormat;
              this.prefsChanged = true;
            }}
          >
            <select id="timeFormatSelect">
              <option value="HHMM_12">4:10 PM</option>
              <option value="HHMM_24">16:10</option>
            </select>
          </gr-select>
        </span>
      </section>
    `;
  }

  private renderEmailNotification() {
    return html`
      <section>
        <label class="title" for="emailNotificationsSelect"
          >Email notifications</label
        >
        <span class="value">
          <gr-select
            .bindValue=${this.convertToString(this.localPrefs.email_strategy)}
            @change=${() => {
              this.localPrefs.email_strategy = this.emailNotificationsSelect
                .value as EmailStrategy;
              this.prefsChanged = true;
            }}
          >
            <select id="emailNotificationsSelect">
              <option value="CC_ON_OWN_COMMENTS">Every comment</option>
              <option value="ENABLED">Only comments left by others</option>
              <option value="ATTENTION_SET_ONLY">
                Only when I am in the attention set
              </option>
              <option value="DISABLED">None</option>
            </select>
          </gr-select>
        </span>
      </section>
    `;
  }

  private renderEmailFormat() {
    if (!this.localPrefs.email_format) return nothing;
    return html`
      <section>
        <label class="title" for="emailFormatSelect">Email format</label>
        <span class="value">
          <gr-select
            .bindValue=${this.convertToString(this.localPrefs.email_format)}
            @change=${() => {
              this.localPrefs.email_format = this.emailFormatSelect
                .value as EmailFormat;
              this.prefsChanged = true;
            }}
          >
            <select id="emailFormatSelect">
              <option value="HTML_PLAINTEXT">HTML and plaintext</option>
              <option value="PLAINTEXT">Plaintext only</option>
            </select>
          </gr-select>
        </span>
      </section>
    `;
  }

  private renderBrowserNotifications() {
    if (!this.flagsService.isEnabled(KnownExperimentId.PUSH_NOTIFICATIONS))
      return nothing;
    if (
      !this.flagsService.isEnabled(
        KnownExperimentId.PUSH_NOTIFICATIONS_DEVELOPER
      ) &&
      !areNotificationsEnabled(this.account)
    )
      return nothing;
    return html`
      <section id="allowBrowserNotificationsSection">
        <div class="title">
          <label for="allowBrowserNotifications"
            >Allow browser notifications</label
          >
          <a
            href=${getDocUrl(
              this.docsBaseUrl,
              'user-attention-set.html#_browser_notifications'
            )}
            target="_blank"
            rel="noopener noreferrer"
          >
            <gr-icon icon="help" title="read documentation"></gr-icon>
          </a>
        </div>
        <span class="value">
          <input
            id="allowBrowserNotifications"
            type="checkbox"
            ?checked=${this.localPrefs.allow_browser_notifications}
            @change=${() => {
              this.localPrefs.allow_browser_notifications =
                this.allowBrowserNotifications!.checked;
              this.prefsChanged = true;
            }}
          />
        </span>
      </section>
    `;
  }

  private renderDefaultBaseForMerges() {
    if (!this.localPrefs.default_base_for_merges) return nothing;
    return nothing;
    // TODO: Re-enable respecting the default_base_for_merges preference.
    // See corresponding TODO in change-model.
    // return html`
    //   <section>
    //     <span class="title">Default Base For Merges</span>
    //     <span class="value">
    //       <gr-select
    //         .bindValue=${this.convertToString(
    //           this.localPrefs.default_base_for_merges
    //         )}
    //         @change=${() => {
    //           this.localPrefs.default_base_for_merges = this
    //             .defaultBaseForMergesSelect.value as DefaultBase;
    //           this.prefsChanged = true;
    //         }}
    //       >
    //         <select id="defaultBaseForMergesSelect">
    //           <option value="AUTO_MERGE">Auto Merge</option>
    //           <option value="FIRST_PARENT">First Parent</option>
    //         </select>
    //       </gr-select>
    //     </span>
    //   </section>
    // `;
  }

  private renderRelativeDateInChangeTable() {
    return html`
      <section>
        <label class="title" for="relativeDateInChangeTable"
          >Show Relative Dates In Changes Table</label
        >
        <span class="value">
          <input
            id="relativeDateInChangeTable"
            type="checkbox"
            ?checked=${this.localPrefs.relative_date_in_change_table}
            @change=${() => {
              this.localPrefs.relative_date_in_change_table =
                this.relativeDateInChangeTable.checked;
              this.prefsChanged = true;
            }}
          />
        </span>
      </section>
    `;
  }

  private renderDiffView() {
    return html`
      <section>
        <span class="title">Diff view</span>
        <span class="value">
          <gr-select
            .bindValue=${this.convertToString(this.localPrefs.diff_view)}
            @change=${() => {
              this.localPrefs.diff_view = this.diffViewSelect
                .value as DiffViewMode;
              this.prefsChanged = true;
            }}
          >
            <select id="diffViewSelect">
              <option value="SIDE_BY_SIDE">Side by side</option>
              <option value="UNIFIED_DIFF">Unified diff</option>
            </select>
          </gr-select>
        </span>
      </section>
    `;
  }

  private renderShowSizeBarsInFileList() {
    return html`
      <section>
        <label for="showSizeBarsInFileList" class="title"
          >Show size bars in file list</label
        >
        <span class="value">
          <input
            id="showSizeBarsInFileList"
            type="checkbox"
            ?checked=${this.localPrefs.size_bar_in_change_table}
            @change=${() => {
              this.localPrefs.size_bar_in_change_table =
                this.showSizeBarsInFileList.checked;
              this.prefsChanged = true;
            }}
          />
        </span>
      </section>
    `;
  }

  private renderPublishCommentsOnPush() {
    return html`
      <section>
        <label for="publishCommentsOnPush" class="title"
          >Publish comments on push</label
        >
        <span class="value">
          <input
            id="publishCommentsOnPush"
            type="checkbox"
            ?checked=${this.localPrefs.publish_comments_on_push}
            @change=${() => {
              this.localPrefs.publish_comments_on_push =
                this.publishCommentsOnPush.checked;
              this.prefsChanged = true;
            }}
          />
        </span>
      </section>
    `;
  }

  private renderWorkInProgressByDefault() {
    return html`
      <section>
        <label for="workInProgressByDefault" class="title"
          >Set new changes to "work in progress" by default</label
        >
        <span class="value">
          <input
            id="workInProgressByDefault"
            type="checkbox"
            ?checked=${this.localPrefs.work_in_progress_by_default}
            @change=${() => {
              this.localPrefs.work_in_progress_by_default =
                this.workInProgressByDefault.checked;
              this.prefsChanged = true;
            }}
          />
        </span>
      </section>
    `;
  }

  private renderDisableKeyboardShortcuts() {
    return html`
      <section>
        <label for="disableKeyboardShortcuts" class="title"
          >Disable all keyboard shortcuts</label
        >
        <span class="value">
          <input
            id="disableKeyboardShortcuts"
            type="checkbox"
            ?checked=${this.localPrefs.disable_keyboard_shortcuts}
            @change=${() => {
              this.localPrefs.disable_keyboard_shortcuts =
                this.disableKeyboardShortcuts.checked;
              this.prefsChanged = true;
            }}
          />
        </span>
      </section>
    `;
  }

  private renderDisableTokenHighlighting() {
    return html`
      <section>
        <label for="disableTokenHighlighting" class="title"
          >Disable token highlighting on hover</label
        >
        <span class="value">
          <input
            id="disableTokenHighlighting"
            type="checkbox"
            ?checked=${this.localPrefs.disable_token_highlighting}
            @change=${() => {
              this.localPrefs.disable_token_highlighting =
                this.disableTokenHighlighting.checked;
              this.prefsChanged = true;
            }}
          />
        </span>
      </section>
    `;
  }

  private renderInsertSignedOff() {
    return html`
      <section>
        <label for="insertSignedOff" class="title">
          Insert Signed-off-by Footer For Inline Edit Changes
        </label>
        <span class="value">
          <input
            id="insertSignedOff"
            type="checkbox"
            ?checked=${this.localPrefs.signed_off_by}
            @change=${() => {
              this.localPrefs.signed_off_by = this.insertSignedOff.checked;
              this.prefsChanged = true;
            }}
          />
        </span>
      </section>
    `;
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
    Promise.all([this.accountInfo.loadData(), this.emailEditor.loadData()]);
  }

  private copyPrefs(direction: CopyPrefsDirection) {
    if (direction === CopyPrefsDirection.LocalPrefsToPrefs) {
      this.prefs = {
        ...this.localPrefs,
      };
    } else {
      this.localPrefs = {
        ...this.prefs,
      };
    }
  }

  // private but used in test
  handleSavePreferences() {
    return this.getUserModel().updatePreferences(this.localPrefs);
  }

  // private but used in test
  handleSaveChangeTable() {
    this.prefs.change_table = this.localChangeTableColumns;
    this.prefs.legacycid_in_change_table = this.showNumber;
    return this.restApiService.savePreferences(this.prefs).then(() => {
      this.changeTableChanged = false;
    });
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
    this.restApiService.addAccountEmail(this.newEmail).then(response => {
      this.addingEmail = false;

      // If it was unsuccessful.
      if (response.status < 200 || response.status >= 300) {
        return;
      }

      this.lastSentVerificationEmail = this.newEmail;
      this.newEmail = '';
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

  // private but used in test
  showHttpAuth() {
    if (this.serverConfig?.auth?.git_basic_auth_policy) {
      return HTTP_AUTH.includes(
        this.serverConfig.auth.git_basic_auth_policy.toUpperCase()
      );
    }

    return false;
  }

  /**
   * bind-value has type string so we have to convert anything inputed
   * to string.
   *
   * This is so typescript template checker doesn't fail.
   */
  private convertToString(
    key?:
      | DateFormat
      | DefaultBase
      | DiffViewMode
      | EmailFormat
      | EmailStrategy
      | TimeFormat
      | number
  ) {
    return key !== undefined ? String(key) : '';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-settings-view': GrSettingsView;
  }
}
