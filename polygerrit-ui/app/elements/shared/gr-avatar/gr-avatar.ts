/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {getBaseUrl} from '../../../utils/url-util';
import {AccountInfo} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {LitElement, css, html} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {pluginLoaderToken} from '../gr-js-api-interface/gr-plugin-loader';
import {resolve} from '../../../models/dependency';

/**
 * The <gr-avatar> component works by updating its own background and visibility
 * rather than conditionally rendering an image into it's shadow root.
 */
@customElement('gr-avatar')
export class GrAvatar extends LitElement {
  @property({type: Object})
  account?: AccountInfo;

  @property({type: Number})
  imageSize = 16;

  @state() private hasAvatars = false;

  @property({type: Boolean})
  forceFetch = false;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getPluginLoader = resolve(this, pluginLoaderToken);

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
    this.updateHostVisibilityAndImage();
    return html``;
  }

  override connectedCallback() {
    super.connectedCallback();
    Promise.all([
      this.restApiService.getConfig(),
      this.getPluginLoader().awaitPluginsLoaded(),
    ]).then(([cfg]) => {
      this.hasAvatars = Boolean(cfg?.plugin?.has_avatars);
      this.updateHostVisibilityAndImage();
    });
  }

  private updateHostVisibilityAndImage() {
    if (!this.hasAvatars || !this.account) {
      this.hidden = true;
      return;
    }
    this.hidden = false;

    const url = this.buildAvatarURL(this.account);
    if (url) {
      this.style.backgroundImage = `url("${url}")`;
    }
  }

  private getAccounts(account: AccountInfo) {
    return (
      account._account_id || account.email || account.username || account.name
    );
  }

  private buildAvatarURL(account?: AccountInfo) {
    if (!account) {
      return '';
    }
    const avatars = account.avatars || [];
    // if there is no avatar url in account, there is no avatar set on server,
    // and request /avatar?s will be 404.
    if (avatars.length === 0 && !this.forceFetch) {
      return '';
    }
    for (let i = 0; i < avatars.length; i++) {
      if (avatars[i].height === this.imageSize) {
        return avatars[i].url;
      }
    }
    const accountIdentifier = this.getAccounts(account);
    if (!accountIdentifier) {
      return '';
    }
    return `${getBaseUrl()}/accounts/${encodeURIComponent(
      accountIdentifier
    )}/avatar?s=${this.imageSize}`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-avatar': GrAvatar;
  }
}
