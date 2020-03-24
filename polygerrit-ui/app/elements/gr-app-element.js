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
import '../scripts/bundled-polymer.js';
import '../behaviors/base-url-behavior/base-url-behavior.js';
import '../behaviors/keyboard-shortcut-behavior/keyboard-shortcut-behavior.js';
import '../styles/shared-styles.js';
import '../styles/themes/app-theme.js';
import './admin/gr-admin-view/gr-admin-view.js';
import './documentation/gr-documentation-search/gr-documentation-search.js';
import './change-list/gr-change-list-view/gr-change-list-view.js';
import './change-list/gr-dashboard-view/gr-dashboard-view.js';
import './change/gr-change-view/gr-change-view.js';
import './core/gr-error-manager/gr-error-manager.js';
import './core/gr-keyboard-shortcuts-dialog/gr-keyboard-shortcuts-dialog.js';
import './core/gr-main-header/gr-main-header.js';
import './core/gr-navigation/gr-navigation.js';
import './core/gr-reporting/gr-reporting.js';
import './core/gr-router/gr-router.js';
import './core/gr-smart-search/gr-smart-search.js';
import './diff/gr-diff-view/gr-diff-view.js';
import './edit/gr-editor-view/gr-editor-view.js';
import './plugins/gr-endpoint-decorator/gr-endpoint-decorator.js';
import './plugins/gr-endpoint-param/gr-endpoint-param.js';
import './plugins/gr-external-style/gr-external-style.js';
import './plugins/gr-plugin-host/gr-plugin-host.js';
import './settings/gr-cla-view/gr-cla-view.js';
import './settings/gr-registration-dialog/gr-registration-dialog.js';
import './settings/gr-settings-view/gr-settings-view.js';
import './shared/gr-fixed-panel/gr-fixed-panel.js';
import './shared/gr-lib-loader/gr-lib-loader.js';
import './shared/gr-rest-api-interface/gr-rest-api-interface.js';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import moment from 'moment/src/moment.js';
self.moment = moment;
import {htmlTemplate} from './gr-app-element_html.js';

/**
 * @appliesMixin Gerrit.BaseUrlMixin
 * @appliesMixin Gerrit.KeyboardShortcutMixin
 * @extends Polymer.Element
 */
