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
      account: Object,
      avatarImageSize: {
        type: Number,
        value: 32,
      },
      config: Object,
      showEmail: {
        type: Boolean,
        value: false,
      },
    },

    behaviors: [
      Gerrit.AnonymousNameBehavior,
    ],

    attached() {
      this.$.restAPI.getConfig().then(cfg => {
        this.config = cfg;
      });
    },

    _accountOrAnon(account) {
      return this.getUserName(this.config, account, false);
    },

    _computeAccountTitle(account) {
      if (!account) { return; }
      let result = '';
      if (this._accountOrAnon(account)) {
        result += this._accountOrAnon(account);
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
  });
})();
