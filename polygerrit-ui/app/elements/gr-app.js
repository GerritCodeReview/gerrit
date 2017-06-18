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
      params: Object,
      keyEventTarget: {
        type: Object,
        value() { return document.body; },
      },

      _account: {
        type: Object,
        observer: '_accountChanged',
      },
      _serverConfig: Object,
      _version: String,
      _showChangeListView: Boolean,
      _showDashboardView: Boolean,
      _showChangeView: Boolean,
      _showDiffView: Boolean,
      _showSettingsView: Boolean,
      _showProjectListView: Boolean,
      _showAdminGroup: Boolean,
      _showAdminProject: Boolean,
      _showPluginListView: Boolean,
      _showAdminView: Boolean,
      _showCLAView: Boolean,
      _showGroupListView: Boolean,
      _viewState: Object,
      _lastError: Object,
      _lastSearchPage: String,
      _path: String,
    },

    listeners: {
      'page-error': '_handlePageError',
      'title-change': '_handleTitleChange',
      'location-change': '_handleLocationChange',
    },

    observers: [
      '_viewChanged(params.view)',
    ],

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.KeyboardShortcutBehavior,
    ],

    keyBindings: {
      '?': '_showKeyboardShortcuts',
    },

    created() {
      // If shadow dom cookie is set, reload the page using shadow dom.
      if (util.getCookie('USE_SHADOW_DOM') === 'true') {
        if (!window.location.href.includes('?dom=shadow')) {
          window.location.href = window.location.href + '?dom=shadow';
        }
      }
    },

    ready() {
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
      this.$.errorManager.knownAccountId =
          this._account && this._account._account_id || null;
    },

    _viewChanged(view) {
      this.$.errorView.classList.remove('show');
      this.set('_showChangeListView', view === 'gr-change-list-view');
      this.set('_showDashboardView', view === 'gr-dashboard-view');
      this.set('_showChangeView', view === 'gr-change-view');
      this.set('_showDiffView', view === 'gr-diff-view');
      this.set('_showSettingsView', view === 'gr-settings-view');
      this.set('_showGroupListView', view === 'gr-admin-group-list');
      this.set('_showProjectListView', view === 'gr-admin-project-list');
      this.set('_showAdminGroup', view === 'gr-admin-group');
      this.set('_showAdminProject', view === 'gr-admin-project');
      this.set('_showPluginListView', view === 'gr-admin-plugin-list');
      this.set('_showAdminView', view === 'gr-admin-view');
      this.set('_showCLAView', view === 'gr-cla-view');
      if (this.params.justRegistered) {
        this.$.registration.open();
      }
      this.$.header.unfloat();
    },

    _loginTapHandler(e) {
      e.preventDefault();
      page.show('/login/' + encodeURIComponent(
          window.location.pathname + window.location.hash));
    },

    // Argument used for binding update only.
    _computeLoggedIn(account) {
      return !!(account && Object.keys(account).length > 0);
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
      this._handleSearchPageChange();
    },

    _handleSearchPageChange() {
      if (!this.params) {
        return;
      }
      const viewsToCheck = ['gr-change-list-view', 'gr-dashboard-view'];
      if (viewsToCheck.includes(this.params.view)) {
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
      if (this.params.view === 'gr-settings-view') {
        this.$$('gr-settings-view').reloadAccountDetail();
      }
    },

    _handleRegistrationDialogClose(e) {
      this.params.justRegistered = false;
      this.$.registration.close();
    },
  });
})();
