/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {getBaseUrl} from '../../../utils/url-util';
import {getPluginLoader} from '../gr-js-api-interface/gr-plugin-loader';
import {AccountInfo} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';

@customElement('gr-avatar')
export class GrAvatar extends LitElement {
  @property({type: Object})
  account?: AccountInfo;

  @property({type: Number})
  imageSize = 16;

  @property({type: Boolean})
  _hasAvatars = false;

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      css`
        :host([hidden]) {
          display: none;
        }
        :host {
          display: inline-block;
          border-radius: 50%;
          background-size: cover;
          background-color: var(
            --avatar-background-color,
            var(--gray-background)
          );
        }
      `,
    ];
  }

  override render() {
    this._updateAvatarURL();
    return html``;
  }

  override connectedCallback() {
    super.connectedCallback();
    Promise.all([
      this._getConfig(),
      getPluginLoader().awaitPluginsLoaded(),
    ]).then(([cfg]) => {
      this._hasAvatars = !!(cfg && cfg.plugin && cfg.plugin.has_avatars);

      this._updateAvatarURL();
    });
  }

  _getConfig() {
    return this.restApiService.getConfig();
  }

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
  }

  _getAccounts(account: AccountInfo) {
    return (
      account._account_id || account.email || account.username || account.name
    );
  }

  _buildAvatarURL(account?: AccountInfo) {
    if (!account) {
      return '';
    }
    const avatars = account.avatars || [];
    // if there is no avatar url in account, there is no avatar set on server,
    // and request /avatar?s will be 404.
    if (avatars.length === 0) {
      return '';
    }
    for (let i = 0; i < avatars.length; i++) {
      if (avatars[i].height === this.imageSize) {
        return avatars[i].url;
      }
    }
    const accountID = this._getAccounts(account);
    if (!accountID) {
      return '';
    }
    return (
      `${getBaseUrl()}/accounts/` +
      encodeURIComponent(`${this._getAccounts(account)}`) +
      `/avatar?s=${this.imageSize}`
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-avatar': GrAvatar;
  }
}
