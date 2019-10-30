/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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

  Polymer({
    is: 'gr-app-element',

    /**
     * Fired when the URL location changes.
     *
     * @event location-change
     */

    properties: {
      /**
       * @type {{ query: string, view: string, screen: string }}
       */
      params: Object,
      keyEventTarget: {
        type: Object,
        value() { return document.body; },
      },

      _account: {
        type: Object,
        observer: '_accountChanged',
      },

      /**
       * The last time the g key was pressed in milliseconds (or a keydown event
       * was handled if the key is held down).
       * @type {number|null}
       */
      _lastGKeyPressTimestamp: {
        type: Number,
        value: null,
      },

      /**
       * @type {{ plugin: Object }}
       */
      _serverConfig: Object,
      _version: String,
      _showChangeListView: Boolean,
      _showDashboardView: Boolean,
      _showChangeView: Boolean,
      _showDiffView: Boolean,
      _showSettingsView: Boolean,
      _showAdminView: Boolean,
      _showCLAView: Boolean,
      _showEditorView: Boolean,
      _showPluginScreen: Boolean,
      _showDocumentationSearch: Boolean,
      /** @type {?} */
      _viewState: Object,
      /** @type {?} */
      _lastError: Object,
      _lastSearchPage: String,
      _path: String,
      _pluginScreenName: {
        type: String,
        computed: '_computePluginScreenName(params)',
      },
      _settingsUrl: String,
      _feedbackUrl: String,
      // Used to allow searching on mobile
      mobileSearch: {
        type: Boolean,
        value: false,
      },
    },

    listeners: {
      'page-error': '_handlePageError',
      'title-change': '_handleTitleChange',
      'location-change': '_handleLocationChange',
      'rpc-log': '_handleRpcLog',
    },

    observers: [
      '_viewChanged(params.view)',
      '_paramsChanged(params.*)',
    ],

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.KeyboardShortcutBehavior,
    ],

    keyboardShortcuts() {
      return {
        [this.Shortcut.OPEN_SHORTCUT_HELP_DIALOG]: '_showKeyboardShortcuts',
        [this.Shortcut.GO_TO_USER_DASHBOARD]: '_goToUserDashboard',
        [this.Shortcut.GO_TO_OPENED_CHANGES]: '_goToOpenedChanges',
        [this.Shortcut.GO_TO_MERGED_CHANGES]: '_goToMergedChanges',
        [this.Shortcut.GO_TO_ABANDONED_CHANGES]: '_goToAbandonedChanges',
        [this.Shortcut.GO_TO_WATCHED_CHANGES]: '_goToWatchedChanges',
      };
    },

    created() {
      this._bindKeyboardShortcuts();
    },

    ready() {
      this.$.reporting.appStarted();
      this.$.router.start();

      this.$.restAPI.getAccount().then(account => {
        this._account = account;
      });
      this.$.restAPI.getConfig().then(config => {
        this._serverConfig = config;

        if (config && config.gerrit && config.gerrit.report_bug_url) {
          this._feedbackUrl = config.gerrit.report_bug_url;
        }
      });
      this.$.restAPI.getVersion().then(version => {
        this._version = version;
        this._logWelcome();
      });

      if (window.localStorage.getItem('dark-theme')) {
        // No need to add the style module to element again as it's imported
        // by importHref already
        this.$.libLoader.getDarkTheme();
      }

      // Note: this is evaluated here to ensure that it only happens after the
      // router has been initialized. @see Issue 7837
      this._settingsUrl = Gerrit.Nav.getUrlForSettings();

      this._viewState = {
        changeView: {
          changeNum: null,
          patchRange: null,
          selectedFileIndex: 0,
          showReplyDialog: false,
          diffMode: null,
          numFilesShown: null,
          scrollTop: 0,
        },
        changeListView: {
          query: null,
          offset: 0,
          selectedChangeIndex: 0,
        },
        dashboardView: {
          selectedChangeIndex: 0,
        },
      };
    },

    _bindKeyboardShortcuts() {
      this.bindShortcut(this.Shortcut.SEND_REPLY,
          this.DOC_ONLY, 'ctrl+enter', 'meta+enter');
      this.bindShortcut(this.Shortcut.EMOJI_DROPDOWN,
          this.DOC_ONLY, ':');

      this.bindShortcut(
          this.Shortcut.OPEN_SHORTCUT_HELP_DIALOG, '?');
      this.bindShortcut(
          this.Shortcut.GO_TO_USER_DASHBOARD, this.GO_KEY, 'i');
      this.bindShortcut(
          this.Shortcut.GO_TO_OPENED_CHANGES, this.GO_KEY, 'o');
      this.bindShortcut(
          this.Shortcut.GO_TO_MERGED_CHANGES, this.GO_KEY, 'm');
      this.bindShortcut(
          this.Shortcut.GO_TO_ABANDONED_CHANGES, this.GO_KEY, 'a');
      this.bindShortcut(
          this.Shortcut.GO_TO_WATCHED_CHANGES, this.GO_KEY, 'w');

      this.bindShortcut(
          this.Shortcut.CURSOR_NEXT_CHANGE, 'j');
      this.bindShortcut(
          this.Shortcut.CURSOR_PREV_CHANGE, 'k');
      this.bindShortcut(
          this.Shortcut.OPEN_CHANGE, 'o');
      this.bindShortcut(
          this.Shortcut.NEXT_PAGE, 'n', ']');
      this.bindShortcut(
          this.Shortcut.PREV_PAGE, 'p', '[');
      this.bindShortcut(
          this.Shortcut.TOGGLE_CHANGE_REVIEWED, 'r');
      this.bindShortcut(
          this.Shortcut.TOGGLE_CHANGE_STAR, 's');
      this.bindShortcut(
          this.Shortcut.REFRESH_CHANGE_LIST, 'shift+r');
      this.bindShortcut(
          this.Shortcut.EDIT_TOPIC, 't');

      this.bindShortcut(
          this.Shortcut.OPEN_REPLY_DIALOG, 'a');
      this.bindShortcut(
          this.Shortcut.OPEN_DOWNLOAD_DIALOG, 'd');
      this.bindShortcut(
          this.Shortcut.EXPAND_ALL_MESSAGES, 'x');
      this.bindShortcut(
          this.Shortcut.COLLAPSE_ALL_MESSAGES, 'z');
      this.bindShortcut(
          this.Shortcut.REFRESH_CHANGE, 'shift+r');
      this.bindShortcut(
          this.Shortcut.UP_TO_DASHBOARD, 'u');
      this.bindShortcut(
          this.Shortcut.UP_TO_CHANGE, 'u');
      this.bindShortcut(
          this.Shortcut.TOGGLE_DIFF_MODE, 'm');

      this.bindShortcut(
          this.Shortcut.NEXT_LINE, 'j', 'down');
      this.bindShortcut(
          this.Shortcut.PREV_LINE, 'k', 'up');
      this.bindShortcut(
          this.Shortcut.NEXT_CHUNK, 'n');
      this.bindShortcut(
          this.Shortcut.PREV_CHUNK, 'p');
      this.bindShortcut(
          this.Shortcut.EXPAND_ALL_DIFF_CONTEXT, 'shift+x');
      this.bindShortcut(
          this.Shortcut.NEXT_COMMENT_THREAD, 'shift+n');
      this.bindShortcut(
          this.Shortcut.PREV_COMMENT_THREAD, 'shift+p');
      this.bindShortcut(
          this.Shortcut.EXPAND_ALL_COMMENT_THREADS, this.DOC_ONLY, 'e');
      this.bindShortcut(
          this.Shortcut.COLLAPSE_ALL_COMMENT_THREADS,
          this.DOC_ONLY, 'shift+e');
      this.bindShortcut(
          this.Shortcut.LEFT_PANE, 'shift+left');
      this.bindShortcut(
          this.Shortcut.RIGHT_PANE, 'shift+right');
      this.bindShortcut(
          this.Shortcut.TOGGLE_LEFT_PANE, 'shift+a');
      this.bindShortcut(
          this.Shortcut.NEW_COMMENT, 'c');
      this.bindShortcut(
          this.Shortcut.SAVE_COMMENT,
          'ctrl+enter', 'meta+enter', 'ctrl+s', 'meta+s');
      this.bindShortcut(
          this.Shortcut.OPEN_DIFF_PREFS, ',');
      this.bindShortcut(
          this.Shortcut.TOGGLE_DIFF_REVIEWED, 'r');

      this.bindShortcut(
          this.Shortcut.NEXT_FILE, ']');
      this.bindShortcut(
          this.Shortcut.PREV_FILE, '[');
      this.bindShortcut(
          this.Shortcut.NEXT_FILE_WITH_COMMENTS, 'shift+j');
      this.bindShortcut(
          this.Shortcut.PREV_FILE_WITH_COMMENTS, 'shift+k');
      this.bindShortcut(
          this.Shortcut.CURSOR_NEXT_FILE, 'j', 'down');
      this.bindShortcut(
          this.Shortcut.CURSOR_PREV_FILE, 'k', 'up');
      this.bindShortcut(
          this.Shortcut.OPEN_FILE, 'o', 'enter');
      this.bindShortcut(
          this.Shortcut.TOGGLE_FILE_REVIEWED, 'r');
      this.bindShortcut(
          this.Shortcut.NEXT_UNREVIEWED_FILE, 'shift+m');
      this.bindShortcut(
          this.Shortcut.TOGGLE_ALL_INLINE_DIFFS, 'shift+i:keyup');
      this.bindShortcut(
          this.Shortcut.TOGGLE_INLINE_DIFF, 'i:keyup');

      this.bindShortcut(
          this.Shortcut.OPEN_FIRST_FILE, ']');
      this.bindShortcut(
          this.Shortcut.OPEN_LAST_FILE, '[');

      this.bindShortcut(
          this.Shortcut.SEARCH, '/');
    },

    _accountChanged(account) {
      if (!account) { return; }

      // Preferences are cached when a user is logged in; warm them.
      this.$.restAPI.getPreferences();
      this.$.restAPI.getDiffPreferences();
      this.$.restAPI.getEditPreferences();
      this.$.errorManager.knownAccountId =
          this._account && this._account._account_id || null;
    },

    _viewChanged(view) {
      this.$.errorView.classList.remove('show');
      this.set('_showChangeListView', view === Gerrit.Nav.View.SEARCH);
      this.set('_showDashboardView', view === Gerrit.Nav.View.DASHBOARD);
      this.set('_showChangeView', view === Gerrit.Nav.View.CHANGE);
      this.set('_showDiffView', view === Gerrit.Nav.View.DIFF);
      this.set('_showSettingsView', view === Gerrit.Nav.View.SETTINGS);
      this.set('_showAdminView', view === Gerrit.Nav.View.ADMIN ||
          view === Gerrit.Nav.View.GROUP || view === Gerrit.Nav.View.REPO);
      this.set('_showCLAView', view === Gerrit.Nav.View.AGREEMENTS);
      this.set('_showEditorView', view === Gerrit.Nav.View.EDIT);
      const isPluginScreen = view === Gerrit.Nav.View.PLUGIN_SCREEN;
      this.set('_showPluginScreen', false);
      // Navigation within plugin screens does not restamp gr-endpoint-decorator
      // because _showPluginScreen value does not change. To force restamp,
      // change _showPluginScreen value between true and false.
      if (isPluginScreen) {
        this.async(() => this.set('_showPluginScreen', true), 1);
      }
      this.set('_showDocumentationSearch',
          view === Gerrit.Nav.View.DOCUMENTATION_SEARCH);
      if (this.params.justRegistered) {
        this.$.registrationOverlay.open();
        this.$.registrationDialog.loadData().then(() => {
          this.$.registrationOverlay.refit();
        });
      }
      this.$.header.unfloat();
    },

    _handlePageError(e) {
      const props = [
        '_showChangeListView',
        '_showDashboardView',
        '_showChangeView',
        '_showDiffView',
        '_showSettingsView',
        '_showAdminView',
      ];
      for (const showProp of props) {
        this.set(showProp, false);
      }

      this.$.errorView.classList.add('show');
      const response = e.detail.response;
      const err = {text: [response.status, response.statusText].join(' ')};
      if (response.status === 404) {
        err.emoji = '¯\\_(ツ)_/¯';
        this._lastError = err;
      } else {
        err.emoji = 'o_O';
        response.text().then(text => {
          err.moreInfo = text;
          this._lastError = err;
        });
      }
    },

    _handleLocationChange(e) {
      const hash = e.detail.hash.substring(1);
      let pathname = e.detail.pathname;
      if (pathname.startsWith('/c/') && parseInt(hash, 10) > 0) {
        pathname += '@' + hash;
      }
      this.set('_path', pathname);
    },

    _paramsChanged(paramsRecord) {
      const params = paramsRecord.base;
      const viewsToCheck = [Gerrit.Nav.View.SEARCH, Gerrit.Nav.View.DASHBOARD];
      if (viewsToCheck.includes(params.view)) {
        this.set('_lastSearchPage', location.pathname);
      }
    },

    _handleTitleChange(e) {
      if (e.detail.title) {
        document.title = e.detail.title + ' · Gerrit Code Review';
      } else {
        document.title = '';
      }
    },

    _showKeyboardShortcuts(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }
      this.$.keyboardShortcuts.open();
    },

    _handleKeyboardShortcutDialogClose() {
      this.$.keyboardShortcuts.close();
    },

    _handleAccountDetailUpdate(e) {
      this.$.mainHeader.reload();
      if (this.params.view === Gerrit.Nav.View.SETTINGS) {
        this.$$('gr-settings-view').reloadAccountDetail();
      }
    },

    _handleRegistrationDialogClose(e) {
      this.params.justRegistered = false;
      this.$.registrationOverlay.close();
    },

    _goToOpenedChanges() {
      Gerrit.Nav.navigateToStatusSearch('open');
    },

    _goToUserDashboard() {
      Gerrit.Nav.navigateToUserDashboard();
    },

    _goToMergedChanges() {
      Gerrit.Nav.navigateToStatusSearch('merged');
    },

    _goToAbandonedChanges() {
      Gerrit.Nav.navigateToStatusSearch('abandoned');
    },

    _goToWatchedChanges() {
      // The query is hardcoded, and doesn't respect custom menu entries
      Gerrit.Nav.navigateToSearchQuery('is:watched is:open');
    },

    _computePluginScreenName({plugin, screen}) {
      if (!plugin || !screen) return '';
      return `${plugin}-screen-${screen}`;
    },

    _logWelcome() {
      console.group('Runtime Info');
      console.log('Gerrit UI (PolyGerrit)');
      console.log(`Gerrit Server Version: ${this._version}`);
      if (window.VERSION_INFO) {
        console.log(`UI Version Info: ${window.VERSION_INFO}`);
      }
      if (this._feedbackUrl) {
        console.log(`Please file bugs and feedback at: ${this._feedbackUrl}`);
      }
      console.groupEnd();
    },

    /**
     * Intercept RPC log events emitted by REST API interfaces.
     * Note: the REST API interface cannot use gr-reporting directly because
     * that would create a cyclic dependency.
     */
    _handleRpcLog(e) {
      this.$.reporting.reportRpcTiming(e.detail.anonymizedUrl,
          e.detail.elapsed);
    },

    _mobileSearchToggle(e) {
      this.mobileSearch = !this.mobileSearch;
    },

    getThemeEndpoint() {
      // For now, we only have dark mode and light mode
      return window.localStorage.getItem('dark-theme') ?
        'app-theme-dark' :
        'app-theme-light';
    },
  });
})();
