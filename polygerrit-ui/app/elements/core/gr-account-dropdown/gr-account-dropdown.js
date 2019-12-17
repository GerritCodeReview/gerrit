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

  const INTERPOLATE_URL_PATTERN = /\$\{([\w]+)\}/g;

  /**
   * @appliesMixin Gerrit.DisplayNameMixin
   */
  class GrAccountDropdown extends Polymer.mixinBehaviors( [
    Gerrit.DisplayNameBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-account-dropdown'; }

    static get properties() {
      return {
        account: Object,
        config: Object,
        links: {
          type: Array,
          computed: '_getLinks(_switchAccountUrl, _path)',
        },
        topContent: {
          type: Array,
          computed: '_getTopContent(account)',
        },
        _path: {
          type: String,
          value: '/',
        },
        _hasAvatars: Boolean,
        _switchAccountUrl: String,
      };
    }

    attached() {
      super.attached();
      this._handleLocationChange();
      this.listen(window, 'location-change', '_handleLocationChange');
      this.$.restAPI.getConfig().then(cfg => {
        this.config = cfg;

        if (cfg && cfg.auth && cfg.auth.switch_account_url) {
          this._switchAccountUrl = cfg.auth.switch_account_url;
        } else {
          this._switchAccountUrl = '';
        }
        this._hasAvatars = !!(cfg && cfg.plugin && cfg.plugin.has_avatars);
      });
    }

    detached() {
      super.detached();
      this.unlisten(window, 'location-change', '_handleLocationChange');
    }

    _getLinks(switchAccountUrl, path) {
      // Polymer 2: check for undefined
      if ([switchAccountUrl, path].some(arg => arg === undefined)) {
        return undefined;
      }

      const links = [{name: 'Settings', url: '/settings/'}];
      if (switchAccountUrl) {
        const replacements = {path};
        const url = this._interpolateUrl(switchAccountUrl, replacements);
        links.push({name: 'Switch account', url, external: true});
      }
      links.push({name: 'Sign out', url: '/logout'});
      return links;
    }

    _getTopContent(account) {
      return [
        {text: this._accountName(account), bold: true},
        {text: account.email ? account.email : ''},
      ];
    }

    _handleLocationChange() {
      this._path =
          window.location.pathname +
          window.location.search +
          window.location.hash;
    }

    _interpolateUrl(url, replacements) {
      return url.replace(INTERPOLATE_URL_PATTERN, (match, p1) => {
        return replacements[p1] || '';
      });
    }

    _accountName(account) {
      return this.getUserName(this.config, account, true);
    }
  }

  customElements.define(GrAccountDropdown.is, GrAccountDropdown);
})();
