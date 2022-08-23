/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import './gr-avatar';
import {AccountInfo} from '../../../types/common';
import {LitElement, css, html} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {uniqueDefinedAvatar} from '../../../utils/account-util';
import {getAppContext} from '../../../services/app-context';
import {getPluginLoader} from '../gr-js-api-interface/gr-plugin-loader';

/**
 * This elements draws stack of avatars overlapped with each other.
 *
 * If accounts is empty or contains accounts with more than MAX_STACK unique
 * avatars the fallback slot is rendered instead.
 *
 * Style parameters:
 *   --avatar-size: size of the individual avatars. (Default: 16px)
 *   --stack-border-color: border of individual avatars in stack.
 *       (Default: #ffffff)
 */
@customElement('gr-avatar-stack')
export class GrAvatarStack extends LitElement {
  static readonly MAX_STACK = 4;

  @property({type: Array})
  accounts: AccountInfo[] = [];

  /**
   * The size of requested image in px.
   *
   * By default this also controls avatarSize.
   */
  @property({type: Number})
  imageSize = 16;

  /**
   * Reflects plugins.has_avatars value of server configuration.
   */
  @state() private hasAvatars = false;

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      css`
        gr-avatar {
          box-sizing: border-box;
          vertical-align: top;
          height: var(--avatar-size, 16px);
          width: var(--avatar-size, 16px);
          border: solid 1px var(--stack-border-color, #ffffff);
        }
        gr-avatar:not(:first-child) {
          margin-left: calc((var(--avatar-size, 16px) / -2));
        }
      `,
    ];
  }

  override connectedCallback() {
    super.connectedCallback();
    Promise.all([
      this.restApiService.getConfig(),
      getPluginLoader().awaitPluginsLoaded(),
    ]).then(([cfg]) => {
      this.hasAvatars = Boolean(cfg?.plugin?.has_avatars);
    });
  }

  override render() {
    const uniqueAvatarAccounts = this.accounts
      .filter(account => !!account?.avatars?.[0]?.url)
      .filter(uniqueDefinedAvatar);
    if (
      !this.hasAvatars ||
      uniqueAvatarAccounts.length === 0 ||
      uniqueAvatarAccounts.length > GrAvatarStack.MAX_STACK
    ) {
      return html`<slot name="fallback"></slot>`;
    }
    return uniqueAvatarAccounts.map(
      account =>
        html`<gr-avatar .account=${account} .imageSize=${this.imageSize}>
        </gr-avatar>`
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-avatar-stack': GrAvatarStack;
  }
}
