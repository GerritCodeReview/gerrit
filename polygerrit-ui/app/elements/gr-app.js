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

  Polymer({
    is: 'gr-app',

    properties: {
      account: {
        type: Object,
        observer: '_accountChanged',
      },
      accountReady: {
        type: Object,
        readOnly: true,
        notify: true,
        value: function() {
          return new Promise(function(resolve) {
            this._resolveAccountReady = resolve;
          }.bind(this));
        },
      },
      config: Object,
      version: String,
      params: Object,
      keyEventTarget: {
        type: Object,
        value: function() { return document.body; },
      },

      _diffPreferences: Object,
      _showChangeListView: Boolean,
      _showDashboardView: Boolean,
      _showChangeView: Boolean,
      _showDiffView: Boolean,
      _viewState: Object,
    },

    listeners: {
      'title-change': '_handleTitleChange',
    },

    observers: [
      '_viewChanged(params.view)',
    ],

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
    ],

    get loggedIn() {
      return !!(this.account && Object.keys(this.account).length > 0);
    },

    attached: function() {
      this.$.restAPI.getAccount().then(function(account) {
        this.account = account;
      }.bind(this));
      this.$.restAPI.getConfig().then(function(config) {
        this.config = config;
      }.bind(this));
    },

    ready: function() {
      this._viewState = {
        changeView: {
          changeNum: null,
          patchNum: null,
          selectedFileIndex: 0,
          showReplyDialog: false,
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

    _accountChanged: function() {
      this._resolveAccountReady();
      if (this.loggedIn) {
        this.$.diffPreferencesXHR.generateRequest();
      } else {
        // These defaults should match the defaults in
        // gerrit-extension-api/src/main/jcg/gerrit/extensions/client/DiffPreferencesInfo.java
        // NOTE: There are some settings that don't apply to PolyGerrit
        // (Render mode being at least one of them).
        this._diffPreferences = {
          auto_hide_diff_table_header: true,
          context: 10,
          cursor_blink_rate: 0,
          ignore_whitespace: 'IGNORE_NONE',
          intraline_difference: true,
          line_length: 100,
          show_line_endings: true,
          show_tabs: true,
          show_whitespace_errors: true,
          syntax_highlighting: true,
          tab_size: 8,
          theme: 'DEFAULT',
        };
      }
    },

    _viewChanged: function(view) {
      this.set('_showChangeListView', view == 'gr-change-list-view');
      this.set('_showDashboardView', view == 'gr-dashboard-view');
      this.set('_showChangeView', view == 'gr-change-view');
      this.set('_showDiffView', view == 'gr-diff-view');
    },

    _loginTapHandler: function(e) {
      e.preventDefault();
      page.show('/login/' + encodeURIComponent(
          window.location.pathname + window.location.hash));
    },

    _computeLoggedIn: function(account) { // argument used for binding update only
      return this.loggedIn;
    },

    _handleTitleChange: function(e) {
      if (e.detail.title) {
        document.title = e.detail.title + ' · Gerrit Code Review';
      } else {
        document.title = '';
      }
    },

    _handleKey: function(e) {
      if (this.shouldSupressKeyboardShortcut(e)) { return; }

      switch (e.keyCode) {
        case 191:  // '/' or '?' with shift key.
          // TODO(andybons): Localization using e.key/keypress event.
          if (!e.shiftKey) { break; }
          this.$.keyboardShortcuts.open();
      }
    },

    _handleKeyboardShortcutDialogClose: function() {
      this.$.keyboardShortcuts.close();
    },
  });
})();
