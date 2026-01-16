/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import './gr-avatar';
import '../gr-hovercard-account/gr-hovercard-account';
import {AccountInfo, ServerInfo} from '../../../types/common';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {
  isDetailedAccount,
  uniqueAccountId,
  uniqueDefinedAvatar,
} from '../../../utils/account-util';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {subscribe} from '../../lit/subscription-controller';
import {getDisplayName} from '../../../utils/display-name-util';
import {accountsModelToken} from '../../../models/accounts/accounts-model';
import {isDefined} from '../../../types/types';
import {when} from 'lit/directives/when.js';

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

  @state()
  detailedAccounts: AccountInfo[] = [];

  /**
   * The size of requested image in px.
   *
   * By default this also controls avatarSize.
   */
  @property({type: Number})
  imageSize = 16;

  /**
   * Whether a hover-card should be shown for each avatar when hovered
   */
  @property({type: Boolean})
  enableHover = false;

  /**
   * In gr-app, gr-account-chip is in charge of loading a full account, so
   * avatars will be set. However, code-owners will create gr-avatars with a
   * bare account-id. To enable fetching of those avatars, a flag is added to
   * gr-avatar-stack that will fetch the accounts on demand
   */
  @property({type: Boolean})
  forceFetch = false;

  private readonly getAccountsModel = resolve(this, accountsModelToken);

  @state() config?: ServerInfo;

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
      config => (this.config = config)
    );
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('accounts')) {
      if (
        this.forceFetch &&
        this.accounts.length > 0 &&
        this.accounts.some(a => !isDetailedAccount(a))
      ) {
        Promise.all(
          this.accounts.map(account =>
            this.getAccountsModel().fillDetails(account)
          )
        ).then(accounts => {
          // Only keep the detailed accounts as only those will be shown.
          // It is possible for the server to return an empty account with just an account-id.
          // This could be due to the fact that the user does not have permission to see this account.
          this.detailedAccounts = accounts.filter(
            a => isDefined(a) && isDetailedAccount(a)
          );
        });
      } else {
        this.detailedAccounts = this.accounts;
      }
    }
  }

  override render() {
    const uniqueAvatarAccounts = this.forceFetch
      ? this.detailedAccounts.filter(uniqueAccountId)
      : this.detailedAccounts
          .filter(account => !!account?.avatars?.[0]?.url)
          .filter(uniqueDefinedAvatar);
    const hasAvatars = this.config?.plugin?.has_avatars ?? false;
    if (
      !hasAvatars ||
      uniqueAvatarAccounts.length === 0 ||
      uniqueAvatarAccounts.length > GrAvatarStack.MAX_STACK
    ) {
      return html`<slot name="fallback"></slot>`;
    }
    return uniqueAvatarAccounts.map(
      account =>
        html`<gr-avatar
          .account=${account}
          .imageSize=${this.imageSize}
          aria-label=${getDisplayName(this.config, account)}
        >
          ${when(
            this.enableHover,
            () =>
              html`<gr-hovercard-account
                .account=${account}
              ></gr-hovercard-account>`
          )}
        </gr-avatar>`
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-avatar-stack': GrAvatarStack;
  }
}
