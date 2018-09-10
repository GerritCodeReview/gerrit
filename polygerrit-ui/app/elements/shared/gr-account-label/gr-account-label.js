/**
@license
Copyright (C) 2016 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../behaviors/gr-anonymous-name-behavior/gr-anonymous-name-behavior.js';

import '../../../behaviors/gr-tooltip-behavior/gr-tooltip-behavior.js';
import '../../../../@polymer/polymer/polymer-legacy.js';
import '../../../styles/shared-styles.js';
import '../gr-avatar/gr-avatar.js';
import '../gr-limited-text/gr-limited-text.js';
import '../gr-rest-api-interface/gr-rest-api-interface.js';
/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
(function(window) {
  'use strict';

  const util = window.util || {};

  util.parseDate = function(dateStr) {
    // Timestamps are given in UTC and have the format
    // "'yyyy-mm-dd hh:mm:ss.fffffffff'" where "'ffffffffff'" represents
    // nanoseconds.
    // Munge the date into an ISO 8061 format and parse that.
    return new Date(dateStr.replace(' ', 'T') + 'Z');
  };

  util.getCookie = function(name) {
    const key = name + '=';
    const cookies = document.cookie.split(';');
    for (let i = 0; i < cookies.length; i++) {
      let c = cookies[i];
      while (c.charAt(0) === ' ') {
        c = c.substring(1);
      }
      if (c.startsWith(key)) {
        return c.substring(key.length, c.length);
      }
    }
    return '';
  };
  window.util = util;
})(window);

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      :host {
        display: inline;
      }
      :host::after {
        content: var(--account-label-suffix);
      }
      gr-avatar {
        height: 1.3em;
        width: 1.3em;
        margin-right: .15em;
        vertical-align: -.25em;
      }
      .text {
        @apply --gr-account-label-text-style;
      }
      .text:hover {
        @apply --gr-account-label-text-hover-style;
      }
      .email,
      .showEmail .name {
        display: none;
      }
      .showEmail .email {
        display: inline-block;
      }
    </style>
    <span>
      <template is="dom-if" if="[[!hideAvatar]]">
        <gr-avatar account="[[account]]" image-size="[[avatarImageSize]]"></gr-avatar>
      </template>
      <span class\$="text [[_computeShowEmailClass(account)]]">
        <span class="name">
          [[_computeName(account, _serverConfig)]]</span>
        <span class="email">
          [[_computeEmailStr(account)]]
        </span>
        <template is="dom-if" if="[[account.status]]">
          (<gr-limited-text limit="10" text="[[account.status]]"></gr-limited-text>)
        </template>
      </span>
    </span>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

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
    Gerrit.AnonymousNameBehavior,
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

  _computeAccountTitle(account, tooltip) {
    if (!account) { return; }
    let result = '';
    if (this._computeName(account, this._serverConfig)) {
      result += this._computeName(account, this._serverConfig);
    }
    if (account.email) {
      result += ' <' + account.email + '>';
    }
    if (this.additionalText) {
      return result + ' ' + this.additionalText;
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
  }
});
