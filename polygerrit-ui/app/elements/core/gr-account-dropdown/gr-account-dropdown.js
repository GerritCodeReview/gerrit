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
  'use strict'

   var ANONYMOUS_NAME = 'Anonymous';

  Polymer({
    is: 'gr-account-dropdown',

    properties: {
      account: Object,
      _hasAvatars: Boolean,
      _anonymousName: {
        type: String,
        value: ANONYMOUS_NAME,
      },
      links: {
        type: Array,
        value: [
          {name: 'Settings', url: '/settings'},
          {name: 'Switch account', url: '/switch-account'},
          {name: 'Sign out', url: '/logout'},
        ],
      },
      topContent: {
        type: Array,
        computed: '_getTopContent(account, _anonymousName)',
      },
    },

    attached: function() {
      this.$.restAPI.getConfig().then(function(cfg) {
        this._hasAvatars = !!(cfg && cfg.plugin && cfg.plugin.has_avatars);
        if (cfg && cfg.user &&
            cfg.user.anonymous_coward_name &&
            cfg.user.anonymous_coward_name !== 'Anonymous Coward') {
          this._anonymousName = cfg.user.anonymous_coward_name;
        }
      }.bind(this));
    },

    _getTopContent: function(account, _anonymousName) {
      return [
        {text: this._accountName(account, _anonymousName), bold: true},
        {text: account.email ? account.email : ''},
      ];
    },

    _accountName: function(account, _anonymousName) {
      if (account && account.name) {
        return account.name;
      } else if (account && account.email) {
        return account.email;
      }
      return _anonymousName;
    },
  });
})();
