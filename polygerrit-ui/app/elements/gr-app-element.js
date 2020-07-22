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
import '../styles/shared-styles.js';
import '../styles/themes/app-theme.js';
import {applyTheme as applyDarkTheme} from '../styles/themes/dark-theme.js';
import './admin/gr-admin-view/gr-admin-view.js';
import './documentation/gr-documentation-search/gr-documentation-search.js';
import './change-list/gr-change-list-view/gr-change-list-view.js';
import './change-list/gr-dashboard-view/gr-dashboard-view.js';
import './change/gr-change-view/gr-change-view.js';
import './core/gr-error-manager/gr-error-manager.js';
import './core/gr-keyboard-shortcuts-dialog/gr-keyboard-shortcuts-dialog.js';
import './core/gr-main-header/gr-main-header.js';
import './core/gr-router/gr-router.js';
import './core/gr-smart-search/gr-smart-search.js';
import './diff/gr-diff-view/gr-diff-view.js';
import './edit/gr-editor-view/gr-editor-view.js';
import './plugins/gr-endpoint-decorator/gr-endpoint-decorator.js';
import './plugins/gr-endpoint-param/gr-endpoint-param.js';
import './plugins/gr-endpoint-slot/gr-endpoint-slot.js';
import './plugins/gr-external-style/gr-external-style.js';
import './plugins/gr-plugin-host/gr-plugin-host.js';
import './settings/gr-cla-view/gr-cla-view.js';
import './settings/gr-registration-dialog/gr-registration-dialog.js';
import './settings/gr-settings-view/gr-settings-view.js';
import './shared/gr-fixed-panel/gr-fixed-panel.js';
import './shared/gr-lib-loader/gr-lib-loader.js';
import './shared/gr-rest-api-interface/gr-rest-api-interface.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-app-element_html.js';
import {getBaseUrl} from '../utils/url-util.js';
import {
  KeyboardShortcutMixin,
  Shortcut,
  SPECIAL_SHORTCUT,
} from '../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin.js';
import {GerritNav} from './core/gr-navigation/gr-navigation.js';
import {appContext} from '../services/app-context.js';

/**
 * @extends PolymerElement
 */
