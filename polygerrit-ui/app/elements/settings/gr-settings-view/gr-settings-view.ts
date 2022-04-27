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
import '@polymer/paper-toggle-button/paper-toggle-button';
import '../../../styles/gr-font-styles';
import '../../../styles/gr-form-styles';
import '../../../styles/gr-menu-page-styles';
import '../../../styles/gr-page-nav-styles';
import '../../../styles/gr-paper-styles';
import '../../../styles/shared-styles';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../gr-change-table-editor/gr-change-table-editor';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-diff-preferences/gr-diff-preferences';
import '../../shared/gr-page-nav/gr-page-nav';
import '../../shared/gr-select/gr-select';
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
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-settings-view_html';
import {getDocsBaseUrl} from '../../../utils/url-util';
import {customElement, property, observe} from '@polymer/decorators';
import {AppElementParams} from '../../gr-app-types';
import {GrAccountInfo} from '../gr-account-info/gr-account-info';
import {GrWatchedProjectsEditor} from '../gr-watched-projects-editor/gr-watched-projects-editor';
import {GrGroupList} from '../gr-group-list/gr-group-list';
import {GrIdentities} from '../gr-identities/gr-identities';
import {GrDiffPreferences} from '../../shared/gr-diff-preferences/gr-diff-preferences';
import {PreferencesInput, ServerInfo} from '../../../types/common';
import {GrSshEditor} from '../gr-ssh-editor/gr-ssh-editor';
import {GrGpgEditor} from '../gr-gpg-editor/gr-gpg-editor';
import {GrEmailEditor} from '../gr-email-editor/gr-email-editor';
import {fireAlert, fireTitleChange} from '../../../utils/event-util';
import {getAppContext} from '../../../services/app-context';
import {GerritView} from '../../../services/router/router-model';
import {
  DateFormat,
  DefaultBase,
  DiffViewMode,
  EmailFormat,
  EmailStrategy,
  TimeFormat,
} from '../../../constants/constants';
import {columnNames} from '../../change-list/gr-change-list/gr-change-list';
import {windowLocationReload} from '../../../utils/dom-util';
import {ValueChangedEvent} from '../../../types/events';
import {css, html} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';
import {paperStyles} from '../../../styles/gr-paper-styles';
import {fontStyles} from '../../../styles/gr-font-styles';

const PREFS_SECTION_FIELDS: Array<keyof PreferencesInput> = [
  'changes_per_page',
  'date_format',
  'time_format',
  'email_strategy',
  'diff_view',
  'publish_comments_on_push',
  'disable_keyboard_shortcuts',
  'disable_token_highlighting',
  'work_in_progress_by_default',
  'default_base_for_merges',
  'signed_off_by',
  'email_format',
  'size_bar_in_change_table',
  'relative_date_in_change_table',
];

const GERRIT_DOCS_BASE_URL =
  'https://gerrit-review.googlesource.com/' + 'Documentation';
const GERRIT_DOCS_FILTER_PATH = '/user-notify.html';
const ABSOLUTE_URL_PATTERN = /^https?:/;
const TRAILING_SLASH_PATTERN = /\/$/;

const HTTP_AUTH = ['HTTP', 'HTTP_LDAP'];

enum CopyPrefsDirection {
  PrefsToLocalPrefs,
  LocalPrefsToPrefs,
}

export interface GrSettingsView {
  $: {
    accountInfo: GrAccountInfo;
    watchedProjectsEditor: GrWatchedProjectsEditor;
    groupList: GrGroupList;
    identities: GrIdentities;
    diffPrefs: GrDiffPreferences;
    sshEditor: GrSshEditor;
    gpgEditor: GrGpgEditor;
    emailEditor: GrEmailEditor;
    insertSignedOff: HTMLInputElement;
    workInProgressByDefault: HTMLInputElement;
    showSizeBarsInFileList: HTMLInputElement;
    publishCommentsOnPush: HTMLInputElement;
    disableKeyboardShortcuts: HTMLInputElement;
    disableTokenHighlighting: HTMLInputElement;
    relativeDateInChangeTable: HTMLInputElement;
    changesPerPageSelect: HTMLInputElement;
    dateTimeFormatSelect: HTMLInputElement;
    timeFormatSelect: HTMLInputElement;
    emailNotificationsSelect: HTMLInputElement;
    emailFormatSelect: HTMLInputElement;
    defaultBaseForMergesSelect: HTMLInputElement;
    diffViewSelect: HTMLInputElement;
  };
}

