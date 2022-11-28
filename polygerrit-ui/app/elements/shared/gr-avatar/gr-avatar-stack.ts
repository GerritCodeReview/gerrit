/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import './gr-avatar';
import {AccountInfo} from '../../../types/common';
import {LitElement, css, html} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {
  uniqueAccountId,
  uniqueDefinedAvatar,
} from '../../../utils/account-util';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {subscribe} from '../../lit/subscription-controller';

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
   * In gr-app, gr-account-chip is in charge of loading a full account, so
   * avatars will be set. However, code-owners will create gr-avatars with a
   * bare account-id. To enable fetching of those avatars, a flag is added to
   * gr-avatar that will disregard the absence of avatar urls.
   */
  @property({type: Boolean})
  forceFetch = false;

  /**
   * Reflects plugins.has_avatars value of server configuration.
   */
  @state() private hasAvatars = false;

  static override get styles() {
    return [
      css`
        gr-avatar {
          box-sizing: border-box;
          vertical-align: top;
          height: var(--avatar-size, 16px);
          width: var(--avatar-size, 16px);
          border: solid 1px var(--stack-border-color, transparent);
        }
        gr-avatar:not(:first-child) {
          margin-left: calc((var(--avatar-size, 16px) / -2));
        }
      `,
    ];
  }

  private readonly getConfigModel = resolve(this, configModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getConfigModel().serverConfig$,
      config => {
        this.hasAvatars = Boolean(config?.plugin?.has_avatars);
      }
    );
  }

  override render() {
    const uniqueAvatarAccounts = this.forceFetch
      ? this.accounts.filter(uniqueAccountId)
      : this.accounts
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
        html`<gr-avatar
          .forceFetch=${this.forceFetch}
          .account=${account}
          .imageSize=${this.imageSize}
        >
        </gr-avatar>`
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-avatar-stack': GrAvatarStack;
  }
}
