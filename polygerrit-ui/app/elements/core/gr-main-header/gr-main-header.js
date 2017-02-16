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

  var ADMIN_LINKS = [
    {
      url: '/admin/groups',
      name: 'Groups',
    },
    {
      url: '/admin/create-group',
      name: 'Create Group',
      capability: 'createGroup'
    },
    {
      url: '/admin/projects',
      name: 'Projects',
    },
    {
      url: '/admin/create-project',
      name: 'Create Project',
      capability: 'createProject',
    },
    {
      url: '/admin/plugins',
      name: 'Plugins',
      capability: 'viewPlugins',
    },
  ];

  var DEFAULT_LINKS = [{
    title: 'Changes',
    links: [
      {
        url: '/q/status:open',
        name: 'Open',
      },
      {
        url: '/q/status:merged',
        name: 'Merged',
      },
      {
        url: '/q/status:abandoned',
        name: 'Abandoned',
      },
    ],
  }];

  Polymer({
    is: 'gr-main-header',

    hostAttributes: {
      role: 'banner',
    },

    properties: {
      searchQuery: {
        type: String,
        notify: true,
      },

      _account: Object,
      _adminLinks: {
        type: Array,
        value: function() { return []; },
      },
      _defaultLinks: {
        type: Array,
        value: function() {
          return DEFAULT_LINKS;
        },
      },
      _links: {
        type: Array,
        computed: '_computeLinks(_defaultLinks, _userLinks, _adminLinks)',
      },
      _loginURL: {
        type: String,
        value: '/login',
      },
      _userLinks: {
        type: Array,
        value: function() { return []; },
      },
    },

    observers: [
      '_accountLoaded(_account)',
    ],

    attached: function() {
      this._loadAccount();
      this.listen(window, 'location-change', '_handleLocationChange');
    },

    detached: function() {
      this.unlisten(window, 'location-change', '_handleLocationChange');
    },

    reload: function() {
      this._loadAccount();
    },

    _handleLocationChange: function(e) {
      this._loginURL = '/login/' + encodeURIComponent(
          window.location.pathname +
          window.location.search +
          window.location.hash);
    },

    _computeRelativeURL: function(path) {
      return '//' + window.location.host + path;
    },

    _computeLinks: function(defaultLinks, userLinks, adminLinks) {
      var links = defaultLinks.slice();
      if (userLinks && userLinks.length > 0) {
        links.push({
          title: 'Your',
          links: userLinks,
        });
      }
      if (adminLinks && adminLinks.length > 0) {
        links.push({
          title: 'Admin',
          links: adminLinks,
        });
      }
      return links;
    },

    _loadAccount: function() {
      this.$.restAPI.getAccount().then(function(account) {
        this._account = account;
        this.$.accountContainer.classList.toggle('loggedIn', account != null);
        this.$.accountContainer.classList.toggle('loggedOut', account == null);
      }.bind(this));
    },

    _accountLoaded: function(account) {
      if (!account) { return; }

      this.$.restAPI.getPreferences().then(function(prefs) {
        this._userLinks =
            prefs.my.map(this._stripHashPrefix).filter(this._isSupportedLink);
      }.bind(this));
      this._loadAccountCapabilities();
    },

    _loadAccountCapabilities: function() {
      var params = ['createProject', 'createGroup', 'viewPlugins'];
      return this.$.restAPI.getAccountCapabilities(params)
          .then(function(capabilities) {
        this._adminLinks = ADMIN_LINKS.filter(function(link) {
          return !link.capability ||
              capabilities.hasOwnProperty(link.capability);
        });
      }.bind(this));
    },

    _stripHashPrefix: function(linkObj) {
      if (linkObj.url.indexOf('#') === 0) {
        linkObj.url = linkObj.url.slice(1);
      }
      return linkObj;
    },

    _isSupportedLink: function(linkObj) {
      // Groups are not yet supported.
      return linkObj.url.indexOf('/groups') !== 0;
    },
  });
})();
