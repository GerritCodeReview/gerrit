// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function() {
  'use strict';

  const PREFS_SECTION_FIELDS = [
    'changes_per_page',
    'date_format',
    'time_format',
    'email_strategy',
    'diff_view',
    'expand_inline_diffs',
    'publish_comments_on_push',
    'signed_off_by',
    'email_format',
  ];

  const GERRIT_DOCS_BASE_URL = 'https://gerrit-review.googlesource.com/' +
      'Documentation';
  const GERRIT_DOCS_FILTER_PATH = '/user-notify.html';
  const ABSOLUTE_URL_PATTERN = /^https?:/;
  const TRAILING_SLASH_PATTERN = /\/$/;

  Polymer({
    is: 'gr-settings-view',

    /**
     * Fired when the title of the page should change.
     *
     * @event title-change
     */

    /**
     * Fired with email confirmation text.
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

      const promises = [
        this.$.accountInfo.loadData(),
        this.$.watchedProjectsEditor.loadData(),
        this.$.groupList.loadData(),
        this.$.httpPass.loadData(),
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

        if (this._serverConfig.sshd) {
          configPromises.push(this.$.sshEditor.loadData());
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
      let columns = this.prefs.change_table;

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

    _handleExpandInlineDiffsChanged() {
      this.set('_localPrefs.expand_inline_diffs',
          this.$.expandInlineDiffs.checked);
    },

    _handlePublishCommentsOnPushChanged() {
      this.set('_localPrefs.publish_comments_on_push',
          this.$.publishCommentsOnPush.checked);
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

    _handleLineWrappingChanged() {
      this.set('_diffPrefs.line_wrapping', this.$.lineWrapping.checked);
    },

    _handleShowTabsChanged() {
      this.set('_diffPrefs.show_tabs', this.$.showTabs.checked);
    },

    _handleShowTrailingWhitespaceChanged() {
      this.set('_diffPrefs.show_whitespace_errors',
          this.$.showTrailingWhitespace.checked);
    },

    _handleSyntaxHighlightingChanged() {
      this.set('_diffPrefs.syntax_highlighting',
          this.$.syntaxHighlighting.checked);
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
  });
})();