class GrAppElement extends mixinBehaviors( [
  Gerrit.BaseUrlBehavior,
  Gerrit.KeyboardShortcutBehavior,
], GestureEventListeners(
    LegacyElementMixin(
        PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-app-element'; }
  /**
   * Fired when the URL location changes.
   *
   * @event location-change
   */

  static get properties() {
    return {
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
       *
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

      /**
       * Other elements in app must open this URL when
       * user login is required.
       */
      _loginUrl: {
        type: String,
        value: '/login',
      },
    };
  }

  static get observers() {
    return [
      '_viewChanged(params.view)',
      '_paramsChanged(params.*)',
    ];
  }

  keyboardShortcuts() {
    return {
      [this.Shortcut.OPEN_SHORTCUT_HELP_DIALOG]: '_showKeyboardShortcuts',
      [this.Shortcut.GO_TO_USER_DASHBOARD]: '_goToUserDashboard',
      [this.Shortcut.GO_TO_OPENED_CHANGES]: '_goToOpenedChanges',
      [this.Shortcut.GO_TO_MERGED_CHANGES]: '_goToMergedChanges',
      [this.Shortcut.GO_TO_ABANDONED_CHANGES]: '_goToAbandonedChanges',
      [this.Shortcut.GO_TO_WATCHED_CHANGES]: '_goToWatchedChanges',
    };
  }

  /** @override */
  created() {
    super.created();
    this._bindKeyboardShortcuts();
    this.addEventListener('page-error',
        e => this._handlePageError(e));
    this.addEventListener('title-change',
        e => this._handleTitleChange(e));
    this.addEventListener('location-change',
        e => this._handleLocationChange(e));
    this.addEventListener('rpc-log',
        e => this._handleRpcLog(e));
    this.addEventListener('shortcut-triggered',
        e => this._handleShortcutTriggered(e));
  }

  /** @override */
  ready() {
    super.ready();
    this._updateLoginUrl();
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
  }

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
        this.Shortcut.TOGGLE_CHANGE_REVIEWED, 'r:keyup');
    this.bindShortcut(
        this.Shortcut.TOGGLE_CHANGE_STAR, 's:keyup');
    this.bindShortcut(
        this.Shortcut.REFRESH_CHANGE_LIST, 'shift+r:keyup');
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
        this.Shortcut.REFRESH_CHANGE, 'shift+r:keyup');
    this.bindShortcut(
        this.Shortcut.UP_TO_DASHBOARD, 'u');
    this.bindShortcut(
        this.Shortcut.UP_TO_CHANGE, 'u');
    this.bindShortcut(
        this.Shortcut.TOGGLE_DIFF_MODE, 'm:keyup');

    this.bindShortcut(
        this.Shortcut.NEXT_LINE, 'j', 'down');
    this.bindShortcut(
        this.Shortcut.PREV_LINE, 'k', 'up');
    if (this._isCursorManagerSupportMoveToVisibleLine()) {
      this.bindShortcut(
          this.Shortcut.VISIBLE_LINE, '.');
    }
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
        this.Shortcut.TOGGLE_DIFF_REVIEWED, 'r:keyup');

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
        this.Shortcut.TOGGLE_FILE_REVIEWED, 'r:keyup');
    this.bindShortcut(
        this.Shortcut.NEXT_UNREVIEWED_FILE, 'shift+m');
    this.bindShortcut(
        this.Shortcut.TOGGLE_ALL_INLINE_DIFFS, 'shift+i:keyup');
    this.bindShortcut(
        this.Shortcut.TOGGLE_INLINE_DIFF, 'i:keyup');
    this.bindShortcut(
        this.Shortcut.TOGGLE_BLAME, 'b');

    this.bindShortcut(
        this.Shortcut.OPEN_FIRST_FILE, ']');
    this.bindShortcut(
        this.Shortcut.OPEN_LAST_FILE, '[');

    this.bindShortcut(
        this.Shortcut.SEARCH, '/');
  }

  _isCursorManagerSupportMoveToVisibleLine() {
    // This method is a copy-paste from the
    // method _isIntersectionObserverSupported of gr-cursor-manager.js
    // It is better share this method with gr-cursor-manager,
    // but doing it require a lot if changes instead of 1-line copied code
    return 'IntersectionObserver' in window;
  }

  _accountChanged(account) {
    if (!account) { return; }

    // Preferences are cached when a user is logged in; warm them.
    this.$.restAPI.getPreferences();
    this.$.restAPI.getDiffPreferences();
    this.$.restAPI.getEditPreferences();
    this.$.errorManager.knownAccountId =
        this._account && this._account._account_id || null;
  }

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
  }

  _handleShortcutTriggered(event) {
    const {event: e, goKey} = event.detail;
    // eg: {key: "k:keydown", ..., from: "gr-diff-view"}
    let key = `${e.key}:${e.type}`;
    if (goKey) key = 'g+' + key;
    if (e.shiftKey) key = 'shift+' + key;
    if (e.ctrlKey) key = 'ctrl+' + key;
    if (e.metaKey) key = 'meta+' + key;
    if (e.altKey) key = 'alt+' + key;
    this.$.reporting.reportInteraction('shortcut-triggered', {
      key,
      from: event.path && event.path[0]
        && event.path[0].nodeName || 'unknown',
    });
  }

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
  }

  _handleLocationChange(e) {
    this._updateLoginUrl();

    const hash = e.detail.hash.substring(1);
    let pathname = e.detail.pathname;
    if (pathname.startsWith('/c/') && parseInt(hash, 10) > 0) {
      pathname += '@' + hash;
    }
    this.set('_path', pathname);
  }

  _updateLoginUrl() {
    const baseUrl = this.getBaseUrl();
    if (baseUrl) {
      // Strip the canonical path from the path since needing canonical in
      // the path is uneeded and breaks the url.
      this._loginUrl = baseUrl + '/login/' + encodeURIComponent(
          '/' + window.location.pathname.substring(baseUrl.length) +
          window.location.search +
          window.location.hash);
    } else {
      this._loginUrl = '/login/' + encodeURIComponent(
          window.location.pathname +
          window.location.search +
          window.location.hash);
    }
  }

  _paramsChanged(paramsRecord) {
    const params = paramsRecord.base;
    const viewsToCheck = [Gerrit.Nav.View.SEARCH, Gerrit.Nav.View.DASHBOARD];
    if (viewsToCheck.includes(params.view)) {
      this.set('_lastSearchPage', location.pathname);
    }
  }

  _handleTitleChange(e) {
    if (e.detail.title) {
      document.title = e.detail.title + ' · Gerrit Code Review';
    } else {
      document.title = '';
    }
  }

  _showKeyboardShortcuts(e) {
    // same shortcut should close the dialog if pressed again
    // when dialog is open
    if (this.$.keyboardShortcuts.opened) {
      this.$.keyboardShortcuts.close();
      return;
    }
    if (this.shouldSuppressKeyboardShortcut(e)) { return; }
    this.$.keyboardShortcuts.open();
  }

  _handleKeyboardShortcutDialogClose() {
    this.$.keyboardShortcuts.close();
  }

  _handleAccountDetailUpdate(e) {
    this.$.mainHeader.reload();
    if (this.params.view === Gerrit.Nav.View.SETTINGS) {
      this.shadowRoot.querySelector('gr-settings-view').reloadAccountDetail();
    }
  }

  _handleRegistrationDialogClose(e) {
    this.params.justRegistered = false;
    this.$.registrationOverlay.close();
  }

  _goToOpenedChanges() {
    Gerrit.Nav.navigateToStatusSearch('open');
  }

  _goToUserDashboard() {
    Gerrit.Nav.navigateToUserDashboard();
  }

  _goToMergedChanges() {
    Gerrit.Nav.navigateToStatusSearch('merged');
  }

  _goToAbandonedChanges() {
    Gerrit.Nav.navigateToStatusSearch('abandoned');
  }

  _goToWatchedChanges() {
    // The query is hardcoded, and doesn't respect custom menu entries
    Gerrit.Nav.navigateToSearchQuery('is:watched is:open');
  }

  _computePluginScreenName({plugin, screen}) {
    if (!plugin || !screen) return '';
    return `${plugin}-screen-${screen}`;
  }

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
  }

  /**
   * Intercept RPC log events emitted by REST API interfaces.
   * Note: the REST API interface cannot use gr-reporting directly because
   * that would create a cyclic dependency.
   */
  _handleRpcLog(e) {
    this.$.reporting.reportRpcTiming(e.detail.anonymizedUrl,
        e.detail.elapsed);
  }

  _mobileSearchToggle(e) {
    this.mobileSearch = !this.mobileSearch;
  }

  getThemeEndpoint() {
    // For now, we only have dark mode and light mode
    return window.localStorage.getItem('dark-theme') ?
      'app-theme-dark' :
      'app-theme-light';
  }
}

customElements.define(GrAppElement.is, GrAppElement);
