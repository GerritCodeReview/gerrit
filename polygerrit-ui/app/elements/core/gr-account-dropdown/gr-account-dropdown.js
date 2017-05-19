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

  const INTERPOLATE_URL_PATTERN = /\$\{([\w]+)\}/g;

  const ANONYMOUS_NAME = 'Anonymous';

  Polymer({
    is: 'gr-account-dropdown',

    properties: {
      account: Object,
      _anonymousName: {
        type: String,
        value: ANONYMOUS_NAME,
      },
      links: {
        type: Array,
        computed: '_getLinks(_switchAccountUrl, _path)',
      },
      topContent: {
        type: Array,
        computed: '_getTopContent(account, _anonymousName)',
      },
      _path: {
        type: String,
        value: '/',
      },
      _hasAvatars: Boolean,
      _switchAccountUrl: String,
    },

    attached() {
      this._handleLocationChange();
      this.listen(window, 'location-change', '_handleLocationChange');
      this.$.restAPI.getConfig().then(cfg => {
        if (cfg && cfg.auth && cfg.auth.switch_account_url) {
          this._switchAccountUrl = cfg.auth.switch_account_url;
        } else {
          this._switchAccountUrl = null;
        }
        this._hasAvatars = !!(cfg && cfg.plugin && cfg.plugin.has_avatars);

        if (cfg && cfg.user &&
            cfg.user.anonymous_coward_name &&
            cfg.user.anonymous_coward_name !== 'Anonymous Coward') {
          this._anonymousName = cfg.user.anonymous_coward_name;
        }
      });
    },

    detached() {
      this.unlisten(window, 'location-change', '_handleLocationChange');
    },

    _getLinks(switchAccountUrl, path) {
      const links = [{name: 'Settings', url: '/settings'}];
      if (switchAccountUrl) {
        const replacements = {path};
        const url = this._interpolateUrl(switchAccountUrl, replacements);
        links.push({name: 'Switch account', url});
      }
      links.push({name: 'Sign out', url: '/logout'});
      return links;
    },

    _getTopContent(account, _anonymousName) {
      return [
        {text: this._accountName(account, _anonymousName), bold: true},
        {text: account.email ? account.email : ''},
      ];
    },

    _handleLocationChange() {
      this._path =
          window.location.pathname +
          window.location.search +
          window.location.hash;
    },

    _interpolateUrl(url, replacements) {
      return url.replace(INTERPOLATE_URL_PATTERN, (match, p1) => {
        return replacements[p1] || '';
      });
    },

    _accountName(account, _anonymousName) {
      if (account && account.name) {
        return account.name;
      } else if (account && account.email) {
        return account.email;
      }
      return _anonymousName;
    },
  });
})();
