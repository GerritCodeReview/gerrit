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
import '../../shared/gr-button/gr-button';
import '../../shared/gr-dropdown/gr-dropdown';
import '../../../styles/shared-styles';
import '../../shared/gr-avatar/gr-avatar';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-account-dropdown_html';
import {getUserName} from '../../../utils/display-name-util';
import {customElement, property} from '@polymer/decorators';
import {AccountInfo, ServerInfo} from '../../../types/common';
import {appContext} from '../../../services/app-context';
import {fireEvent} from '../../../utils/event-util';
import {
  DropdownContent,
  DropdownLink,
} from '../../shared/gr-dropdown/gr-dropdown';

const INTERPOLATE_URL_PATTERN = /\${([\w]+)}/g;

declare global {
  interface HTMLElementTagNameMap {
    'gr-account-dropdown': GrAccountDropdown;
  }
}

@customElement('gr-account-dropdown')
export class GrAccountDropdown extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object})
  account?: AccountInfo;

  @property({type: Object})
  config?: ServerInfo;

  @property({type: Array, computed: '_getLinks(_switchAccountUrl, _path)'})
  links?: DropdownLink[];

  @property({type: Array, computed: '_getTopContent(account)'})
  topContent?: DropdownContent[];

  @property({type: String})
  _path = '/';

  @property({type: Boolean})
  _hasAvatars = false;

  @property({type: String})
  _switchAccountUrl = '';

  private readonly restApiService = appContext.restApiService;

  /** @override */
  connectedCallback() {
    super.connectedCallback();
    this.handleLocationChange();
    window.addEventListener('location-change', this.handleLocationChange);
    this.restApiService.getConfig().then(cfg => {
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
  disconnectedCallback() {
    window.removeEventListener('location-change', this.handleLocationChange);
    super.disconnectedCallback();
  }

  _getLinks(switchAccountUrl?: string, path?: string) {
    // Polymer 2: check for undefined
    if (switchAccountUrl === undefined || path === undefined) {
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

  _getTopContent(account?: AccountInfo) {
    return [
      {text: this._accountName(account), bold: true},
      {text: account?.email ? account.email : ''},
    ];
  }

  _handleShortcutsTap() {
    fireEvent(this, 'show-keyboard-shortcuts');
  }

  private readonly handleLocationChange = () => {
    this._path =
      window.location.pathname + window.location.search + window.location.hash;
  };

  _interpolateUrl(url: string, replacements: {[key: string]: string}) {
    return url.replace(
      INTERPOLATE_URL_PATTERN,
      (_, p1) => replacements[p1] || ''
    );
  }

  _accountName(account?: AccountInfo) {
    return getUserName(this.config, account);
  }
}
