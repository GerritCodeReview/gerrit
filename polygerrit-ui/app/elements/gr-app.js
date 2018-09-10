/**
@license
Copyright (C) 2015 The Android Open Source Project

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
import '../../@polymer/polymer/polymer-legacy.js';

import '../../polymer-resin/standalone/polymer-resin.js';
import '../behaviors/safe-types-behavior/safe-types-behavior.js';
import '../behaviors/base-url-behavior/base-url-behavior.js';
import '../behaviors/keyboard-shortcut-behavior/keyboard-shortcut-behavior.js';
import '../styles/shared-styles.js';
import '../styles/themes/app-theme.js';
import './admin/gr-admin-view/gr-admin-view.js';
import './change-list/gr-change-list-view/gr-change-list-view.js';
import './change-list/gr-dashboard-view/gr-dashboard-view.js';
import './change/gr-change-view/gr-change-view.js';
import './core/gr-error-manager/gr-error-manager.js';
import './core/gr-keyboard-shortcuts-dialog/gr-keyboard-shortcuts-dialog.js';
import './core/gr-main-header/gr-main-header.js';
import './core/gr-navigation/gr-navigation.js';
import './core/gr-reporting/gr-reporting.js';
import './core/gr-router/gr-router.js';
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
// This must be set prior to loading Polymer for the first time.
if (localStorage.getItem('USE_SHADOW_DOM') === 'true') {
  window.Polymer = {
    dom: 'shadow',
    passiveTouchGestures: true,
  };
} else if (!window.Polymer) {
  window.Polymer = {
    passiveTouchGestures: true,
  };
}
security.polymer_resin.install({
  allowedIdentifierPrefixes: [''],
  reportHandler: security.polymer_resin.CONSOLE_LOGGING_REPORT_HANDLER,
  safeTypesBridge: Gerrit.SafeTypes.safeTypesBridge,
});
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
  _template: Polymer.html`
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
      <gr-main-header id="mainHeader" search-query="{{params.query}}" class\$="[[_computeShadowClass(_isShadowDom)]]">
      </gr-main-header>
    </gr-fixed-panel>
    <main>
      <template is="dom-if" if="[[_showChangeListView]]" restamp="true">
        <gr-change-list-view params="[[params]]" account="[[_account]]" view-state="{{_viewState.changeListView}}"></gr-change-list-view>
      </template>
      <template is="dom-if" if="[[_showDashboardView]]" restamp="true">
        <gr-dashboard-view account="[[_account]]" params="[[params]]" view-state="{{_viewState.dashboardView}}"></gr-dashboard-view>
      </template>
      <template is="dom-if" if="[[_showChangeView]]" restamp="true">
        <gr-change-view params="[[params]]" view-state="{{_viewState.changeView}}" back-page="[[_lastSearchPage]]"></gr-change-view>
      </template>
      <template is="dom-if" if="[[_showEditorView]]" restamp="true">
        <gr-editor-view params="[[params]]"></gr-editor-view>
      </template>
      <template is="dom-if" if="[[_showDiffView]]" restamp="true">
          <gr-diff-view params="[[params]]" change-view-state="{{_viewState.changeView}}"></gr-diff-view>
        </template>
      <template is="dom-if" if="[[_showSettingsView]]" restamp="true">
        <gr-settings-view params="[[params]]" on-account-detail-update="_handleAccountDetailUpdate">
        </gr-settings-view>
      </template>
      <template is="dom-if" if="[[_showAdminView]]" restamp="true">
        <gr-admin-view path="[[_path]]" params="[[params]]"></gr-admin-view>
      </template>
      <template is="dom-if" if="[[_showPluginScreen]]" restamp="true">
        <gr-endpoint-decorator name="[[_pluginScreenName]]">
          <gr-endpoint-param name="token" value="[[params.screen]]"></gr-endpoint-param>
        </gr-endpoint-decorator>
      </template>
      <template is="dom-if" if="[[_showCLAView]]" restamp="true">
        <gr-cla-view></gr-cla-view>
      </template>
      <div id="errorView" class="errorView">
        <div class="errorEmoji">[[_lastError.emoji]]</div>
        <div class="errorText">[[_lastError.text]]</div>
        <div class="errorMoreInfo">[[_lastError.moreInfo]]</div>
      </div>
    </main>
    <footer r="contentinfo" class\$="[[_computeShadowClass(_isShadowDom)]]">
      <div>
        Powered by <a href="https://www.gerritcodereview.com/" rel="noopener" target="_blank">Gerrit Code Review</a>
        ([[_version]])
      </div>
      <div>
        <a class="feedback" href\$="[[_feedbackUrl]]" rel="noopener" target="_blank">Send feedback</a>
        <template is="dom-if" if="[[_computeShowGwtUiLink(_serverConfig)]]">
          |
          <a id="gwtLink" href\$="[[computeGwtUrl(_path)]]" rel="external">Switch to Old UI</a>
        </template>
        | Press “?” for keyboard shortcuts
      </div>
    </footer>
    <gr-overlay id="keyboardShortcuts" with-backdrop="">
      <gr-keyboard-shortcuts-dialog view="[[params.view]]" on-close="_handleKeyboardShortcutDialogClose"></gr-keyboard-shortcuts-dialog>
    </gr-overlay>
    <gr-overlay id="registrationOverlay" with-backdrop="">
      <gr-registration-dialog id="registrationDialog" settings-url="[[_settingsUrl]]" on-account-detail-update="_handleAccountDetailUpdate" on-close="_handleRegistrationDialogClose">
      </gr-registration-dialog>
    </gr-overlay>
    <gr-endpoint-decorator name="plugin-overlay"></gr-endpoint-decorator>
    <gr-error-manager id="errorManager"></gr-error-manager>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
    <gr-reporting id="reporting"></gr-reporting>
    <gr-router id="router"></gr-router>
    <gr-plugin-host id="plugins" config="[[_serverConfig]]">
    </gr-plugin-host>
    <gr-lib-loader id="libLoader"></gr-lib-loader>
    <gr-external-style id="externalStyle" name="app-theme"></gr-external-style>
`,

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
    _feedbackUrl: {
      type: String,
      value: 'https://bugs.chromium.org/p/gerrit/issues/entry' +
        '?template=PolyGerrit%20Issue',
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

  keyBindings: {
    '?': '_showKeyboardShortcuts',
    'g:keydown': '_gKeyDown',
    'g:keyup': '_gKeyUp',
    'a m o': '_jumpKeyPressed',
  },

  ready() {
    this._isShadowDom = undefined.useShadow;
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
      this.$.registrationOverlay.open();
      this.$.registrationDialog.loadData().then(() => {
        this.$.registrationOverlay.refit();
      });
    }
    this.$.header.unfloat();
  },

  _computeShowGwtUiLink(config) {
    return !window.DEPRECATE_GWT_UI &&
        config.gerrit.web_uis && config.gerrit.web_uis.includes('GWT');
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
  }
});
