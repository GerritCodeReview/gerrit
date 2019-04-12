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

  class GrApp extends Polymer.Element {
    static get template() {
      return Polymer.html`
        <style include="shared-styles">
          :host {
            background-color: var(--view-background-color);
            display: flex;
            flex-direction: column;
            min-height: 100%;
          }
          gr-fixed-panel {
            /**
             * This one should be greater that the z-index in gr-diff-view
             * because gr-main-header contains overlay.
             */
            z-index: 10;
          }
          gr-main-header,
          footer {
            color: var(--primary-text-color);
          }
          gr-main-header {
            background-color: var(--header-background-color);
            padding: 0 var(--default-horizontal-margin);
            border-bottom: 1px solid var(--border-color);
          }
          gr-main-header.shadow {
            /* Make it obvious for shadow dom testing */
            border-bottom: 1px solid pink;
          }
          footer {
            background-color: var(--footer-background-color);
            border-top: 1px solid var(--border-color);
            display: flex;
            justify-content: space-between;
            padding: .5rem var(--default-horizontal-margin);
            z-index: 100;
          }
          main {
            flex: 1;
            padding-bottom: 2em;
            position: relative;
          }
          .errorView {
            align-items: center;
            display: none;
            flex-direction: column;
            justify-content: center;
            position: absolute;
            top: 0;
            right: 0;
            bottom: 0;
            left: 0;
          }
          .errorView.show {
            display: flex;
          }
          .errorEmoji {
            font-size: 2.6rem;
          }
          .errorText,
          .errorMoreInfo {
            margin-top: .75em;
          }
          .errorText {
            font-size: 1.2rem;
          }
          .errorMoreInfo {
            color: var(--deemphasized-text-color);
          }
          .feedback {
            color: var(--error-text-color);
          }
        </style>
        <gr-fixed-panel id="header">
          <gr-main-header
              id="mainHeader"
              search-query="{{params.query}}"
              class$="[[_computeShadowClass(_isShadowDom)]]">
          </gr-main-header>
        </gr-fixed-panel>
        <main>
          <template is="dom-if" if="[[_showChangeListView]]" restamp="true">
            <gr-change-list-view
                params="[[params]]"
                account="[[_account]]"
                view-state="{{_viewState.changeListView}}"></gr-change-list-view>
          </template>
          <template is="dom-if" if="[[_showDashboardView]]" restamp="true">
            <gr-dashboard-view
                account="[[_account]]"
                params="[[params]]"
                view-state="{{_viewState.dashboardView}}"></gr-dashboard-view>
          </template>
          <template is="dom-if" if="[[_showChangeView]]" restamp="true">
            <gr-change-view
                params="[[params]]"
                view-state="{{_viewState.changeView}}"
                back-page="[[_lastSearchPage]]"></gr-change-view>
          </template>
          <template is="dom-if" if="[[_showEditorView]]" restamp="true">
            <gr-editor-view
                params="[[params]]"></gr-editor-view>
          </template>
          <template is="dom-if" if="[[_showDiffView]]" restamp="true">
              <gr-diff-view
                  params="[[params]]"
                  change-view-state="{{_viewState.changeView}}"></gr-diff-view>
            </template>
          <template is="dom-if" if="[[_showSettingsView]]" restamp="true">
            <gr-settings-view
                params="[[params]]"
                on-account-detail-update="_handleAccountDetailUpdate">
            </gr-settings-view>
          </template>
          <template is="dom-if" if="[[_showAdminView]]" restamp="true">
            <gr-admin-view path="[[_path]]"
                params=[[params]]></gr-admin-view>
          </template>
          <template is="dom-if" if="[[_showPluginScreen]]" restamp="true">
            <gr-endpoint-decorator name="[[_pluginScreenName]]">
              <gr-endpoint-param name="token" value="[[params.screen]]"></gr-endpoint-param>
            </gr-endpoint-decorator>
          </template>
          <template is="dom-if" if="[[_showCLAView]]" restamp="true">
            <gr-cla-view></gr-cla-view>
          </template>
          <template is="dom-if" if="[[_showDocumentationSearch]]" restamp="true">
            <gr-documentation-search
                params="[[params]]">
            </gr-documentation-search>
          </template>
          <div id="errorView" class="errorView">
            <div class="errorEmoji">[[_lastError.emoji]]</div>
            <div class="errorText">[[_lastError.text]]</div>
            <div class="errorMoreInfo">[[_lastError.moreInfo]]</div>
          </div>
        </main>
        <footer r="contentinfo" class$="[[_computeShadowClass(_isShadowDom)]]">
          <div>
            Powered by <a href="https://www.gerritcodereview.com/" rel="noopener"
            target="_blank">Gerrit Code Review</a>
            ([[_version]])
          </div>
          <div>
            <a class="feedback"
                href$="[[_feedbackUrl]]"
                rel="noopener" target="_blank">Send feedback</a>
            | Press &ldquo;?&rdquo; for keyboard shortcuts
          </div>
        </footer>
        <gr-overlay id="keyboardShortcuts" with-backdrop>
          <gr-keyboard-shortcuts-dialog
              view="[[params.view]]"
              on-close="_handleKeyboardShortcutDialogClose"></gr-keyboard-shortcuts-dialog>
        </gr-overlay>
        <gr-overlay id="registrationOverlay" with-backdrop>
          <gr-registration-dialog
              id="registrationDialog"
              settings-url="[[_settingsUrl]]"
              on-account-detail-update="_handleAccountDetailUpdate"
              on-close="_handleRegistrationDialogClose">
          </gr-registration-dialog>
        </gr-overlay>
        <gr-endpoint-decorator name="plugin-overlay"></gr-endpoint-decorator>
        <gr-error-manager id="errorManager"></gr-error-manager>
        <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
        <gr-reporting id="reporting"></gr-reporting>
        <gr-router id="router"></gr-router>
        <gr-plugin-host id="plugins"
            config="[[_serverConfig]]">
        </gr-plugin-host>
        <gr-lib-loader id="libLoader"></gr-lib-loader>
        <gr-external-style id="externalStyle" name="app-theme"></gr-external-style>
      `;
    }

    static get is() { return 'gr-app'; }

    static get properties() { return {
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
      _isShadowDom: Boolean,
      _pluginScreenName: {
        type: String,
        computed: '_computePluginScreenName(params)',
      },
      _settingsUrl: String,
      _feedbackUrl: {
        type: String,
        value: 'https://bugs.chromium.org/p/gerrit/issues/entry' +
          '?template=PolyGerrit%20Issue',
      },
    }}

    static get observers() { return [
      '_viewChanged(params.view)',
      '_paramsChanged(params.*)',
    ]}

    constructor() {
      super();
    }

    listeners: {
      'page-error': '_handlePageError',
      'title-change': '_handleTitleChange',
      'location-change': '_handleLocationChange',
      'rpc-log': '_handleRpcLog',
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.KeyboardShortcutBehavior,
    ],

    keyboardShortcuts() {
      return {
        [this.Shortcut.OPEN_SHORTCUT_HELP_DIALOG]: '_showKeyboardShortcuts',
        [this.Shortcut.GO_TO_OPENED_CHANGES]: '_goToOpenedChanges',
        [this.Shortcut.GO_TO_MERGED_CHANGES]: '_goToMergedChanges',
        [this.Shortcut.GO_TO_ABANDONED_CHANGES]: '_goToAbandonedChanges',
      };
    },

    created() {
      super.created();

      this._bindKeyboardShortcuts();
    },

    ready() {
      super.ready();

      this._isShadowDom = Polymer.Settings.useShadow;
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
        this.$.libLoader.getDarkTheme().then(module => {
          Polymer.dom(this.root).appendChild(module);
        });
      }

      // Note: this is evaluated here to ensure that it only happens after the
      // router has been initialized. @see Issue 7837
      this._settingsUrl = Gerrit.Nav.getUrlForSettings();

      this.$.reporting.appStarted(document.visibilityState === 'hidden');

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

      this.bindShortcut(
          this.Shortcut.OPEN_SHORTCUT_HELP_DIALOG, '?');
      this.bindShortcut(
          this.Shortcut.GO_TO_OPENED_CHANGES, this.GO_KEY, 'o');
      this.bindShortcut(
          this.Shortcut.GO_TO_MERGED_CHANGES, this.GO_KEY, 'm');
      this.bindShortcut(
          this.Shortcut.GO_TO_ABANDONED_CHANGES, this.GO_KEY, 'a');

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

    _computeShadowClass(isShadowDom) {
      return isShadowDom ? 'shadow' : '';
    },

    _goToOpenedChanges() {
      Gerrit.Nav.navigateToStatusSearch('open');
    },

    _goToMergedChanges() {
      Gerrit.Nav.navigateToStatusSearch('merged');
    },

    _goToAbandonedChanges() {
      Gerrit.Nav.navigateToStatusSearch('abandoned');
    },

    _computePluginScreenName({plugin, screen}) {
      return Gerrit._getPluginScreenName(plugin, screen);
    },

    _logWelcome() {
      console.group('Runtime Info');
      console.log('Gerrit UI (PolyGerrit)');
      console.log(`Gerrit Server Version: ${this._version}`);
      if (window.VERSION_INFO) {
        console.log(`UI Version Info: ${window.VERSION_INFO}`);
      }
      const renderTime = new Date(window.performance.timing.loadEventStart);
      console.log(`Document loaded at: ${renderTime}`);
      console.log(`Please file bugs and feedback at: ${this._feedbackUrl}`);
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
  }

  customElements.define(GrApp.is, GrApp);
})();
