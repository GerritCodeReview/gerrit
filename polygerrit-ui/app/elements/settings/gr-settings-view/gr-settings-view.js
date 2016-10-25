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

  var PREFS_SECTION_FIELDS = [
    'changes_per_page',
    'date_format',
    'time_format',
    'email_strategy',
    'diff_view',
  ];

  Polymer({
    is: 'gr-settings-view',

    /**
     * Fired when the title of the page should change.
     *
     * @event title-change
     */

    properties: {
      prefs: {
        type: Object,
        value: function() { return {}; },
      },
      _accountInfoMutable: Boolean,
      _accountInfoChanged: Boolean,
      _diffPrefs: Object,
      _localPrefs: {
        type: Object,
        value: function() { return {}; },
      },
      _localMenu: {
        type: Array,
        value: function() { return []; },
      },
      _loading: {
        type: Boolean,
        value: true,
      },
      _prefsChanged: {
        type: Boolean,
        value: false,
      },
      _diffPrefsChanged: {
        type: Boolean,
        value: false,
      },
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
      _newEmail: String,
      _addingEmail: {
        type: Boolean,
        value: false,
      },
      _lastSentVerificationEmail: {
        type: String,
        value: null,
      },
      _serverConfig: Object,
      _headerHeight: Number,

      /**
       * For testing purposes.
       */
      _loadingPromise: Object,
    },

    observers: [
      '_handlePrefsChanged(_localPrefs.*)',
      '_handleDiffPrefsChanged(_diffPrefs.*)',
      '_handleMenuChanged(_localMenu.splices)',
    ],

    attached: function() {
      this.fire('title-change', {title: 'Settings'});

      var promises = [
        this.$.accountInfo.loadData(),
        this.$.watchedProjectsEditor.loadData(),
        this.$.emailEditor.loadData(),
        this.$.groupList.loadData(),
        this.$.httpPass.loadData(),
      ];

      promises.push(this.$.restAPI.getPreferences().then(function(prefs) {
        this.prefs = prefs;
        this._copyPrefs('_localPrefs', 'prefs');
        this._cloneMenu();
      }.bind(this)));

      promises.push(this.$.restAPI.getDiffPreferences().then(function(prefs) {
        this._diffPrefs = prefs;
      }.bind(this)));

      promises.push(this.$.restAPI.getConfig().then(function(config) {
        this._serverConfig = config;
        if (this._serverConfig.sshd) {
          return this.$.sshEditor.loadData();
        }
      }.bind(this)));

      this._loadingPromise = Promise.all(promises).then(function() {
        this._loading = false;
      }.bind(this));

      this.listen(window, 'scroll', '_handleBodyScroll');
    },

    detached: function() {
      this.unlisten(window, 'scroll', '_handleBodyScroll');
    },

    reloadAccountDetail: function() {
      Promise.all([
        this.$.accountInfo.loadData(),
        this.$.emailEditor.loadData(),
      ]);
    },

    _handleBodyScroll: function(e) {
      if (this._headerHeight === undefined) {
        var top = this.$.settingsNav.offsetTop;
        for (var offsetParent = this.$.settingsNav.offsetParent;
           offsetParent;
           offsetParent = offsetParent.offsetParent) {
          top += offsetParent.offsetTop;
        }
        this._headerHeight = top;
      }

      this.$.settingsNav.classList.toggle('pinned',
          window.scrollY >= this._headerHeight);
    },

    _isLoading: function() {
      return this._loading || this._loading === undefined;
    },

    _copyPrefs: function(to, from) {
      for (var i = 0; i < PREFS_SECTION_FIELDS.length; i++) {
        this.set([to, PREFS_SECTION_FIELDS[i]],
            this[from][PREFS_SECTION_FIELDS[i]]);
      }
    },

    _cloneMenu: function() {
      var menu = [];
      this.prefs.my.forEach(function(item) {
        menu.push({
          name: item.name,
          url: item.url,
          target: item.target,
        });
      });
      this._localMenu = menu;
    },

    _handlePrefsChanged: function(prefs) {
      if (this._isLoading()) { return; }
      this._prefsChanged = true;
    },

    _handleDiffPrefsChanged: function() {
      if (this._isLoading()) { return; }
      this._diffPrefsChanged = true;
    },

    _handleMenuChanged: function() {
      if (this._isLoading()) { return; }
      this._menuChanged = true;
    },

    _handleSaveAccountInfo: function() {
      this.$.accountInfo.save();
    },

    _handleSavePreferences: function() {
      this._copyPrefs('prefs', '_localPrefs');

      return this.$.restAPI.savePreferences(this.prefs).then(function() {
        this._prefsChanged = false;
      }.bind(this));
    },

    _handleLineWrappingChanged: function() {
      this.set('_diffPrefs.line_wrapping', this.$.lineWrapping.checked);
    },

    _handleShowTabsChanged: function() {
      this.set('_diffPrefs.show_tabs', this.$.showTabs.checked);
    },

    _handleSyntaxHighlightingChanged: function() {
      this.set('_diffPrefs.syntax_highlighting',
          this.$.syntaxHighlighting.checked);
    },

    _handleSaveDiffPreferences: function() {
      return this.$.restAPI.saveDiffPreferences(this._diffPrefs)
          .then(function() {
            this._diffPrefsChanged = false;
          }.bind(this));
    },

    _handleSaveMenu: function() {
      this.set('prefs.my', this._localMenu);
      this._cloneMenu();
      return this.$.restAPI.savePreferences(this.prefs).then(function() {
        this._menuChanged = false;
      }.bind(this));
    },

    _handleSaveWatchedProjects: function() {
      this.$.watchedProjectsEditor.save();
    },

    _computeHeaderClass: function(changed) {
      return changed ? 'edited' : '';
    },

    _handleSaveEmails: function() {
      this.$.emailEditor.save();
    },

    _handleNewEmailKeydown: function(e) {
      if (e.keyCode === 13) { // Enter
        e.stopPropagation();
        this._handleAddEmailButton();
      }
    },

    _isNewEmailValid: function(newEmail) {
      return newEmail.indexOf('@') !== -1;
    },

    _computeAddEmailButtonEnabled: function(newEmail, addingEmail) {
      return this._isNewEmailValid(newEmail) && !addingEmail;
    },

    _handleAddEmailButton: function() {
      if (!this._isNewEmailValid(this._newEmail)) { return; }

      this._addingEmail = true;
      this.$.restAPI.addAccountEmail(this._newEmail).then(function(response) {
        this._addingEmail = false;

        // If it was unsuccessful.
        if (response.status < 200 || response.status >= 300) { return; }

        this._lastSentVerificationEmail = this._newEmail;
        this._newEmail = '';
      }.bind(this));
    },
  });
})();
