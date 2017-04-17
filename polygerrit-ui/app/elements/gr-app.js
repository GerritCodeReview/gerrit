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
        value: function() { return document.body; },
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
      '_loadPlugins(_serverConfig.plugin.js_resource_paths)',
    ],

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.KeyboardShortcutBehavior,
    ],

    keyBindings: {
      '?': '_showKeyboardShortcuts',
    },

    ready: function() {
      this.$.router.start();

      this.$.restAPI.getAccount().then(function(account) {
        this._account = account;
      }.bind(this));
      this.$.restAPI.getConfig().then(function(config) {
        this._serverConfig = config;
      }.bind(this));
      this.$.restAPI.getVersion().then(function(version) {
        this._version = version;
      }.bind(this));

      this.$.reporting.appStarted();
      this._viewState = {
        changeView: {
          changeNum: null,
          patchRange: null,
          selectedFileIndex: 0,
          showReplyDialog: false,
          diffMode: null,
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

    _accountChanged: function(account) {
      if (!account) { return; }

      // Preferences are cached when a user is logged in; warm them.
      this.$.restAPI.getPreferences();
      this.$.restAPI.getDiffPreferences();
      this.$.errorManager.knownAccountId =
        this._account && this._account._account_id || null;
    },

    _viewChanged: function(view) {
      this.$.errorView.hidden = true;
      this.set('_showChangeListView', view === 'gr-change-list-view');
      this.set('_showDashboardView', view === 'gr-dashboard-view');
      this.set('_showChangeView', view === 'gr-change-view');
      this.set('_showDiffView', view === 'gr-diff-view');
      this.set('_showSettingsView', view === 'gr-settings-view');
      this.set('_showAdminView', view === 'gr-admin-view');
      this.set('_showCLAView', view === 'gr-cla-view');
      if (this.params.justRegistered) {
        this.$.registration.open();
      }
    },

    _loadPlugins: function(plugins) {
      Gerrit._setPluginsCount(plugins.length);
      for (var i = 0; i < plugins.length; i++) {
        var scriptEl = document.createElement('script');
        scriptEl.defer = true;
        scriptEl.src = '/' + plugins[i];
        scriptEl.onerror = Gerrit._pluginInstalled;
        document.body.appendChild(scriptEl);
      }
    },

    _loginTapHandler: function(e) {
      e.preventDefault();
      page.show('/login/' + encodeURIComponent(
          window.location.pathname + window.location.hash));
    },

    // Argument used for binding update only.
    _computeLoggedIn: function(account) {
      return !!(account && Object.keys(account).length > 0);
    },

    _computeShowGwtUiLink: function(config) {
      return config.gerrit.web_uis &&
          config.gerrit.web_uis.indexOf('GWT') !== -1;
    },

    _handlePageError: function(e) {
      [
        '_showChangeListView',
        '_showDashboardView',
        '_showChangeView',
        '_showDiffView',
        '_showSettingsView',
      ].forEach(function(showProp) {
        this.set(showProp, false);
      }.bind(this));

      this.$.errorView.hidden = false;
      var response = e.detail.response;
      var err = {text: [response.status, response.statusText].join(' ')};
      if (response.status === 404) {
        err.emoji = '¯\\_(ツ)_/¯';
        this._lastError = err;
      } else {
        err.emoji = 'o_O';
        response.text().then(function(text) {
          err.moreInfo = text;
          this._lastError = err;
        }.bind(this));
      }
    },

    _handleLocationChange: function(e) {
      var hash = e.detail.hash.substring(1);
      var pathname = e.detail.pathname;
      if (pathname.indexOf('/c/') === 0 && parseInt(hash, 10) > 0) {
        pathname += '@' + hash;
      }
      this.set('_path', pathname);
      this._handleSearchPageChange();
    },

    _handleSearchPageChange: function() {
      if (!this.params) {
        return;
      }
      var viewsToCheck = ['gr-change-list-view', 'gr-dashboard-view'];
      if (viewsToCheck.indexOf(this.params.view) !== -1) {
        this.set('_lastSearchPage', location.pathname);
      }
    },

    _handleTitleChange: function(e) {
      if (e.detail.title) {
        document.title = e.detail.title + ' · Gerrit Code Review';
      } else {
        document.title = '';
      }
    },

    _showKeyboardShortcuts: function(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }
      this.$.keyboardShortcuts.open();
    },

    _handleKeyboardShortcutDialogClose: function() {
      this.$.keyboardShortcuts.close();
    },

    _handleAccountDetailUpdate: function(e) {
      this.$.mainHeader.reload();
      if (this.params.view === 'gr-settings-view') {
        this.$$('gr-settings-view').reloadAccountDetail();
      }
    },

    _handleRegistrationDialogClose: function(e) {
      this.params.justRegistered = false;
      this.$.registration.close();
    },
  });
})();
