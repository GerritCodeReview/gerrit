/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
      title: {
        type: String,
        reflectToAttribute: true,
        computed: '_computeAccountTitle(account, additionalText)',
      },
      additionalText: String,
      hasTooltip: {
        type: Boolean,
        reflectToAttribute: true,
        computed: '_computeHasTooltip(account)',
      },
      hideAvatar: {
        type: Boolean,
        value: false,
      },
      _serverConfig: {
        type: Object,
        value: null,
      },
    },

    behaviors: [
      Gerrit.DisplayNameBehavior,
      Gerrit.TooltipBehavior,
    ],

    ready() {
      if (!this.additionalText) { this.additionalText = ''; }
      this.$.restAPI.getConfig()
          .then(config => { this._serverConfig = config; });
    },

    _computeName(account, config) {
      return this.getUserName(config, account, false);
    },

    _computeStatusTextLength(account, config) {
      // 35 as the max length of the name + status
      return Math.max(10, 35 - this._computeName(account, config).length);
    },

    _computeAccountTitle(account, tooltip) {
      // Polymer 2: check for undefined
      if ([
        account,
        tooltip,
      ].some(arg => arg === undefined)) {
        return undefined;
      }

      if (!account) { return; }
      let result = '';
      if (this._computeName(account, this._serverConfig)) {
        result += this._computeName(account, this._serverConfig);
      }
      if (account.email) {
        result += ` <${account.email}>`;
      }
      if (this.additionalText) {
        result += ` ${this.additionalText}`;
      }

      // Show status in the label tooltip instead of
      // in a separate tooltip on status
      if (account.status) {
        result += ` (${account.status})`;
      }

      return result;
    },

    _computeShowEmailClass(account) {
      if (!account || account.name || !account.email) { return ''; }
      return 'showEmail';
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
