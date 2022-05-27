/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-dropdown/gr-dropdown';
import '../../shared/gr-avatar/gr-avatar';
import {getUserName} from '../../../utils/display-name-util';
import {AccountInfo, ServerInfo} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {fireEvent} from '../../../utils/event-util';
import {
  DropdownContent,
  DropdownLink,
} from '../../shared/gr-dropdown/gr-dropdown';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';

const INTERPOLATE_URL_PATTERN = /\${([\w]+)}/g;

declare global {
  interface HTMLElementTagNameMap {
    'gr-account-dropdown': GrAccountDropdown;
  }
}

@customElement('gr-account-dropdown')
export class GrAccountDropdown extends LitElement {
  @property({type: Object})
  account?: AccountInfo;

  @property({type: Object})
  config?: ServerInfo;

  @property({type: String})
  _path = '/';

  @property({type: Boolean})
  _hasAvatars = false;

  @property({type: String})
  _switchAccountUrl = '';

  private readonly restApiService = getAppContext().restApiService;

  override connectedCallback() {
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

  override disconnectedCallback() {
    window.removeEventListener('location-change', this.handleLocationChange);
    super.disconnectedCallback();
  }

  static override get styles() {
    return [
      sharedStyles,
      css`
        gr-dropdown {
          padding: 0 var(--spacing-m);
          --gr-button-text-color: var(--header-text-color);
          --gr-dropdown-item-color: var(--primary-text-color);
        }
        gr-avatar {
          height: 2em;
          width: 2em;
          vertical-align: middle;
        }
      `,
    ];
  }

  override render() {
    return html`<gr-dropdown
      link=""
      .items=${this.links}
      .topContent=${this.topContent}
      @tap-item-shortcuts=${this._handleShortcutsTap}
      .horizontalAlign=${'right'}
    >
      <span ?hidden=${this._hasAvatars}
        >${this._accountName(this.account)}</span
      >
      <gr-avatar
        .account=${this.account}
        ?hidden=${!this._hasAvatars}
        .imageSize=${56}
        aria-label="Account avatar"
      ></gr-avatar>
    </gr-dropdown>`;
  }

  get links(): DropdownLink[] | undefined {
    return this._getLinks(this._switchAccountUrl, this._path);
  }

  get topContent(): DropdownContent[] | undefined {
    return this._getTopContent(this.account);
  }

  _getLinks(switchAccountUrl?: string, path?: string) {
    // Polymer 2: check for undefined
    if (switchAccountUrl === undefined || path === undefined) {
      return undefined;
    }

    const links: DropdownLink[] = [];
    links.push({name: 'Settings', id: 'settings', url: '/settings/'});
    links.push({name: 'Keyboard Shortcuts', id: 'shortcuts'});
    if (switchAccountUrl) {
      const replacements = {path};
      const url = this._interpolateUrl(switchAccountUrl, replacements);
      links.push({name: 'Switch account', url, external: true});
    }
    links.push({name: 'Sign out', id: 'signout', url: '/logout'});
    return links;
  }

  _getTopContent(account?: AccountInfo) {
    return [
      {text: this._accountName(account), bold: true},
      {text: account?.email ? account.email : ''},
    ] as DropdownContent[];
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
