/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {getBaseUrl} from '../../../utils/url-util';
import {AccountInfo} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
<<<<<<< PATCH SET (3f827af8b92efa7c259cabafda293e01970cedd3 Introduce AvatarPluginApi for custom avatar providers.)

import {getAvatarProviders} from '../../../api/avatar';
import {resolve} from '../../../models/dependency';
import {getAppContext} from '../../../services/app-context';
import {AccountInfo} from '../../../types/common';
import {getBaseUrl} from '../../../utils/url-util';
||||||| BASE      (1c3a2b31f347dea867fea4ce7e84f9797be2353c Experimenting)

import {resolve} from '../../../models/dependency';
import {getAppContext} from '../../../services/app-context';
import {AccountInfo} from '../../../types/common';
import {getBaseUrl} from '../../../utils/url-util';
=======
>>>>>>> BASE      (75c389f5f5567acbd4e3bf3642af3dea3ca9641e Merge "gr-rule-editor: Replace gr-select with md-outlined-se)
import {pluginLoaderToken} from '../gr-js-api-interface/gr-plugin-loader';
<<<<<<< PATCH SET (3f827af8b92efa7c259cabafda293e01970cedd3 Introduce AvatarPluginApi for custom avatar providers.)
||||||| BASE      (1c3a2b31f347dea867fea4ce7e84f9797be2353c Experimenting)

import {getRobotAvatarUrl} from './robot-avatar-map';
=======
import {resolve} from '../../../models/dependency';
>>>>>>> BASE      (75c389f5f5567acbd4e3bf3642af3dea3ca9641e Merge "gr-rule-editor: Replace gr-select with md-outlined-se)

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
    // Fallback to empty string to make sure that the user,
    // which doesn't have an avatar set, does not reuse
    // someone elses.
    this.style.backgroundImage = url ? `url("${url}")` : '';
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
<<<<<<< PATCH SET (3f827af8b92efa7c259cabafda293e01970cedd3 Introduce AvatarPluginApi for custom avatar providers.)

    for (const provider of getAvatarProviders()) {
      const url = provider(account);
      if (url) return url;
    }

||||||| BASE      (1c3a2b31f347dea867fea4ce7e84f9797be2353c Experimenting)

    // Check to see if it is a known robot account with a custom avatar.
    const robotUrl = getRobotAvatarUrl(account.email);
    if (robotUrl) {
      return robotUrl;
    }

=======
>>>>>>> BASE      (75c389f5f5567acbd4e3bf3642af3dea3ca9641e Merge "gr-rule-editor: Replace gr-select with md-outlined-se)
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
