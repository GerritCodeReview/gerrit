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
    is: 'gr-avatar',

    properties: {
      account: {
        type: Object,
        observer: '_accountChanged',
      },
      imageSize: {
        type: Number,
        value: 16,
      },
      _hasAvatars: {
        type: Boolean,
        value: false,
      },
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
    ],

    attached() {
      Promise.all([
        this._getConfig(),
        Gerrit.awaitPluginsLoaded(),
      ]).then(([cfg]) => {
        this._hasAvatars = !!(cfg && cfg.plugin && cfg.plugin.has_avatars);

        this._updateAvatarURL();
      });
    },

    _getConfig() {
      return this.$.restAPI.getConfig();
    },

    _accountChanged(account) {
      this._updateAvatarURL();
    },

    _updateAvatarURL() {
      if (!this._hasAvatars || !this.account) {
        this.hidden = true;
        return;
      }
      this.hidden = false;

      const url = this._buildAvatarURL(this.account);
      if (url) {
        this.style.backgroundImage = 'url("' + url + '")';
      }
    },

    _getAccounts(account) {
      return account._account_id || account.email || account.username ||
          account.name;
    },

    _buildAvatarURL(account) {
      if (!account) { return ''; }
      const avatars = account.avatars || [];
      for (let i = 0; i < avatars.length; i++) {
        if (avatars[i].height === this.imageSize) {
          return avatars[i].url;
        }
      }
      return this.getBaseUrl() + '/accounts/' +
        encodeURIComponent(this._getAccounts(account)) +
        '/avatar?s=' + this.imageSize;
    },
  });
})();
