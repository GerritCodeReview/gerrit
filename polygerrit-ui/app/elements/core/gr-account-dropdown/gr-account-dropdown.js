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
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-dropdown/gr-dropdown.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-avatar/gr-avatar.js';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-account-dropdown_html.js';
import {DisplayNameBehavior} from '../../../behaviors/gr-display-name-behavior/gr-display-name-behavior.js';

const INTERPOLATE_URL_PATTERN = /\${([\w]+)}/g;

/**
 * @extends PolymerElement
 */
class GrAccountDropdown extends mixinBehaviors( [
  DisplayNameBehavior,
], GestureEventListeners(
    LegacyElementMixin(
        PolymerElement))) {
  static get template() { return htmlTemplate; }

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

  /** @override */
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

  /** @override */
  detached() {
    super.detached();
    this.unlisten(window, 'location-change', '_handleLocationChange');
  }

  _getLinks(switchAccountUrl, path) {
    // Polymer 2: check for undefined
    if ([switchAccountUrl, path].includes(undefined)) {
      return undefined;
    }

    const links = [];
    links.push({name: 'Settings', url: '/settings/'});
    links.push({name: 'Keyboard Shortcuts', id: 'shortcuts'});
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

  _handleShortcutsTap(e) {
    this.dispatchEvent(new CustomEvent('show-keyboard-shortcuts',
        {bubbles: true, composed: true}));
  }

  _handleLocationChange() {
    this._path =
        window.location.pathname +
        window.location.search +
        window.location.hash;
  }

  _interpolateUrl(url, replacements) {
    return url.replace(
        INTERPOLATE_URL_PATTERN,
        (match, p1) => replacements[p1] || '');
  }

  _accountName(account) {
    return this.getUserName(this.config, account);
  }
}

customElements.define(GrAccountDropdown.is, GrAccountDropdown);