@customElement('gr-settings-view')
export class GrSettingsView extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the title of the page should change.
   *
   * @event title-change
   */

  /**
   * Fired with email confirmation text, or when the page reloads.
   *
   * @event show-alert
   */

  @property({type: Object})
  prefs: PreferencesInput = {};

  @property({type: Object})
  params?: AppElementParams;

  @property({type: Boolean})
  _accountInfoChanged = false;

  @property({type: Object})
  _localPrefs: PreferencesInput = {};

  @property({type: Array})
  _localChangeTableColumns: string[] = [];

  @property({type: Boolean})
  _loading = true;

  @property({type: Boolean})
  _changeTableChanged = false;

  @property({type: Boolean})
  _prefsChanged = false;

  @property({type: Boolean})
  _diffPrefsChanged = false;

  @property({type: Boolean})
  _watchedProjectsChanged = false;

  @property({type: Boolean})
  _keysChanged = false;

  @property({type: Boolean})
  _gpgKeysChanged = false;

  @property({type: String})
  _newEmail?: string;

  @property({type: Boolean})
  _addingEmail = false;

  @property({type: String})
  _lastSentVerificationEmail?: string | null = null;

  @property({type: Object})
  _serverConfig?: ServerInfo;

  @property({type: String})
  _docsBaseUrl?: string | null;

  @property({type: Boolean})
  _emailsChanged = false;

  @property({type: Boolean})
  _showNumber?: boolean;

  @property({type: Boolean})
  _isDark = false;

  public _testOnly_loadingPromise?: Promise<void>;

  private readonly restApiService = getAppContext().restApiService;

  override connectedCallback() {
    super.connectedCallback();
    // Polymer 2: anchor tag won't work on shadow DOM
    // we need to manually calling scrollIntoView when hash changed
    window.addEventListener('location-change', this.handleLocationChange);
    fireTitleChange(this, 'Settings');

    this._isDark = !!window.localStorage.getItem('dark-theme');

    const promises: Array<Promise<unknown>> = [
      this.$.accountInfo.loadData(),
      this.$.watchedProjectsEditor.loadData(),
      this.$.groupList.loadData(),
      this.$.identities.loadData(),
    ];

    // TODO(dhruvsri): move this to the service
    promises.push(
      this.restApiService.getPreferences().then(prefs => {
        if (!prefs) {
          throw new Error('getPreferences returned undefined');
        }
        this.prefs = prefs;
        this._showNumber = !!prefs.legacycid_in_change_table;
        this._copyPrefs(CopyPrefsDirection.PrefsToLocalPrefs);
        this._localChangeTableColumns =
          prefs.change_table.length === 0
            ? columnNames
            : prefs.change_table.map(column =>
                column === 'Project' ? 'Repo' : column
              );
      })
    );

    promises.push(
      this.restApiService.getConfig().then(config => {
        this._serverConfig = config;
        const configPromises: Array<Promise<void>> = [];

        if (this._serverConfig && this._serverConfig.sshd) {
          configPromises.push(this.$.sshEditor.loadData());
        }

        if (
          this._serverConfig &&
          this._serverConfig.receive &&
          this._serverConfig.receive.enable_signed_push
        ) {
          configPromises.push(this.$.gpgEditor.loadData());
        }

        configPromises.push(
          getDocsBaseUrl(config, this.restApiService).then(baseUrl => {
            this._docsBaseUrl = baseUrl;
          })
        );

        return Promise.all(configPromises);
      })
    );

    if (
      this.params &&
      this.params.view === GerritView.SETTINGS &&
      this.params.emailToken
    ) {
      promises.push(
        this.restApiService
          .confirmEmail(this.params.emailToken)
          .then(message => {
            if (message) {
              fireAlert(this, message);
            }
            this.$.emailEditor.loadData();
          })
      );
    } else {
      promises.push(this.$.emailEditor.loadData());
    }

    this._testOnly_loadingPromise = Promise.all(promises).then(() => {
      this._loading = false;

      // Handle anchor tag for initial load
      this.handleLocationChange();
    });
  }

  static styles = [
    sharedStyles,
    paperStyles,
    fontStyles,
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
      .main section.darkToggle {
        display: block;
      }
      .filters p,
      .darkToggle p {
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
    `,
  ];

  render() {
    return html`div class="loading" hidden$="[[!_loading]]">Loading...</div>
    <div hidden$="[[_loading]]" hidden="">
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
          <template is="dom-if" if="[[_showHttpAuth(_serverConfig)]]">
            <li><a href="#HTTPCredentials">HTTP Credentials</a></li>
          </template>
          <li hidden$="[[!_serverConfig.sshd]]">
            <a href="#SSHKeys"> SSH Keys </a>
          </li>
          <li hidden$="[[!_serverConfig.receive.enable_signed_push]]">
            <a href="#GPGKeys"> GPG Keys </a>
          </li>
          <li><a href="#Groups">Groups</a></li>
          <li><a href="#Identities">Identities</a></li>
          <template
            is="dom-if"
            if="[[_serverConfig.auth.use_contributor_agreements]]"
          >
            <li>
              <a href="#Agreements">Agreements</a>
            </li>
          </template>
          <li><a href="#MailFilters">Mail Filters</a></li>
          <gr-endpoint-decorator name="settings-menu-item">
          </gr-endpoint-decorator>
        </ul>
      </gr-page-nav>
      <div class="main gr-form-styles">
        <h1 class="heading-1">User Settings</h1>
        <h2 id="Theme">Theme</h2>
        <section class="darkToggle">
          <div class="toggle">
            <paper-toggle-button
              aria-labelledby="darkThemeToggleLabel"
              checked="[[_isDark]]"
              on-change="_handleToggleDark"
              on-click="_onTapDarkToggle"
            ></paper-toggle-button>
            <div id="darkThemeToggleLabel">
              Dark theme (the toggle reloads the page)
            </div>
          </div>
        </section>
        <h2 id="Profile" class$="[[_computeHeaderClass(_accountInfoChanged)]]">
          Profile
        </h2>
        <fieldset id="profile">
          <gr-account-info
            id="accountInfo"
            has-unsaved-changes="[[_accountInfoChanged]]"
            on-unsaved-changes-changed="_handleAccountInfoUnsavedChanges"
          ></gr-account-info>
          <gr-button
            on-click="_handleSaveAccountInfo"
            disabled="[[!_accountInfoChanged]]"
            >Save changes</gr-button
          >
        </fieldset>
        <h2 id="Preferences" class$="[[_computeHeaderClass(_prefsChanged)]]">
          Preferences
        </h2>
        <fieldset id="preferences">
          <section>
            <label class="title" for="changesPerPageSelect"
              >Changes per page</label
            >
            <span class="value">
              <gr-select
                bind-value="[[_convertToString(_localPrefs.changes_per_page)]]"
                on-change="_handleChangesPerPage"
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
          <section>
            <label class="title" for="dateTimeFormatSelect"
              >Date/time format</label
            >
            <span class="value">
              <gr-select
                bind-value="[[_convertToString(_localPrefs.date_format)]]"
                on-change="_handleDateFormat"
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
                bind-value="[[_convertToString(_localPrefs.time_format)]]"
                aria-label="Time Format"
                on-change="_handleTimeFormat"
              >
                <select id="timeFormatSelect">
                  <option value="HHMM_12">4:10 PM</option>
                  <option value="HHMM_24">16:10</option>
                </select>
              </gr-select>
            </span>
          </section>
          <section>
            <label class="title" for="emailNotificationsSelect"
              >Email notifications</label
            >
            <span class="value">
              <gr-select
                bind-value="[[_convertToString(_localPrefs.email_strategy)]]"
                on-change="_handleEmailStrategy"
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
          <section hidden$="[[!_convertToString(_localPrefs.email_format)]]">
            <label class="title" for="emailFormatSelect">Email format</label>
            <span class="value">
              <gr-select
                bind-value="[[_convertToString(_localPrefs.email_format)]]"
                on-change="_handleEmailFormat"
              >
                <select id="emailFormatSelect">
                  <option value="HTML_PLAINTEXT">HTML and plaintext</option>
                  <option value="PLAINTEXT">Plaintext only</option>
                </select>
              </gr-select>
            </span>
          </section>
          <section hidden$="[[!_localPrefs.default_base_for_merges]]">
            <span class="title">Default Base For Merges</span>
            <span class="value">
              <gr-select
                bind-value="[[_convertToString(_localPrefs.default_base_for_merges)]]"
                on-change="_handleDefaultBaseForMerges"
              >
                <select id="defaultBaseForMergesSelect">
                  <option value="AUTO_MERGE">Auto Merge</option>
                  <option value="FIRST_PARENT">First Parent</option>
                </select>
              </gr-select>
            </span>
          </section>
          <section>
            <label class="title" for="relativeDateInChangeTable"
              >Show Relative Dates In Changes Table</label
            >
            <span class="value">
              <input
                id="relativeDateInChangeTable"
                type="checkbox"
                checked$="[[_localPrefs.relative_date_in_change_table]]"
                on-change="_handleRelativeDateInChangeTable"
              />
            </span>
          </section>
          <section>
            <span class="title">Diff view</span>
            <span class="value">
              <gr-select
                bind-value="[[_convertToString(_localPrefs.diff_view)]]"
                on-change="_handleDiffView"
              >
                <select id="diffViewSelect">
                  <option value="SIDE_BY_SIDE">Side by side</option>
                  <option value="UNIFIED_DIFF">Unified diff</option>
                </select>
              </gr-select>
            </span>
          </section>
          <section>
            <label for="showSizeBarsInFileList" class="title"
              >Show size bars in file list</label
            >
            <span class="value">
              <input
                id="showSizeBarsInFileList"
                type="checkbox"
                checked$="[[_localPrefs.size_bar_in_change_table]]"
                on-change="_handleShowSizeBarsInFileListChanged"
              />
            </span>
          </section>
          <section>
            <label for="publishCommentsOnPush" class="title"
              >Publish comments on push</label
            >
            <span class="value">
              <input
                id="publishCommentsOnPush"
                type="checkbox"
                checked$="[[_localPrefs.publish_comments_on_push]]"
                on-change="_handlePublishCommentsOnPushChanged"
              />
            </span>
          </section>
          <section>
            <label for="workInProgressByDefault" class="title"
              >Set new changes to "work in progress" by default</label
            >
            <span class="value">
              <input
                id="workInProgressByDefault"
                type="checkbox"
                checked$="[[_localPrefs.work_in_progress_by_default]]"
                on-change="_handleWorkInProgressByDefault"
              />
            </span>
          </section>
          <section>
            <label for="disableKeyboardShortcuts" class="title"
              >Disable all keyboard shortcuts</label
            >
            <span class="value">
              <input
                id="disableKeyboardShortcuts"
                type="checkbox"
                checked$="[[_localPrefs.disable_keyboard_shortcuts]]"
                on-change="_handleDisableKeyboardShortcutsChanged"
              />
            </span>
          </section>
          <section>
            <label for="disableTokenHighlighting" class="title"
              >Disable token highlighting on hover</label
            >
            <span class="value">
              <input
                id="disableTokenHighlighting"
                type="checkbox"
                checked$="[[_localPrefs.disable_token_highlighting]]"
                on-change="_handleDisableTokenHighlightingChanged"
              />
            </span>
          </section>
          <section>
            <label for="insertSignedOff" class="title">
              Insert Signed-off-by Footer For Inline Edit Changes
            </label>
            <span class="value">
              <input
                id="insertSignedOff"
                type="checkbox"
                checked$="[[_localPrefs.signed_off_by]]"
                on-change="_handleInsertSignedOff"
              />
            </span>
          </section>
          <gr-button
            id="savePrefs"
            on-click="_handleSavePreferences"
            disabled="[[!_prefsChanged]]"
            >Save changes</gr-button
          >
        </fieldset>
        <h2
          id="DiffPreferences"
          class$="[[_computeHeaderClass(_diffPrefsChanged)]]"
        >
          Diff Preferences
        </h2>
        <fieldset id="diffPreferences">
          <gr-diff-preferences
            id="diffPrefs"
            on-has-unsaved-changes-changed="_handleHasUnsavedChangesChanged"
          ></gr-diff-preferences>
          <gr-button
            id="saveDiffPrefs"
            on-click="_handleSaveDiffPreferences"
            disabled$="[[!_diffPrefsChanged]]"
            >Save changes</gr-button
          >
        </fieldset>
        <gr-edit-preferences id="editPrefs"></gr-edit-preferences>
        <gr-menu-editor></gr-menu-editor>
        <h2
          id="ChangeTableColumns"
          class$="[[_computeHeaderClass(_changeTableChanged)]]"
        >
          Change Table Columns
        </h2>
        <fieldset id="changeTableColumns">
          <gr-change-table-editor
            show-number="[[_showNumber]]"
            on-show-number-changed="_handleShowNumberChanged"
            server-config="[[_serverConfig]]"
            displayed-columns="[[_localChangeTableColumns]]"
            on-displayed-columns-changed="_handleDisplayedColumnsChanged"
          >
          </gr-change-table-editor>
          <gr-button
            id="saveChangeTable"
            on-click="_handleSaveChangeTable"
            disabled="[[!_changeTableChanged]]"
            >Save changes</gr-button
          >
        </fieldset>
        <h2
          id="Notifications"
          class$="[[_computeHeaderClass(_watchedProjectsChanged)]]"
        >
          Notifications
        </h2>
        <fieldset id="watchedProjects">
          <gr-watched-projects-editor
            has-unsaved-changes="[[_watchedProjectsChanged]]"
            on-has-unsaved-changes-changed="_handleHasProjectsChanged"
            id="watchedProjectsEditor"
          ></gr-watched-projects-editor>
          <gr-button
            on-click="_handleSaveWatchedProjects"
            disabled$="[[!_watchedProjectsChanged]]"
            id="_handleSaveWatchedProjects"
            >Save changes</gr-button
          >
        </fieldset>
        <h2 id="EmailAddresses" class$="[[_computeHeaderClass(_emailsChanged)]]">
          Email Addresses
        </h2>
        <fieldset id="email">
          <gr-email-editor
            id="emailEditor"
            has-unsaved-changes="[[_emailsChanged]]"
            on-has-unsaved-changes-changed="_handleHasEmailsChanged"
          ></gr-email-editor>
          <gr-button on-click="_handleSaveEmails" disabled$="[[!_emailsChanged]]"
            >Save changes</gr-button
          >
        </fieldset>
        <fieldset id="newEmail">
          <section>
            <span class="title">New email address</span>
            <span class="value">
              <iron-input
                class="newEmailInput"
                bind-value="{{_newEmail}}"
                type="text"
                on-keydown="_handleNewEmailKeydown"
                placeholder="email@example.com"
              >
                <input
                  class="newEmailInput"
                  type="text"
                  disabled="[[_addingEmail]]"
                  on-keydown="_handleNewEmailKeydown"
                  placeholder="email@example.com"
                />
              </iron-input>
            </span>
          </section>
          <section
            id="verificationSentMessage"
            hidden$="[[!_lastSentVerificationEmail]]"
          >
            <p>
              A verification email was sent to
              <em>[[_lastSentVerificationEmail]]</em>. Please check your inbox.
            </p>
          </section>
          <gr-button
            disabled="[[!_computeAddEmailButtonEnabled(_newEmail, _addingEmail)]]"
            on-click="_handleAddEmailButton"
            >Send verification</gr-button
          >
        </fieldset>
        <template is="dom-if" if="[[_showHttpAuth(_serverConfig)]]">
          <div>
            <h2 id="HTTPCredentials">HTTP Credentials</h2>
            <fieldset>
              <gr-http-password id="httpPass"></gr-http-password>
            </fieldset>
          </div>
        </template>
        <div hidden$="[[!_serverConfig.sshd]]">
          <h2 id="SSHKeys" class$="[[_computeHeaderClass(_keysChanged)]]">
            SSH keys
          </h2>
          <gr-ssh-editor
            id="sshEditor"
            has-unsaved-changes-changed="handleUnsavedChangesChanged"
          ></gr-ssh-editor>
        </div>
        <div hidden$="[[!_serverConfig.receive.enable_signed_push]]">
          <h2 id="GPGKeys" class$="[[_computeHeaderClass(_gpgKeysChanged)]]">
            GPG keys
          </h2>
          <gr-gpg-editor
            id="gpgEditor"
            has-unsaved-changes="[[_gpgKeysChanged]]"
            on-has-unsaved-changes-changed="_handleGpgEditorHasSavedChanges"
          ></gr-gpg-editor>
        </div>
        <h2 id="Groups">Groups</h2>
        <fieldset>
          <gr-group-list id="groupList"></gr-group-list>
        </fieldset>
        <h2 id="Identities">Identities</h2>
        <fieldset>
          <gr-identities
            id="identities"
            server-config="[[_serverConfig]]"
          ></gr-identities>
        </fieldset>
        <template
          is="dom-if"
          if="[[_serverConfig.auth.use_contributor_agreements]]"
        >
          <h2 id="Agreements">Agreements</h2>
          <fieldset>
            <gr-agreements-list id="agreementsList"></gr-agreements-list>
          </fieldset>
        </template>
        <h2 id="MailFilters">Mail Filters</h2>
        <fieldset class="filters">
          <p>
            Gerrit emails include metadata about the change to support writing
            mail filters.
          </p>
          <p>
            Here are some example Gmail queries that can be used for filters or
            for searching through archived messages. View the
            <a
              href$="[[_getFilterDocsLink(_docsBaseUrl)]]"
              target="_blank"
              rel="nofollow"
              >Gerrit documentation</a
            >
            for the complete set of footers.
          </p>
          <table>
            <tbody>
              <tr>
                <th>Name</th>
                <th>Query</th>
              </tr>
              <tr>
                <td>Changes requesting my review</td>
                <td>
                  <code class="queryExample">
                    "Gerrit-Reviewer: <em>Your Name</em>
                    &lt;<em>your.email@example.com</em>&gt;"
                  </code>
                </td>
              </tr>
              <tr>
                <td>Changes requesting my attention</td>
                <td>
                  <code class="queryExample">
                    "Gerrit-Attention: <em>Your Name</em>
                    &lt;<em>your.email@example.com</em>&gt;"
                  </code>
                </td>
              </tr>
              <tr>
                <td>Changes from a specific owner</td>
                <td>
                  <code class="queryExample">
                    "Gerrit-Owner: <em>Owner name</em>
                    &lt;<em>owner.email@example.com</em>&gt;"
                  </code>
                </td>
              </tr>
              <tr>
                <td>Changes targeting a specific branch</td>
                <td>
                  <code class="queryExample">
                    "Gerrit-Branch: <em>branch-name</em>"
                  </code>
                </td>
              </tr>
              <tr>
                <td>Changes in a specific project</td>
                <td>
                  <code class="queryExample">
                    "Gerrit-Project: <em>project-name</em>"
                  </code>
                </td>
              </tr>
              <tr>
                <td>Messages related to a specific Change ID</td>
                <td>
                  <code class="queryExample">
                    "Gerrit-Change-Id: <em>Change ID</em>"
                  </code>
                </td>
              </tr>
              <tr>
                <td>Messages related to a specific change number</td>
                <td>
                  <code class="queryExample">
                    "Gerrit-Change-Number: <em>change number</em>"
                  </code>
                </td>
              </tr>
            </tbody>
          </table>
        </fieldset>
        <gr-endpoint-decorator name="settings-screen"> </gr-endpoint-decorator>
      </div>
    </div>`;
  }

  override disconnectedCallback() {
    window.removeEventListener('location-change', this.handleLocationChange);
    super.disconnectedCallback();
  }

  handleUnsavedChangesChanged(e: ValueChangedEvent) {
    this._keysChanged = !!e.detail.value;
  }

  _handleGpgEditorHasSavedChanges(e: ValueChangedEvent<boolean>) {
    this._gpgKeysChanged = e.detail.value;
  }

  private readonly handleLocationChange = () => {
    // Handle anchor tag after dom attached
    const urlHash = window.location.hash;
    if (urlHash) {
      // Use shadowRoot for Polymer 2
      const elem = (this.shadowRoot || document).querySelector(urlHash);
      if (elem) {
        elem.scrollIntoView();
      }
    }
  };

  reloadAccountDetail() {
    Promise.all([this.$.accountInfo.loadData(), this.$.emailEditor.loadData()]);
  }

  _isLoading() {
    return this._loading || this._loading === undefined;
  }

  _copyPrefs(direction: CopyPrefsDirection) {
    let to;
    let from;
    if (direction === CopyPrefsDirection.LocalPrefsToPrefs) {
      from = this._localPrefs;
      to = 'prefs';
    } else {
      from = this.prefs;
      to = '_localPrefs';
    }
    for (let i = 0; i < PREFS_SECTION_FIELDS.length; i++) {
      this.set([to, PREFS_SECTION_FIELDS[i]], from[PREFS_SECTION_FIELDS[i]]);
    }
  }

  @observe('_localChangeTableColumns', '_showNumber')
  _handleChangeTableChanged() {
    if (this._isLoading()) {
      return;
    }
    this._changeTableChanged = true;
  }

  @observe('_localPrefs.*')
  _handlePrefsChanged() {
    if (this._isLoading()) {
      return;
    }
    this._prefsChanged = true;
  }

  _handleRelativeDateInChangeTable() {
    this.set(
      '_localPrefs.relative_date_in_change_table',
      this.$.relativeDateInChangeTable.checked
    );
  }

  _handleShowSizeBarsInFileListChanged() {
    this.set(
      '_localPrefs.size_bar_in_change_table',
      this.$.showSizeBarsInFileList.checked
    );
  }

  _handlePublishCommentsOnPushChanged() {
    this.set(
      '_localPrefs.publish_comments_on_push',
      this.$.publishCommentsOnPush.checked
    );
  }

  _handleDisableKeyboardShortcutsChanged() {
    this.set(
      '_localPrefs.disable_keyboard_shortcuts',
      this.$.disableKeyboardShortcuts.checked
    );
  }

  _handleDisableTokenHighlightingChanged() {
    this.set(
      '_localPrefs.disable_token_highlighting',
      this.$.disableTokenHighlighting.checked
    );
  }

  _handleWorkInProgressByDefault() {
    this.set(
      '_localPrefs.work_in_progress_by_default',
      this.$.workInProgressByDefault.checked
    );
  }

  _handleInsertSignedOff() {
    this.set('_localPrefs.signed_off_by', this.$.insertSignedOff.checked);
  }

  _handleSaveAccountInfo() {
    this.$.accountInfo.save();
  }

  _handleSavePreferences() {
    this._copyPrefs(CopyPrefsDirection.LocalPrefsToPrefs);

    return this.restApiService.savePreferences(this.prefs).then(() => {
      this._prefsChanged = false;
    });
  }

  _handleSaveChangeTable() {
    this.set('prefs.change_table', this._localChangeTableColumns);
    this.set('prefs.legacycid_in_change_table', this._showNumber);
    return this.restApiService.savePreferences(this.prefs).then(() => {
      this._changeTableChanged = false;
    });
  }

  _handleSaveDiffPreferences() {
    this.$.diffPrefs.save();
  }

  _handleSaveWatchedProjects() {
    this.$.watchedProjectsEditor.save();
  }

  _computeHeaderClass(changed?: boolean) {
    return changed ? 'edited' : '';
  }

  _handleSaveEmails() {
    this.$.emailEditor.save();
  }

  _handleNewEmailKeydown(e: KeyboardEvent) {
    if (e.keyCode === 13) {
      // Enter
      e.stopPropagation();
      this._handleAddEmailButton();
    }
  }

  _isNewEmailValid(newEmail?: string): newEmail is string {
    return !!newEmail && newEmail.includes('@');
  }

  _computeAddEmailButtonEnabled(newEmail?: string, addingEmail?: boolean) {
    return this._isNewEmailValid(newEmail) && !addingEmail;
  }

  _handleAddEmailButton() {
    if (!this._isNewEmailValid(this._newEmail)) return;

    this._addingEmail = true;
    this.restApiService.addAccountEmail(this._newEmail).then(response => {
      this._addingEmail = false;

      // If it was unsuccessful.
      if (response.status < 200 || response.status >= 300) {
        return;
      }

      this._lastSentVerificationEmail = this._newEmail;
      this._newEmail = '';
    });
  }

  _handleAccountInfoUnsavedChanges(e: ValueChangedEvent<boolean>) {
    this._accountInfoChanged = e.detail.value;
  }

  _handleShowNumberChanged(e: ValueChangedEvent<boolean>) {
    this._showNumber = e.detail.value;
  }

  _handleDisplayedColumnsChanged(e: ValueChangedEvent<string[]>) {
    this._localChangeTableColumns = e.detail.value;
  }

  _handleHasEmailsChanged(e: ValueChangedEvent<boolean>) {
    this._emailsChanged = e.detail.value;
  }

  _handleHasProjectsChanged(e: ValueChangedEvent<boolean>) {
    this._watchedProjectsChanged = e.detail.value;
  }

  _getFilterDocsLink(docsBaseUrl?: string | null) {
    let base = docsBaseUrl;
    if (!base || !ABSOLUTE_URL_PATTERN.test(base)) {
      base = GERRIT_DOCS_BASE_URL;
    }

    // Remove any trailing slash, since it is in the GERRIT_DOCS_FILTER_PATH.
    base = base.replace(TRAILING_SLASH_PATTERN, '');

    return base + GERRIT_DOCS_FILTER_PATH;
  }

  _handleToggleDark() {
    if (this._isDark) {
      window.localStorage.removeItem('dark-theme');
    } else {
      window.localStorage.setItem('dark-theme', 'true');
    }
    this.reloadPage();
  }

  reloadPage() {
    windowLocationReload();
  }

  _showHttpAuth(config?: ServerInfo) {
    if (config && config.auth && config.auth.git_basic_auth_policy) {
      return HTTP_AUTH.includes(
        config.auth.git_basic_auth_policy.toUpperCase()
      );
    }

    return false;
  }

  /**
   * Work around a issue on iOS when clicking turns into double tap
   */
  _onTapDarkToggle(e: Event) {
    e.preventDefault();
  }

  _handleChangesPerPage() {
    this.set(
      '_localPrefs.changes_per_page',
      Number(this.$.changesPerPageSelect.value)
    );
  }

  _handleDateFormat() {
    this.set('_localPrefs.date_format', this.$.dateTimeFormatSelect.value);
  }

  _handleTimeFormat() {
    this.set('_localPrefs.time_format', this.$.timeFormatSelect.value);
  }

  _handleEmailStrategy() {
    this.set(
      '_localPrefs.email_strategy',
      this.$.emailNotificationsSelect.value
    );
  }

  _handleEmailFormat() {
    this.set('_localPrefs.email_format', this.$.emailFormatSelect.value);
  }

  _handleDefaultBaseForMerges() {
    this.set(
      '_localPrefs.default_base_for_merges',
      this.$.defaultBaseForMergesSelect.value
    );
  }

  _handleDiffView() {
    this.set(
      '_localPrefs.diff_view',
      this.$.diffViewSelect.value as DiffViewMode
    );
  }

  /**
   * bind-value has type string so we have to convert anything inputed
   * to string.
   *
   * This is so typescript template checker doesn't fail.
   */
  _convertToString(
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

  _handleHasUnsavedChangesChanged(e: ValueChangedEvent<boolean>) {
    this._diffPrefsChanged = e.detail.value;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-settings-view': GrSettingsView;
  }
}