class GrAppElement extends KeyboardShortcutMixin(
    GestureEventListeners(
        LegacyElementMixin(PolymerElement))) {
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
      [Shortcut.OPEN_SHORTCUT_HELP_DIALOG]: '_showKeyboardShortcuts',
      [Shortcut.GO_TO_USER_DASHBOARD]: '_goToUserDashboard',
      [Shortcut.GO_TO_OPENED_CHANGES]: '_goToOpenedChanges',
      [Shortcut.GO_TO_MERGED_CHANGES]: '_goToMergedChanges',
      [Shortcut.GO_TO_ABANDONED_CHANGES]: '_goToAbandonedChanges',
      [Shortcut.GO_TO_WATCHED_CHANGES]: '_goToWatchedChanges',
    };
  }

  constructor() {
    super();
    this.reporting = appContext.reportingService;
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
    // Ideally individual views should handle this event and respond with a soft
    // reload. This is a catch-all for all views that cannot or have not
    // implemented that.
    this.addEventListener('reload', e => window.location.reload());
  }

  /** @override */
  ready() {
    super.ready();
    this._updateLoginUrl();
    this.reporting.appStarted();
    this.$.router.start();

    this.$.restAPI.getAccount().then(account => {
      this._account = account;
      const role = account ? 'user' : 'guest';
      this.reporting.reportLifeCycle(`Started as ${role}`);
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
      applyDarkTheme();
    }

    // Note: this is evaluated here to ensure that it only happens after the
    // router has been initialized. @see Issue 7837
    this._settingsUrl = GerritNav.getUrlForSettings();

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
    this.bindShortcut(Shortcut.SEND_REPLY,
        SPECIAL_SHORTCUT.DOC_ONLY, 'ctrl+enter', 'meta+enter');
    this.bindShortcut(Shortcut.EMOJI_DROPDOWN,
        SPECIAL_SHORTCUT.DOC_ONLY, ':');

    this.bindShortcut(
        Shortcut.OPEN_SHORTCUT_HELP_DIALOG, '?');
    this.bindShortcut(
        Shortcut.GO_TO_USER_DASHBOARD, SPECIAL_SHORTCUT.GO_KEY, 'i');
    this.bindShortcut(
        Shortcut.GO_TO_OPENED_CHANGES, SPECIAL_SHORTCUT.GO_KEY, 'o');
    this.bindShortcut(
        Shortcut.GO_TO_MERGED_CHANGES, SPECIAL_SHORTCUT.GO_KEY, 'm');
    this.bindShortcut(
        Shortcut.GO_TO_ABANDONED_CHANGES, SPECIAL_SHORTCUT.GO_KEY, 'a');
    this.bindShortcut(
        Shortcut.GO_TO_WATCHED_CHANGES, SPECIAL_SHORTCUT.GO_KEY, 'w');

    this.bindShortcut(
        Shortcut.CURSOR_NEXT_CHANGE, 'j');
    this.bindShortcut(
        Shortcut.CURSOR_PREV_CHANGE, 'k');
    this.bindShortcut(
        Shortcut.OPEN_CHANGE, 'o');
    this.bindShortcut(
        Shortcut.NEXT_PAGE, 'n', ']');
    this.bindShortcut(
        Shortcut.PREV_PAGE, 'p', '[');
    this.bindShortcut(
        Shortcut.TOGGLE_CHANGE_REVIEWED, 'r:keyup');
    this.bindShortcut(
        Shortcut.TOGGLE_CHANGE_STAR, 's:keyup');
    this.bindShortcut(
        Shortcut.REFRESH_CHANGE_LIST, 'shift+r:keyup');
    this.bindShortcut(
        Shortcut.EDIT_TOPIC, 't');

    this.bindShortcut(
        Shortcut.OPEN_REPLY_DIALOG, 'a:keyup');
    this.bindShortcut(
        Shortcut.OPEN_DOWNLOAD_DIALOG, 'd:keyup');
    this.bindShortcut(
        Shortcut.EXPAND_ALL_MESSAGES, 'x');
    this.bindShortcut(
        Shortcut.COLLAPSE_ALL_MESSAGES, 'z');
    this.bindShortcut(
        Shortcut.REFRESH_CHANGE, 'shift+r:keyup');
    this.bindShortcut(
        Shortcut.UP_TO_DASHBOARD, 'u');
    this.bindShortcut(
        Shortcut.UP_TO_CHANGE, 'u');
    this.bindShortcut(
        Shortcut.TOGGLE_DIFF_MODE, 'm:keyup');
    this.bindShortcut(
        Shortcut.DIFF_AGAINST_BASE, SPECIAL_SHORTCUT.V_KEY, 'down', 's');
    this.bindShortcut(
        Shortcut.DIFF_AGAINST_LATEST, SPECIAL_SHORTCUT.V_KEY, 'up', 'w');
    this.bindShortcut(
        Shortcut.DIFF_BASE_AGAINST_LEFT,
        SPECIAL_SHORTCUT.V_KEY, 'left', 'a');
    this.bindShortcut(
        Shortcut.DIFF_RIGHT_AGAINST_LATEST,
        SPECIAL_SHORTCUT.V_KEY, 'right', 'd');
    this.bindShortcut(
        Shortcut.DIFF_BASE_AGAINST_LATEST, SPECIAL_SHORTCUT.V_KEY, 'b');

    this.bindShortcut(
        Shortcut.NEXT_LINE, 'j', 'down');
    this.bindShortcut(
        Shortcut.PREV_LINE, 'k', 'up');
    if (this._isCursorManagerSupportMoveToVisibleLine()) {
      this.bindShortcut(
          Shortcut.VISIBLE_LINE, '.');
    }
    this.bindShortcut(
        Shortcut.NEXT_CHUNK, 'n');
    this.bindShortcut(
        Shortcut.PREV_CHUNK, 'p');
    this.bindShortcut(
        Shortcut.EXPAND_ALL_DIFF_CONTEXT, 'shift+x');
    this.bindShortcut(
        Shortcut.NEXT_COMMENT_THREAD, 'shift+n');
    this.bindShortcut(
        Shortcut.PREV_COMMENT_THREAD, 'shift+p');
    this.bindShortcut(
        Shortcut.EXPAND_ALL_COMMENT_THREADS,
        SPECIAL_SHORTCUT.DOC_ONLY, 'e');
    this.bindShortcut(
        Shortcut.COLLAPSE_ALL_COMMENT_THREADS,
        SPECIAL_SHORTCUT.DOC_ONLY, 'shift+e');
    this.bindShortcut(
        Shortcut.LEFT_PANE, 'shift+left');
    this.bindShortcut(
        Shortcut.RIGHT_PANE, 'shift+right');
    this.bindShortcut(
        Shortcut.TOGGLE_LEFT_PANE, 'shift+a');
    this.bindShortcut(
        Shortcut.NEW_COMMENT, 'c');
    this.bindShortcut(
        Shortcut.SAVE_COMMENT,
        'ctrl+enter', 'meta+enter', 'ctrl+s', 'meta+s');
    this.bindShortcut(
        Shortcut.OPEN_DIFF_PREFS, ',');
    this.bindShortcut(
        Shortcut.TOGGLE_DIFF_REVIEWED, 'r:keyup');

    this.bindShortcut(
        Shortcut.NEXT_FILE, ']');
    this.bindShortcut(
        Shortcut.PREV_FILE, '[');
    this.bindShortcut(
        Shortcut.NEXT_FILE_WITH_COMMENTS, 'shift+j');
    this.bindShortcut(
        Shortcut.PREV_FILE_WITH_COMMENTS, 'shift+k');
    this.bindShortcut(
        Shortcut.CURSOR_NEXT_FILE, 'j', 'down');
    this.bindShortcut(
        Shortcut.CURSOR_PREV_FILE, 'k', 'up');
    this.bindShortcut(
        Shortcut.OPEN_FILE, 'o', 'enter');
    this.bindShortcut(
        Shortcut.TOGGLE_FILE_REVIEWED, 'r:keyup');
    this.bindShortcut(
        Shortcut.NEXT_UNREVIEWED_FILE, 'shift+m');
    this.bindShortcut(
        Shortcut.TOGGLE_ALL_INLINE_DIFFS, 'shift+i:keyup');
    this.bindShortcut(
        Shortcut.TOGGLE_INLINE_DIFF, 'i:keyup');
    this.bindShortcut(
        Shortcut.TOGGLE_BLAME, 'b');
    this.bindShortcut(
        Shortcut.TOGGLE_HIDE_ALL_COMMENT_THREADS, 'h');

    this.bindShortcut(
        Shortcut.OPEN_FIRST_FILE, ']');
    this.bindShortcut(
        Shortcut.OPEN_LAST_FILE, '[');

    this.bindShortcut(
        Shortcut.SEARCH, '/');
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
    this.set('_showChangeListView', view === GerritNav.View.SEARCH);
    this.set('_showDashboardView', view === GerritNav.View.DASHBOARD);
    this.set('_showChangeView', view === GerritNav.View.CHANGE);
    this.set('_showDiffView', view === GerritNav.View.DIFF);
    this.set('_showSettingsView', view === GerritNav.View.SETTINGS);
    this.set('_showAdminView', view === GerritNav.View.ADMIN ||
        view === GerritNav.View.GROUP || view === GerritNav.View.REPO);
    this.set('_showCLAView', view === GerritNav.View.AGREEMENTS);
    this.set('_showEditorView', view === GerritNav.View.EDIT);
    const isPluginScreen = view === GerritNav.View.PLUGIN_SCREEN;
    this.set('_showPluginScreen', false);
    // Navigation within plugin screens does not restamp gr-endpoint-decorator
    // because _showPluginScreen value does not change. To force restamp,
    // change _showPluginScreen value between true and false.
    if (isPluginScreen) {
      this.async(() => this.set('_showPluginScreen', true), 1);
    }
    this.set('_showDocumentationSearch',
        view === GerritNav.View.DOCUMENTATION_SEARCH);
    if (this.params.justRegistered) {
      this.$.registrationOverlay.open();
      this.$.registrationDialog.loadData().then(() => {
        this.$.registrationOverlay.refit();
      });
    }
    this.$.header.unfloat();
  }

  _handleShortcutTriggered(event) {
    const {event: e, goKey, vKey} = event.detail;
    // eg: {key: "k:keydown", ..., from: "gr-diff-view"}
    let key = `${e.key}:${e.type}`;
    if (goKey) key = 'g+' + key;
    if (vKey) key = 'v+' + key;
    if (e.shiftKey) key = 'shift+' + key;
    if (e.ctrlKey) key = 'ctrl+' + key;
    if (e.metaKey) key = 'meta+' + key;
    if (e.altKey) key = 'alt+' + key;
    this.reporting.reportInteraction('shortcut-triggered', {
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
    const baseUrl = getBaseUrl();
    if (baseUrl) {
      // Strip the canonical path from the path since needing canonical in
      // the path is unneeded and breaks the url.
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
    const viewsToCheck = [GerritNav.View.SEARCH, GerritNav.View.DASHBOARD];
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

  handleShowKeyboardShortcuts() {
    this.$.keyboardShortcuts.open();
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
    if (this.params.view === GerritNav.View.SETTINGS) {
      this.shadowRoot.querySelector('gr-settings-view').reloadAccountDetail();
    }
  }

  _handleRegistrationDialogClose(e) {
    this.params.justRegistered = false;
    this.$.registrationOverlay.close();
  }

  _goToOpenedChanges() {
    GerritNav.navigateToStatusSearch('open');
  }

  _goToUserDashboard() {
    GerritNav.navigateToUserDashboard();
  }

  _goToMergedChanges() {
    GerritNav.navigateToStatusSearch('merged');
  }

  _goToAbandonedChanges() {
    GerritNav.navigateToStatusSearch('abandoned');
  }

  _goToWatchedChanges() {
    // The query is hardcoded, and doesn't respect custom menu entries
    GerritNav.navigateToSearchQuery('is:watched is:open');
  }

  _computePluginScreenName({plugin, screen}) {
    if (!plugin || !screen) return '';
    return `${plugin}-screen-${screen}`;
  }

  _logWelcome() {
    console.group('Runtime Info');
    console.info('Gerrit UI (PolyGerrit)');
    console.info(`Gerrit Server Version: ${this._version}`);
    if (window.VERSION_INFO) {
      console.info(`UI Version Info: ${window.VERSION_INFO}`);
    }
    if (this._feedbackUrl) {
      console.info(`Please file bugs and feedback at: ${this._feedbackUrl}`);
    }
    console.groupEnd();
  }

  /**
   * Intercept RPC log events emitted by REST API interfaces.
   * Note: the REST API interface cannot use gr-reporting directly because
   * that would create a cyclic dependency.
   */
  _handleRpcLog(e) {
    this.reporting.reportRpcTiming(e.detail.anonymizedUrl,
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
