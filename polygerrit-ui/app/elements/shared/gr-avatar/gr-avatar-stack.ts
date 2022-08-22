/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import './gr-avatar';
import {AccountInfo} from '../../../types/common';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {styleMap} from 'lit/directives/style-map.js';
import {uniqueDefinedAvatar} from '../../../utils/account-util';

/**
 * This elements draws stack of avatars overlapped with each other.
 *
 * If accounts is empty or contains accounts with more than MAX_STACK unique
 * avatars the fallback slot is rendered instead.
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
   * The size of avatar element in px.
   *
   * Ignored if avatarStyleSize and stackLeftShift are also set.
   * This controls avatarStyleSize and stackLeftShift.
   *
   * Defaults to imageSize;
   */
  @property({type: Number})
  avatarSize?: number;

  /**
   * Color of the border of indivudual avatar.
   *
   * The border color doesn't match the warning chip in focused and hovered
   * state. Since we are using background color mixing for those states
   * there is currently no easy way to color match the border.
   */
  @property({type: String})
  borderColor = '#ffffff';

  /**
   * Height and width style value of individual avatars.
   *
   * Defaults to imageSize px.
   */
  @property({type: String})
  avatarStyleSize?: string;

  /**
   * margin-right style value of individual avatars responsible for overlapping.
   *
   * Defaults to -imageSize / 2 px
   */
  @property({type: String})
  stackLeftShift?: string;

  static override get styles() {
    return [
      css`
        gr-avatar {
          box-sizing: border-box;
          vertical-align: top;
        }
      `,
    ];
  }

  override render() {
    const uniqueAvatarAccounts = this.accounts
      .filter(account => !!account?.avatars?.[0]?.url)
      .filter(uniqueDefinedAvatar);
    if (
      uniqueAvatarAccounts.length === 0 ||
      uniqueAvatarAccounts.length > GrAvatarStack.MAX_STACK
    ) {
      return html`<slot name="fallback"></slot>`;
    }
    const avatarSize = this.avatarSize ?? this.imageSize;
    const inlineAvatarStyle = {
      height: this.avatarStyleSize ?? `${avatarSize}px`,
      width: this.avatarStyleSize ?? `${avatarSize}px`,
      border: `solid 1px ${this.borderColor}`,
    };
    return html`${uniqueAvatarAccounts.map(
      (account, index) =>
        html`<gr-avatar
          .account=${account}
          .imageSize=${this.imageSize}
          style=${styleMap(
            index + 1 === uniqueAvatarAccounts.length
              ? inlineAvatarStyle
              : {
                  ...inlineAvatarStyle,
                  marginRight:
                    this.stackLeftShift ?? `-${Math.floor(avatarSize / 2)}px`,
                }
          )}
        >
        </gr-avatar>`
    )}`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-avatar-stack': GrAvatarStack;
  }
}
