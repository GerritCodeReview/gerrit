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
    is: 'gr-account-label',

    properties: {
      /**
       * @type {{ name: string, status: string }}
       */
      account: Object,
      avatarImageSize: {
        type: Number,
        value: 32,
      },
      showEmail: {
        type: Boolean,
        value: false,
      },
      title: {
        type: String,
        reflectToAttribute: true,
        computed: '_computeAccountTitle(account)',
      },
      hasTooltip: {
        type: Boolean,
        reflectToAttribute: true,
        computed: '_computeHasTooltip(account)',
      },
      hideAvatar: {
        type: Boolean,
        value: false,
      },
      showAnonymous: {
        type: Boolean,
        value: false,
      },
      _serverConfig: {
        type: Object,
        value: null,
      },
    },

    behaviors: [
      Gerrit.AnonymousNameBehavior,
      Gerrit.TooltipBehavior,
    ],

    ready() {
      if (this.showAnonymous) {
        this.$.restAPI.getConfig()
            .then(config => { this._serverConfig = config; });
      }
    },

    _computeName(account, config, showAnonymous) {
      if (account && account.name) {
        return account.name;
      }
      if (showAnonymous) {
        return this.getAnonymousName(config);
      }
      return '';
    },

    _computeAccountTitle(account) {
      if (!account || (!account.name && !account.email)) { return; }
      let result = '';
      if (account.name) {
        result += account.name;
      }
      if (account.email) {
        result += ' <' + account.email + '>';
      }
      return result;
    },

    _computeShowEmail(showEmail, account) {
      return !!(showEmail && account && account.email);
    },

    _computeEmailStr(account) {
      if (!account || !account.email) {
        return '';
      }
      if (account.name) {
        return '(' + account.email + ')';
      }
      return account.email;
    },

    _computeHasTooltip(account) {
      // If an account has loaded to fire this method, then set to true.
      return !!account;
    },
  });
})();
