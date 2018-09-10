/**
@license
Copyright (C) 2016 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../behaviors/docs-url-behavior/docs-url-behavior.js';
import '../../../../@polymer/paper-toggle-button/paper-toggle-button.js';
import '../../../styles/gr-form-styles.js';
import '../../../styles/gr-menu-page-styles.js';
import '../../../styles/gr-page-nav-styles.js';
import '../../../styles/shared-styles.js';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator.js';
import '../gr-change-table-editor/gr-change-table-editor.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-date-formatter/gr-date-formatter.js';
import '../../shared/gr-page-nav/gr-page-nav.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../shared/gr-select/gr-select.js';
import '../gr-account-info/gr-account-info.js';
import '../gr-agreements-list/gr-agreements-list.js';
import '../gr-edit-preferences/gr-edit-preferences.js';
import '../gr-email-editor/gr-email-editor.js';
import '../gr-gpg-editor/gr-gpg-editor.js';
import '../gr-group-list/gr-group-list.js';
import '../gr-http-password/gr-http-password.js';
import '../gr-identities/gr-identities.js';
import '../gr-menu-editor/gr-menu-editor.js';
import '../gr-ssh-editor/gr-ssh-editor.js';
import '../gr-watched-projects-editor/gr-watched-projects-editor.js';
/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
(function(window) {
  'use strict';

  const util = window.util || {};

  util.parseDate = function(dateStr) {
    // Timestamps are given in UTC and have the format
    // "'yyyy-mm-dd hh:mm:ss.fffffffff'" where "'ffffffffff'" represents
    // nanoseconds.
    // Munge the date into an ISO 8061 format and parse that.
    return new Date(dateStr.replace(' ', 'T') + 'Z');
  };

  util.getCookie = function(name) {
    const key = name + '=';
    const cookies = document.cookie.split(';');
    for (let i = 0; i < cookies.length; i++) {
      let c = cookies[i];
      while (c.charAt(0) === ' ') {
        c = c.substring(1);
      }
      if (c.startsWith(key)) {
        return c.substring(key.length, c.length);
      }
    }
    return '';
  };
  window.util = util;
})(window);

const PREFS_SECTION_FIELDS = [
  'changes_per_page',
  'date_format',
  'time_format',
  'email_strategy',
  'diff_view',
  'publish_comments_on_push',
  'work_in_progress_by_default',
  'signed_off_by',
  'email_format',
  'size_bar_in_change_table',
];

const GERRIT_DOCS_BASE_URL = 'https://gerrit-review.googlesource.com/' +
    'Documentation';
const GERRIT_DOCS_FILTER_PATH = '/user-notify.html';
const ABSOLUTE_URL_PATTERN = /^https?:/;
const TRAILING_SLASH_PATTERN = /\/$/;

const RELOAD_MESSAGE = 'Reloading...';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      :host {
        color: var(--primary-text-color);
      }
      #newEmailInput {
        width: 20em;
      }
      #email {
        margin-bottom: 1em;
      }
      .filters p,
      .darkToggle p {
        margin-bottom: 1em;
      }
      .queryExample em {
        color: violet;
      }
      .toggle {
        align-items: center;
        display: flex;
        margin-bottom: 1rem;
        margin-right: 1rem;
      }
    </style>
    <style include="gr-form-styles"></style>
    <style include="gr-menu-page-styles"></style>
    <style include="gr-page-nav-styles"></style>
    <div class="loading" hidden\$="[[!_loading]]">Loading...</div>
    <div hidden\$="[[_loading]]" hidden="">
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
          <li><a href="#HTTPCredentials">HTTP Credentials</a></li>
          <li hidden\$="[[!_serverConfig.sshd]]"><a href="#SSHKeys">
            SSH Keys
          </a></li>
          <li hidden\$="[[!_serverConfig.receive.enable_signed_push]]"><a href="#GPGKeys">
            GPG Keys
          </a></li>
          <li><a href="#Groups">Groups</a></li>
          <li><a href="#Identities">Identities</a></li>
          <template is="dom-if" if="[[_serverConfig.auth.use_contributor_agreements]]">
            <li>
              <a href="#Agreements">Agreements</a>
            </li>
          </template>
          <li><a href="#MailFilters">Mail Filters</a></li>
          <gr-endpoint-decorator name="settings-menu-item">
          </gr-endpoint-decorator>
        </ul>
      </gr-page-nav>
      <main class="gr-form-styles">
        <h1>User Settings</h1>
        <section class="darkToggle">
          <div class="toggle">
            <paper-toggle-button checked="[[_isDark]]" on-change="_handleToggleDark"></paper-toggle-button>
            <div>Dark theme (alpha)</div>
          </div>
          <p>
            Gerrit's dark theme is in early alpha, and almost definitely will
            not play nicely with themes set by specific Gerrit hosts. Filing
            feedback via the link in the app footer is strongly encouraged!
          </p>
        </section>
        <h2 id="Profile" class\$="[[_computeHeaderClass(_accountInfoChanged)]]">Profile</h2>
        <fieldset id="profile">
          <gr-account-info id="accountInfo" mutable="{{_accountNameMutable}}" has-unsaved-changes="{{_accountInfoChanged}}"></gr-account-info>
          <gr-button on-tap="_handleSaveAccountInfo" disabled="[[!_accountInfoChanged]]">Save changes</gr-button>
        </fieldset>
        <h2 id="Preferences" class\$="[[_computeHeaderClass(_prefsChanged)]]">Preferences</h2>
        <fieldset id="preferences">
          <section>
            <span class="title">Changes per page</span>
            <span class="value">
              <gr-select bind-value="{{_localPrefs.changes_per_page}}">
                <select>
                  <option value="10">10 rows per page</option>
                  <option value="25">25 rows per page</option>
                  <option value="50">50 rows per page</option>
                  <option value="100">100 rows per page</option>
                </select>
              </gr-select>
            </span>
          </section>
          <section>
            <span class="title">Date/time format</span>
            <span class="value">
              <gr-select bind-value="{{_localPrefs.date_format}}">
                <select>
                  <option value="STD">Jun 3 ; Jun 3, 2016</option>
                  <option value="US">06/03 ; 06/03/16</option>
                  <option value="ISO">06-03 ; 2016-06-03</option>
                  <option value="EURO">3. Jun ; 03.06.2016</option>
                  <option value="UK">03/06 ; 03/06/2016</option>
                </select>
              </gr-select>
              <gr-select bind-value="{{_localPrefs.time_format}}">
                <select>
                  <option value="HHMM_12">4:10 PM</option>
                  <option value="HHMM_24">16:10</option>
                </select>
              </gr-select>
            </span>
          </section>
          <section>
            <span class="title">Email notifications</span>
            <span class="value">
              <gr-select bind-value="{{_localPrefs.email_strategy}}">
                <select>
                  <option value="CC_ON_OWN_COMMENTS">Every comment</option>
                  <option value="ENABLED">Only comments left by others</option>
                  <option value="DISABLED">None</option>
                </select>
              </gr-select>
            </span>
          </section>
          <section hidden\$="[[!_localPrefs.email_format]]">
            <span class="title">Email format</span>
            <span class="value">
              <gr-select bind-value="{{_localPrefs.email_format}}">
                <select>
                  <option value="HTML_PLAINTEXT">HTML and plaintext</option>
                  <option value="PLAINTEXT">Plaintext only</option>
                </select>
              </gr-select>
            </span>
          </section>
          <section>
            <span class="title">Diff view</span>
            <span class="value">
              <gr-select bind-value="{{_localPrefs.diff_view}}">
                <select>
                  <option value="SIDE_BY_SIDE">Side by side</option>
                  <option value="UNIFIED_DIFF">Unified diff</option>
                </select>
              </gr-select>
            </span>
          </section>
          <section>
            <span class="title">Show size bars in file list</span>
            <span class="value">
              <input id="showSizeBarsInFileList" type="checkbox" checked\$="[[_localPrefs.size_bar_in_change_table]]" on-change="_handleShowSizeBarsInFileListChanged">
            </span>
          </section>
          <section>
            <span class="title">Publish comments on push</span>
            <span class="value">
              <input id="publishCommentsOnPush" type="checkbox" checked\$="[[_localPrefs.publish_comments_on_push]]" on-change="_handlePublishCommentsOnPushChanged">
            </span>
          </section>
          <section>
            <span class="title">Set new changes to "work in progress" by default</span>
            <span class="value">
              <input id="workInProgressByDefault" type="checkbox" checked\$="[[_localPrefs.work_in_progress_by_default]]" on-change="_handleWorkInProgressByDefault">
            </span>
          </section>
          <section>
            <span class="title">
              Insert Signed-off-by Footer For Inline Edit Changes
            </span>
            <span class="value">
              <input id="insertSignedOff" type="checkbox" checked\$="[[_localPrefs.signed_off_by]]" on-change="_handleInsertSignedOff">
            </span>
          </section>
          <gr-button id="savePrefs" on-tap="_handleSavePreferences" disabled="[[!_prefsChanged]]">Save changes</gr-button>
        </fieldset>
        <h2 id="DiffPreferences" class\$="[[_computeHeaderClass(_diffPrefsChanged)]]">
          Diff Preferences
        </h2>
        <fieldset id="diffPreferences">
          <section>
            <span class="title">Context</span>
            <span class="value">
              <gr-select bind-value="{{_diffPrefs.context}}">
                <select>
                  <option value="3">3 lines</option>
                  <option value="10">10 lines</option>
                  <option value="25">25 lines</option>
                  <option value="50">50 lines</option>
                  <option value="75">75 lines</option>
                  <option value="100">100 lines</option>
                  <option value="-1">Whole file</option>
                </select>
              </gr-select>
            </span>
          </section>
          <section>
            <span class="title">Fit to screen</span>
            <span class="value">
              <input id="diffLineWrapping" type="checkbox" checked\$="[[_diffPrefs.line_wrapping]]" on-change="_handleDiffLineWrappingChanged">
            </span>
          </section>
          <section id="columnsPref" hidden\$="[[_diffPrefs.line_wrapping]]">
            <span class="title">Diff width</span>
            <span class="value">
              <input is="iron-input" type="number" prevent-invalid-input="" allowed-pattern="[0-9]" bind-value="{{_diffPrefs.line_length}}">
            </span>
          </section>
          <section>
            <span class="title">Tab width</span>
            <span class="value">
              <input is="iron-input" type="number" prevent-invalid-input="" allowed-pattern="[0-9]" bind-value="{{_diffPrefs.tab_size}}">
            </span>
          </section>
          <section hidden\$="[[!_diffPrefs.font_size]]">
            <span class="title">Font size</span>
            <span class="value">
              <input is="iron-input" type="number" prevent-invalid-input="" allowed-pattern="[0-9]" bind-value="{{_diffPrefs.font_size}}">
            </span>
          </section>
          <section>
            <span class="title">Show tabs</span>
            <span class="value">
              <input id="diffShowTabs" type="checkbox" checked\$="[[_diffPrefs.show_tabs]]" on-change="_handleDiffShowTabsChanged">
            </span>
          </section>
          <section>
            <span class="title">Show trailing whitespace</span>
            <span class="value">
              <input id="showTrailingWhitespace" type="checkbox" checked\$="[[_diffPrefs.show_whitespace_errors]]" on-change="_handleShowTrailingWhitespaceChanged">
            </span>
          </section>
          <section>
            <span class="title">Syntax highlighting</span>
            <span class="value">
              <input id="diffSyntaxHighlighting" type="checkbox" checked\$="[[_diffPrefs.syntax_highlighting]]" on-change="_handleDiffSyntaxHighlightingChanged">
            </span>
          </section>
          <gr-button id="saveDiffPrefs" on-tap="_handleSaveDiffPreferences" disabled\$="[[!_diffPrefsChanged]]">Save changes</gr-button>
        </fieldset>
        <h2 id="EditPreferences" class\$="[[_computeHeaderClass(_editPrefsChanged)]]">
          Edit Preferences
        </h2>
        <fieldset id="editPreferences">
          <gr-edit-preferences id="editPrefs" has-unsaved-changes="{{_editPrefsChanged}}"></gr-edit-preferences>
          <gr-button id="saveEditPrefs" on-tap="_handleSaveEditPreferences" disabled\$="[[!_editPrefsChanged]]">Save changes</gr-button>
        </fieldset>
        <h2 id="Menu" class\$="[[_computeHeaderClass(_menuChanged)]]">Menu</h2>
        <fieldset id="menu">
          <gr-menu-editor menu-items="{{_localMenu}}"></gr-menu-editor>
          <gr-button id="saveMenu" on-tap="_handleSaveMenu" disabled="[[!_menuChanged]]">Save changes</gr-button>
          <gr-button id="resetMenu" link="" on-tap="_handleResetMenuButton">Reset</gr-button>
        </fieldset>
        <h2 id="ChangeTableColumns" class\$="[[_computeHeaderClass(_changeTableChanged)]]">
          Change Table Columns
        </h2>
        <fieldset id="changeTableColumns">
          <gr-change-table-editor show-number="{{_showNumber}}" displayed-columns="{{_localChangeTableColumns}}">
          </gr-change-table-editor>
          <gr-button id="saveChangeTable" on-tap="_handleSaveChangeTable" disabled="[[!_changeTableChanged]]">Save changes</gr-button>
        </fieldset>
        <h2 id="Notifications" class\$="[[_computeHeaderClass(_watchedProjectsChanged)]]">
          Notifications
        </h2>
        <fieldset id="watchedProjects">
          <gr-watched-projects-editor has-unsaved-changes="{{_watchedProjectsChanged}}" id="watchedProjectsEditor"></gr-watched-projects-editor>
          <gr-button on-tap="_handleSaveWatchedProjects" disabled\$="[[!_watchedProjectsChanged]]" id="_handleSaveWatchedProjects">Save changes</gr-button>
        </fieldset>
        <h2 id="EmailAddresses" class\$="[[_computeHeaderClass(_emailsChanged)]]">
          Email Addresses
        </h2>
        <fieldset id="email">
          <gr-email-editor id="emailEditor" has-unsaved-changes="{{_emailsChanged}}"></gr-email-editor>
          <gr-button on-tap="_handleSaveEmails" disabled\$="[[!_emailsChanged]]">Save changes</gr-button>
        </fieldset>
        <fieldset id="newEmail">
          <section>
            <span class="title">New email address</span>
            <span class="value">
              <input id="newEmailInput" bind-value="{{_newEmail}}" is="iron-input" type="text" disabled="[[_addingEmail]]" on-keydown="_handleNewEmailKeydown" placeholder="email@example.com">
            </span>
          </section>
          <section id="verificationSentMessage" hidden\$="[[!_lastSentVerificationEmail]]">
            <p>
              A verification email was sent to
              <em>[[_lastSentVerificationEmail]]</em>. Please check your inbox.
            </p>
          </section>
          <gr-button disabled="[[!_computeAddEmailButtonEnabled(_newEmail, _addingEmail)]]" on-tap="_handleAddEmailButton">Send verification</gr-button>
        </fieldset>
        <h2 id="HTTPCredentials">HTTP Credentials</h2>
        <fieldset>
          <gr-http-password id="httpPass"></gr-http-password>
        </fieldset>
        <div hidden\$="[[!_serverConfig.sshd]]">
          <h2 id="SSHKeys" class\$="[[_computeHeaderClass(_keysChanged)]]">SSH keys</h2>
          <gr-ssh-editor id="sshEditor" has-unsaved-changes="{{_keysChanged}}"></gr-ssh-editor>
        </div>
        <div hidden\$="[[!_serverConfig.receive.enable_signed_push]]">
          <h2 id="GPGKeys" class\$="[[_computeHeaderClass(_gpgKeysChanged)]]">GPG keys</h2>
          <gr-gpg-editor id="gpgEditor" has-unsaved-changes="{{_gpgKeysChanged}}"></gr-gpg-editor>
        </div>
        <h2 id="Groups">Groups</h2>
        <fieldset>
          <gr-group-list id="groupList"></gr-group-list>
        </fieldset>
        <h2 id="Identities">Identities</h2>
        <fieldset>
          <gr-identities id="identities"></gr-identities>
        </fieldset>
        <template is="dom-if" if="[[_serverConfig.auth.use_contributor_agreements]]">
          <h2 id="Agreements">Agreements</h2>
          <fieldset>
            <gr-agreements-list id="agreementsList"></gr-agreements-list>
          </fieldset>
        </template>
        <h2 id="MailFilters">Mail Filters</h2>
        <fieldset class="filters">
          <p>
            Gerrit emails include metadata about the change to support
            writing mail filters.
          </p>
          <p>
            Here are some example Gmail queries that can be used for filters or
            for searching through archived messages. View the
            <a href\$="[[_getFilterDocsLink(_docsBaseUrl)]]" target="_blank" rel="nofollow">Gerrit documentation</a>
            for the complete set of footers.
          </p>
          <table>
            <tbody>
              <tr><th>Name</th><th>Query</th></tr>
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
        <gr-endpoint-decorator name="settings-screen">
        </gr-endpoint-decorator>
      </main>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-settings-view',

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

  properties: {
    prefs: {
      type: Object,
      value() { return {}; },
    },
    params: {
      type: Object,
      value() { return {}; },
    },
    _accountNameMutable: Boolean,
    _accountInfoChanged: Boolean,
    /** @type {?} */
    _diffPrefs: Object,
    _changeTableColumnsNotDisplayed: Array,
    /** @type {?} */
    _localPrefs: {
      type: Object,
      value() { return {}; },
    },
    _localChangeTableColumns: {
      type: Array,
      value() { return []; },
    },
    _localMenu: {
      type: Array,
      value() { return []; },
    },
    _loading: {
      type: Boolean,
      value: true,
    },
    _changeTableChanged: {
      type: Boolean,
      value: false,
    },
    _prefsChanged: {
      type: Boolean,
      value: false,
    },
    _diffPrefsChanged: {
      type: Boolean,
      value: false,
    },
    /** @type {?} */
    _editPrefsChanged: Boolean,
    _menuChanged: {
      type: Boolean,
      value: false,
    },
    _watchedProjectsChanged: {
      type: Boolean,
      value: false,
    },
    _keysChanged: {
      type: Boolean,
      value: false,
    },
    _gpgKeysChanged: {
      type: Boolean,
      value: false,
    },
    _newEmail: String,
    _addingEmail: {
      type: Boolean,
      value: false,
    },
    _lastSentVerificationEmail: {
      type: String,
      value: null,
    },
    /** @type {?} */
    _serverConfig: Object,
    /** @type {?string} */
    _docsBaseUrl: String,
    _emailsChanged: Boolean,

    /**
     * For testing purposes.
     */
    _loadingPromise: Object,

    _showNumber: Boolean,

    _isDark: {
      type: Boolean,
      value: false,
    },
  },

  behaviors: [
    Gerrit.DocsUrlBehavior,
    Gerrit.ChangeTableBehavior,
  ],

  observers: [
    '_handlePrefsChanged(_localPrefs.*)',
    '_handleDiffPrefsChanged(_diffPrefs.*)',
    '_handleMenuChanged(_localMenu.splices)',
    '_handleChangeTableChanged(_localChangeTableColumns, _showNumber)',
  ],

  attached() {
    this.fire('title-change', {title: 'Settings'});

    this._isDark = !!window.localStorage.getItem('dark-theme');

    const promises = [
      this.$.accountInfo.loadData(),
      this.$.watchedProjectsEditor.loadData(),
      this.$.groupList.loadData(),
      this.$.httpPass.loadData(),
      this.$.identities.loadData(),
      this.$.editPrefs.loadData(),
    ];

    promises.push(this.$.restAPI.getPreferences().then(prefs => {
      this.prefs = prefs;
      this._showNumber = !!prefs.legacycid_in_change_table;
      this._copyPrefs('_localPrefs', 'prefs');
      this._cloneMenu(prefs.my);
      this._cloneChangeTableColumns();
    }));

    promises.push(this.$.restAPI.getDiffPreferences().then(prefs => {
      this._diffPrefs = prefs;
    }));

    promises.push(this.$.restAPI.getConfig().then(config => {
      this._serverConfig = config;
      const configPromises = [];

      if (this._serverConfig && this._serverConfig.sshd) {
        configPromises.push(this.$.sshEditor.loadData());
      }

      if (this._serverConfig &&
          this._serverConfig.receive &&
          this._serverConfig.receive.enable_signed_push) {
        configPromises.push(this.$.gpgEditor.loadData());
      }

      configPromises.push(
          this.getDocsBaseUrl(config, this.$.restAPI)
              .then(baseUrl => { this._docsBaseUrl = baseUrl; }));

      return Promise.all(configPromises);
    }));

    if (this.params.emailToken) {
      promises.push(this.$.restAPI.confirmEmail(this.params.emailToken).then(
          message => {
            if (message) {
              this.fire('show-alert', {message});
            }
            this.$.emailEditor.loadData();
          }));
    } else {
      promises.push(this.$.emailEditor.loadData());
    }

    this._loadingPromise = Promise.all(promises).then(() => {
      this._loading = false;
    });
  },

  reloadAccountDetail() {
    Promise.all([
      this.$.accountInfo.loadData(),
      this.$.emailEditor.loadData(),
    ]);
  },

  _isLoading() {
    return this._loading || this._loading === undefined;
  },

  _copyPrefs(to, from) {
    for (let i = 0; i < PREFS_SECTION_FIELDS.length; i++) {
      this.set([to, PREFS_SECTION_FIELDS[i]],
          this[from][PREFS_SECTION_FIELDS[i]]);
    }
  },

  _cloneMenu(prefs) {
    const menu = [];
    for (const item of prefs) {
      menu.push({
        name: item.name,
        url: item.url,
        target: item.target,
      });
    }
    this._localMenu = menu;
  },

  _cloneChangeTableColumns() {
    let columns = this.getVisibleColumns(this.prefs.change_table);

    if (columns.length === 0) {
      columns = this.columnNames;
      this._changeTableColumnsNotDisplayed = [];
    } else {
      this._changeTableColumnsNotDisplayed = this.getComplementColumns(
          this.prefs.change_table);
    }
    this._localChangeTableColumns = columns;
  },

  _formatChangeTableColumns(changeTableArray) {
    return changeTableArray.map(item => {
      return {column: item};
    });
  },

  _handleChangeTableChanged() {
    if (this._isLoading()) { return; }
    this._changeTableChanged = true;
  },

  _handlePrefsChanged(prefs) {
    if (this._isLoading()) { return; }
    this._prefsChanged = true;
  },

  _handleDiffPrefsChanged() {
    if (this._isLoading()) { return; }
    this._diffPrefsChanged = true;
  },

  _handleShowSizeBarsInFileListChanged() {
    this.set('_localPrefs.size_bar_in_change_table',
        this.$.showSizeBarsInFileList.checked);
  },

  _handlePublishCommentsOnPushChanged() {
    this.set('_localPrefs.publish_comments_on_push',
        this.$.publishCommentsOnPush.checked);
  },

  _handleWorkInProgressByDefault() {
    this.set('_localPrefs.work_in_progress_by_default',
        this.$.workInProgressByDefault.checked);
  },

  _handleInsertSignedOff() {
    this.set('_localPrefs.signed_off_by', this.$.insertSignedOff.checked);
  },

  _handleMenuChanged() {
    if (this._isLoading()) { return; }
    this._menuChanged = true;
  },

  _handleSaveAccountInfo() {
    this.$.accountInfo.save();
  },

  _handleSavePreferences() {
    this._copyPrefs('prefs', '_localPrefs');

    return this.$.restAPI.savePreferences(this.prefs).then(() => {
      this._prefsChanged = false;
    });
  },

  _handleDiffLineWrappingChanged() {
    this.set('_diffPrefs.line_wrapping', this.$.diffLineWrapping.checked);
  },

  _handleDiffShowTabsChanged() {
    this.set('_diffPrefs.show_tabs', this.$.diffShowTabs.checked);
  },

  _handleShowTrailingWhitespaceChanged() {
    this.set('_diffPrefs.show_whitespace_errors',
        this.$.showTrailingWhitespace.checked);
  },

  _handleDiffSyntaxHighlightingChanged() {
    this.set('_diffPrefs.syntax_highlighting',
        this.$.diffSyntaxHighlighting.checked);
  },

  _handleSaveChangeTable() {
    this.set('prefs.change_table', this._localChangeTableColumns);
    this.set('prefs.legacycid_in_change_table', this._showNumber);
    this._cloneChangeTableColumns();
    return this.$.restAPI.savePreferences(this.prefs).then(() => {
      this._changeTableChanged = false;
    });
  },

  _handleSaveDiffPreferences() {
    return this.$.restAPI.saveDiffPreferences(this._diffPrefs)
        .then(() => {
          this._diffPrefsChanged = false;
        });
  },

  _handleSaveEditPreferences() {
    this.$.editPrefs.save();
  },

  _handleSaveMenu() {
    this.set('prefs.my', this._localMenu);
    this._cloneMenu(this.prefs.my);
    return this.$.restAPI.savePreferences(this.prefs).then(() => {
      this._menuChanged = false;
    });
  },

  _handleResetMenuButton() {
    return this.$.restAPI.getDefaultPreferences().then(data => {
      if (data && data.my) {
        this._cloneMenu(data.my);
      }
    });
  },

  _handleSaveWatchedProjects() {
    this.$.watchedProjectsEditor.save();
  },

  _computeHeaderClass(changed) {
    return changed ? 'edited' : '';
  },

  _handleSaveEmails() {
    this.$.emailEditor.save();
  },

  _handleNewEmailKeydown(e) {
    if (e.keyCode === 13) { // Enter
      e.stopPropagation();
      this._handleAddEmailButton();
    }
  },

  _isNewEmailValid(newEmail) {
    return newEmail.includes('@');
  },

  _computeAddEmailButtonEnabled(newEmail, addingEmail) {
    return this._isNewEmailValid(newEmail) && !addingEmail;
  },

  _handleAddEmailButton() {
    if (!this._isNewEmailValid(this._newEmail)) { return; }

    this._addingEmail = true;
    this.$.restAPI.addAccountEmail(this._newEmail).then(response => {
      this._addingEmail = false;

      // If it was unsuccessful.
      if (response.status < 200 || response.status >= 300) { return; }

      this._lastSentVerificationEmail = this._newEmail;
      this._newEmail = '';
    });
  },

  _getFilterDocsLink(docsBaseUrl) {
    let base = docsBaseUrl;
    if (!base || !ABSOLUTE_URL_PATTERN.test(base)) {
      base = GERRIT_DOCS_BASE_URL;
    }

    // Remove any trailing slash, since it is in the GERRIT_DOCS_FILTER_PATH.
    base = base.replace(TRAILING_SLASH_PATTERN, '');

    return base + GERRIT_DOCS_FILTER_PATH;
  },

  _handleToggleDark() {
    if (this._isDark) {
      window.localStorage.removeItem('dark-theme');
    } else {
      window.localStorage.setItem('dark-theme', 'true');
    }
    this.dispatchEvent(new CustomEvent('show-alert', {
      detail: {message: RELOAD_MESSAGE},
      bubbles: true,
    }));
    this.async(() => {
      window.location.reload();
    }, 1);
  }
});
