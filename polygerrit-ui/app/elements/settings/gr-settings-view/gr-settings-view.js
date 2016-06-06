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

    properties: {
      account: {
        type: Object,
        value: function() { return {}; },
      },
      prefs: {
        type: Object,
        value: function() { return {}; },
      },
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
      _menuChanged: {
        type: Boolean,
        value: false,
      },
    },

    observers: [
      '_handlePrefsChanged(_localPrefs.*)',
      '_handleMenuChanged(_localMenu.splices)',
    ],

    attached: function() {
      var promises = [];

      promises.push(this.$.restAPI.getAccount().then(function(account) {
        this.account = account;
      }.bind(this)));

      promises.push(this.$.restAPI.getPreferences().then(function(prefs) {
        this.prefs = prefs;
        this._copyPrefs('_localPrefs', 'prefs');
        this._cloneMenu();
      }.bind(this)));

      Promise.all(promises).then(function() {
        this._loading = false;
      }.bind(this));
    },

    _copyPrefs: function(to, from) {
      for (var i = 0; i < PREFS_SECTION_FIELDS.length; i++) {
        this.set(to + '.' + PREFS_SECTION_FIELDS[i],
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

    _computeRegistered: function(registered) {
      if (!registered) { return ''; }
      return util.parseDate(registered).toGMTString();
    },

    _handlePrefsChanged: function() {
      if (this._loading || this._loading === undefined) { return; }
      this._prefsChanged = true;
    },

    _handleMenuChanged: function () {
      if (this._loading || this._loading === undefined) { return; }
      this._menuChanged = true;
    },

    _handleSavePreferences: function() {
      this._copyPrefs('prefs', '_localPrefs');

      return this.$.restAPI.savePreferences(this.prefs).then(function() {
        this._prefsChanged = false;
      }.bind(this));
    },

    _handleSaveMenu: function() {
      this.set('prefs.my', this._localMenu);
      this._cloneMenu();
      return this.$.restAPI.savePreferences(this.prefs).then(function() {
        this._menuChanged = false;
      }.bind(this));
    },
  });
})();
