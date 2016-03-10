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
      role: 'banner'
    },

    properties: {
      searchQuery: {
        type: String,
        notify: true,
      },

      _account: Object,
      _defaultLinks: {
        type: Array,
        value: function() {
          return DEFAULT_LINKS;
        },
      },
      _links: {
        type: Array,
        computed: '_computeLinks(_defaultLinks, _userLinks)',
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
    },

    _computeLinks: function(defaultLinks, userLinks) {
      var links = defaultLinks.slice();
      if (userLinks && userLinks.length > 0) {
        links.push({
          title: 'Your',
          links: userLinks,
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
