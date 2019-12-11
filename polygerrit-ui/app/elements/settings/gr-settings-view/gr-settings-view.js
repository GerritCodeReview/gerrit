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
(function() {
  'use strict';

  const PREFS_SECTION_FIELDS = [
    'changes_per_page',
    'date_format',
    'time_format',
    'email_strategy',
    'diff_view',
    'publish_comments_on_push',
    'work_in_progress_by_default',
    'default_base_for_merges',
    'signed_off_by',
    'email_format',
    'size_bar_in_change_table',
    'relative_date_in_change_table',
  ];

  const GERRIT_DOCS_BASE_URL = 'https://gerrit-review.googlesource.com/' +
      'Documentation';
  const GERRIT_DOCS_FILTER_PATH = '/user-notify.html';
  const ABSOLUTE_URL_PATTERN = /^https?:/;
  const TRAILING_SLASH_PATTERN = /\/$/;

  const RELOAD_MESSAGE = 'Reloading...';

  const HTTP_AUTH = [
    'HTTP',
    'HTTP_LDAP',
  ];

  /**
   * @appliesMixin Gerrit.DocsUrlMixin
   * @appliesMixin Gerrit.ChangeTableMixin
   * @appliesMixin Gerrit.FireMixin
   */
  class GrSettingsView extends Polymer.mixinBehaviors( [
    Gerrit.DocsUrlBehavior,
    Gerrit.ChangeTableBehavior,
    Gerrit.FireBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-settings-view'; }
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

    static get properties() {
      return {
        prefs: {
          type: Object,
          value() { return {}; },
        },
        params: {
          type: Object,
          value() { return {}; },
        },
        _accountInfoChanged: Boolean,
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
        /** @type {?} */
        _diffPrefsChanged: Boolean,
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
      };
    }

    static get observers() {
      return [
        '_handlePrefsChanged(_localPrefs.*)',
        '_handleMenuChanged(_localMenu.splices)',
        '_handleChangeTableChanged(_localChangeTableColumns, _showNumber)',
      ];
    }

    attached() {
      super.attached();
      // Polymer 2: anchor tag won't work on shadow DOM
      // we need to manually calling scrollIntoView when hash changed
      this.listen(window, 'location-change', '_handleLocationChange');
      this.fire('title-change', {title: 'Settings'});

      this._isDark = !!window.localStorage.getItem('dark-theme');

      const promises = [
        this.$.accountInfo.loadData(),
        this.$.watchedProjectsEditor.loadData(),
        this.$.groupList.loadData(),
        this.$.identities.loadData(),
        this.$.editPrefs.loadData(),
        this.$.diffPrefs.loadData(),
      ];

      promises.push(this.$.restAPI.getPreferences().then(prefs => {
        this.prefs = prefs;
        this._showNumber = !!prefs.legacycid_in_change_table;
        this._copyPrefs('_localPrefs', 'prefs');
        this._cloneMenu(prefs.my);
        this._cloneChangeTableColumns();
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

        // Handle anchor tag for initial load
        this._handleLocationChange();
      });
    }

    detached() {
      super.detached();
      this.unlisten(window, 'location-change', '_handleLocationChange');
    }

    _handleLocationChange() {
      // Handle anchor tag after dom attached
      const urlHash = window.location.hash;
      if (urlHash) {
        // Use shadowRoot for Polymer 2
        const elem = (this.shadowRoot || document).querySelector(urlHash);
        if (elem) {
          elem.scrollIntoView();
        }
      }
    }

    reloadAccountDetail() {
      Promise.all([
        this.$.accountInfo.loadData(),
        this.$.emailEditor.loadData(),
      ]);
    }

    _isLoading() {
      return this._loading || this._loading === undefined;
    }

    _copyPrefs(to, from) {
      for (let i = 0; i < PREFS_SECTION_FIELDS.length; i++) {
        this.set([to, PREFS_SECTION_FIELDS[i]],
            this[from][PREFS_SECTION_FIELDS[i]]);
      }
    }

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
    }

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
    }

    _formatChangeTableColumns(changeTableArray) {
      return changeTableArray.map(item => {
        return {column: item};
      });
    }

    _handleChangeTableChanged() {
      if (this._isLoading()) { return; }
      this._changeTableChanged = true;
    }

    _handlePrefsChanged(prefs) {
      if (this._isLoading()) { return; }
      this._prefsChanged = true;
    }

    _handleRelativeDateInChangeTable() {
      this.set('_localPrefs.relative_date_in_change_table',
          this.$.relativeDateInChangeTable.checked);
    }

    _handleShowSizeBarsInFileListChanged() {
      this.set('_localPrefs.size_bar_in_change_table',
          this.$.showSizeBarsInFileList.checked);
    }

    _handlePublishCommentsOnPushChanged() {
      this.set('_localPrefs.publish_comments_on_push',
          this.$.publishCommentsOnPush.checked);
    }

    _handleWorkInProgressByDefault() {
      this.set('_localPrefs.work_in_progress_by_default',
          this.$.workInProgressByDefault.checked);
    }

    _handleInsertSignedOff() {
      this.set('_localPrefs.signed_off_by', this.$.insertSignedOff.checked);
    }

    _handleMenuChanged() {
      if (this._isLoading()) { return; }
      this._menuChanged = true;
    }

    _handleSaveAccountInfo() {
      this.$.accountInfo.save();
    }

    _handleSavePreferences() {
      this._copyPrefs('prefs', '_localPrefs');

      return this.$.restAPI.savePreferences(this.prefs).then(() => {
        this._prefsChanged = false;
      });
    }

    _handleSaveChangeTable() {
      this.set('prefs.change_table', this._localChangeTableColumns);
      this.set('prefs.legacycid_in_change_table', this._showNumber);
      this._cloneChangeTableColumns();
      return this.$.restAPI.savePreferences(this.prefs).then(() => {
        this._changeTableChanged = false;
      });
    }

    _handleSaveDiffPreferences() {
      this.$.diffPrefs.save();
    }

    _handleSaveEditPreferences() {
      this.$.editPrefs.save();
    }

    _handleSaveMenu() {
      this.set('prefs.my', this._localMenu);
      this._cloneMenu(this.prefs.my);
      return this.$.restAPI.savePreferences(this.prefs).then(() => {
        this._menuChanged = false;
      });
    }

    _handleResetMenuButton() {
      return this.$.restAPI.getDefaultPreferences().then(data => {
        if (data && data.my) {
          this._cloneMenu(data.my);
        }
      });
    }

    _handleSaveWatchedProjects() {
      this.$.watchedProjectsEditor.save();
    }

    _computeHeaderClass(changed) {
      return changed ? 'edited' : '';
    }

    _handleSaveEmails() {
      this.$.emailEditor.save();
    }

    _handleNewEmailKeydown(e) {
      if (e.keyCode === 13) { // Enter
        e.stopPropagation();
        this._handleAddEmailButton();
      }
    }

    _isNewEmailValid(newEmail) {
      return newEmail && newEmail.includes('@');
    }

    _computeAddEmailButtonEnabled(newEmail, addingEmail) {
      return this._isNewEmailValid(newEmail) && !addingEmail;
    }

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
    }

    _getFilterDocsLink(docsBaseUrl) {
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
      this.dispatchEvent(new CustomEvent('show-alert', {
        detail: {message: RELOAD_MESSAGE},
        bubbles: true,
        composed: true,
      }));
      this.async(() => {
        window.location.reload();
      }, 1);
    }

    _showHttpAuth(config) {
      if (config && config.auth &&
          config.auth.git_basic_auth_policy) {
        return HTTP_AUTH.includes(
            config.auth.git_basic_auth_policy.toUpperCase());
      }

      return false;
    }
  }

  customElements.define(GrSettingsView.is, GrSettingsView);
})();
