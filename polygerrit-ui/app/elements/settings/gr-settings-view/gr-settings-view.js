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
      account: {
        type: Object,
        value: function() { return {}; },
      },
      prefs: {
        type: Object,
        value: function() { return {}; },
      },
      _diffPrefs: Object,
      _localPrefs: {
        type: Object,
        value: function() { return {}; },
      },
      _localMenu: {
        type: Array,
        value: function() { return []; },
      },
      _watchedProjects: Array,
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
      _watchedProjectsToRemove: {
        type: Array,
        value: function() { return []; },
      },
    },

    observers: [
      '_handlePrefsChanged(_localPrefs.*)',
      '_handleDiffPrefsChanged(_diffPrefs.*)',
      '_handleMenuChanged(_localMenu.splices)',
      '_handleProjectsChanged(_watchedProjects.*)',
    ],

    attached: function() {
      this.fire('title-change', {title: 'Settings'});

      var promises = [];

      promises.push(this.$.restAPI.getAccount().then(function(account) {
        this.account = account;
      }.bind(this)));

      promises.push(this.$.restAPI.getPreferences().then(function(prefs) {
        this.prefs = prefs;
        this._copyPrefs('_localPrefs', 'prefs');
        this._cloneMenu();
      }.bind(this)));

      promises.push(this.$.restAPI.getDiffPreferences().then(function(prefs) {
        this._diffPrefs = prefs;
      }.bind(this)));

      promises.push(this.$.restAPI.getWatchedProjects().then(function(projs) {
        this._watchedProjects = projs;
      }.bind(this)));

      Promise.all(promises).then(function() {
        this._loading = false;
      }.bind(this));
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

    _computeRegistered: function(registered) {
      if (!registered) { return ''; }
      return util.parseDate(registered).toGMTString();
    },

    _handlePrefsChanged: function() {
      if (this._loading || this._loading === undefined) { return; }
      this._prefsChanged = true;
    },

    _handleDiffPrefsChanged: function() {
      if (this._loading || this._loading === undefined) { return; }
      this._diffPrefsChanged = true;
    },

    _handleMenuChanged: function() {
      if (this._loading || this._loading === undefined) { return; }
      this._menuChanged = true;
    },

    _handleSavePreferences: function() {
      this._copyPrefs('prefs', '_localPrefs');

      return this.$.restAPI.savePreferences(this.prefs).then(function() {
        this._prefsChanged = false;
      }.bind(this));
    },

    _handleShowTabsChanged: function() {
      this.set('_diffPrefs.show_tabs', this.$.showTabs.checked);
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

    _handleWatchedProjectRemoved: function(e) {
      var project = e.detail;

      // If it was never saved, then we don't need to do anything.
      if (project._is_local) { return; }

      this._watchedProjectsToRemove.push(project);
      this._handleProjectsChanged();
    },

    _handleProjectsChanged: function() {
      if (this._loading) { return; }
      this._watchedProjectsChanged = true;
    },

    _handleSaveWatchedProjects: function() {
      this.$.restAPI.deleteWatchedProjects(this._watchedProjectsToRemove)
        .then(function() {
          return this.$.restAPI.saveWatchedProjects(this._watchedProjects);
        }.bind(this))
        .then(function(watchedProjects) {
          this._watchedProjects = watchedProjects;
          this._watchedProjectsChanged = false;
          this._watchedProjectsToRemove = [];
        }.bind(this));
    },

    _computeHeaderClass: function(changed) {
      return changed ? 'edited' : '';
    },
  });
})();
