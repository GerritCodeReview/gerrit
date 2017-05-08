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

  var INTERPOLATE_URL_PATTERN = /\$\{([\w]+)\}/g;

  Polymer({
    is: 'gr-account-dropdown',

    properties: {
      account: Object,
      links: {
        type: Array,
        computed: '_getLinks(_switchAccountUrl, _path)',
      },
      topContent: {
        type: Array,
        computed: '_getTopContent(account, _anon)',
      },
      _path: {
        type: String,
        value: '/',
      },
      _hasAvatars: Boolean,
      _anon: String,
      _switchAccountUrl: String,
    },

    attached: function() {
      this._handleLocationChange();
      this.listen(window, 'location-change', '_handleLocationChange');
      this.$.restAPI.getConfig().then(function(cfg) {
        if (cfg && cfg.auth && cfg.auth.switch_account_url) {
          this._switchAccountUrl = cfg.auth.switch_account_url;
        } else {
          this._switchAccountUrl = null;
        }
        this._hasAvatars = !!(cfg && cfg.plugin && cfg.plugin.has_avatars);
        if (cfg.user && cfg.user.anonymous_coward_name) {
          this._anon = cfg.user.anonymous_coward_name;
        } else {
          this._anon = 'Anonymous Coward';
        }
      }.bind(this));
    },

    detached: function() {
      this.unlisten(window, 'location-change', '_handleLocationChange');
    },

    _getLinks: function(switchAccountUrl, path) {
      var links = [{name: 'Settings', url: '/settings'}];
      if (switchAccountUrl) {
        var replacements = {path: path};
        var url = this._interpolateUrl(switchAccountUrl, replacements);
        links.push({name: 'Switch account', url: url});
      }
      links.push({name: 'Sign out', url: '/logout'});
      return links;
    },

    _getTopContent: function(account, anon) {
      // if (!account) { return []; }
      return [
        {text: this._accountName(account, anon), bold: true},
        {text: account.email},
      ];
    },

    _handleLocationChange: function() {
      this._path =
          window.location.pathname +
          window.location.search +
          window.location.hash;
    },

    _interpolateUrl: function(url, replacements) {
      return url.replace(INTERPOLATE_URL_PATTERN, function(match, p1) {
        return replacements[p1] || '';
      });
    },

    _accountName: function(account, _anon) {
      if (account && account.name) {
        return account.name;
      } else if (_anon) {
        return _anon;
      } else {
        return '';
      }
    },
  });
})();
