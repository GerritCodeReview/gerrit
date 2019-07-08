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
      showEmail: {
        type: Boolean,
        value: false,
      },
    },

    _computeAccountTitle: function(account) {
      if (!account || (!account.name && !account.email)) { return; }
      var result = '';
      if (account.name) {
        result += account.name;
      }
      if (account.email) {
        result += ' <' + account.email + '>';
      }
      return result;
    },

    _computeShowEmail: function(showEmail, account) {
      return !!(showEmail && account && account.email);
    },

    _computeEmailStr: function(account) {
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
