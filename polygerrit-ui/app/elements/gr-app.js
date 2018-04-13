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

  // The maximum age of a keydown event to be used in a jump navigation. This is
  // only for cases when the keyup event is lost.
  const G_KEY_TIMEOUT_MS = 1000;

  // Eagerly render Polymer components when backgrounded. (Skips
  // requestAnimationFrame.)
  // @see https://github.com/Polymer/polymer/issues/3851
  // TODO: Reassess after Polymer 2.0 upgrade.
  // @see Issue 4699
  Polymer.RenderStatus._makeReady();

  Polymer({
    is: 'gr-app',

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
    },

    listeners: {
      'page-error': '_handlePageError',
      'title-change': '_handleTitleChange',
      'location-change': '_handleLocationChange',
    },

    observers: [
      '_viewChanged(params.view)',
      '_paramsChanged(params.*)',
    ],

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.KeyboardShortcutBehavior,
    ],

    keyBindings: {
      '?': '_showKeyboardShortcuts',
      'g:keydown': '_gKeyDown',
      'g:keyup': '_gKeyUp',
      'a m o': '_jumpKeyPressed',
    },

    ready() {
      this._isShadowDom = Polymer.Settings.useShadow;
      this.$.router.start();

      this.$.restAPI.getAccount().then(account => {
        this._account = account;
      });
      this.$.restAPI.getConfig().then(config => {
        this._serverConfig = config;
      });
      this.$.restAPI.getVersion().then(version => {
        this._version = version;
      });

      // Note: this is evaluated here to ensure that it only happens after the
      // router has been initialized. @see Issue 7837
      this._settingsUrl = Gerrit.Nav.getUrlForSettings();

      this.$.reporting.appStarted();
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
      if (this.params.justRegistered) {
        this.$.registration.open();
      }
      this.$.header.unfloat();
    },

    _computeShowGwtUiLink(config) {
      return config.gerrit.web_uis && config.gerrit.web_uis.includes('GWT');
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
      this.$.registration.close();
    },

    _computeShadowClass(isShadowDom) {
      return isShadowDom ? 'shadow' : '';
    },

    _gKeyDown(e) {
      if (this.modifierPressed(e)) { return; }
      this._lastGKeyPressTimestamp = Date.now();
    },

    _gKeyUp() {
      this._lastGKeyPressTimestamp = null;
    },

    _jumpKeyPressed(e) {
      if (!this._lastGKeyPressTimestamp ||
          (Date.now() - this._lastGKeyPressTimestamp > G_KEY_TIMEOUT_MS) ||
          this.shouldSuppressKeyboardShortcut(e)) { return; }
      e.preventDefault();

      let status = null;
      if (e.detail.key === 'a') {
        status = 'abandoned';
      } else if (e.detail.key === 'm') {
        status = 'merged';
      } else if (e.detail.key === 'o') {
        status = 'open';
      }
      if (status !== null) {
        Gerrit.Nav.navigateToStatusSearch(status);
      }
    },

    _computePluginScreenName({plugin, screen}) {
      return Gerrit._getPluginScreenName(plugin, screen);
    },
  });
})();
