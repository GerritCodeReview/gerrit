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

import '../gr-account-label/gr-account-label';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {AccountInfo, ChangeInfo} from '../../../types/common';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {ParsedChangeInfo} from '../../../types/types';

@customElement('gr-account-link')
export class GrAccountLink extends LitElement {
  @property({type: String})
  voteableText?: string;

  @property({type: Object})
  account?: AccountInfo;

  /**
   * Optional ChangeInfo object, typically comes from the change page or
   * from a row in a list of search results. This is needed for some change
   * related features like adding the user as a reviewer.
   */
  @property({type: Object})
  change?: ChangeInfo | ParsedChangeInfo;

  /**
   * Should this user be considered to be in the attention set, regardless
   * of the current state of the change object?
   */
  @property({type: Boolean})
  forceAttention = false;

  /**
   * Should attention set related features be shown in the component? Note
   * that the information whether the user is in the attention set or not is
   * part of the ChangeInfo object in the change property.
   */
  @property({type: Boolean})
  highlightAttention = false;

  @property({type: Boolean})
  hideAvatar = false;

  @property({type: Boolean})
  hideStatus = false;

  /**
   * Only show the first name in the account label.
   */
  @property({type: Boolean})
  firstName = false;

  static get styles() {
    return [
      css`
        :host {
          display: inline-block;
          vertical-align: top;
        }
        a {
          color: var(--primary-text-color);
          text-decoration: none;
        }
        gr-account-label::part(gr-account-label-text):hover {
          text-decoration: underline !important;
        }
      `,
    ];
  }

  override render() {
    if (!this.account) return;
    return html`<span>
      <a href="${this._computeOwnerLink(this.account)}">
        <gr-account-label
          .account="${this.account}"
          .change="${this.change}"
          ?forceAttention=${this.forceAttention}
          ?highlightAttention=${this.highlightAttention}
          ?hideAvatar=${this.hideAvatar}
          ?hideStatus=${this.hideStatus}
          ?firstName=${this.firstName}
          .voteableText=${this.voteableText}
          exportparts="gr-account-label-text: gr-account-link-text"
        >
        </gr-account-label>
      </a>
    </span>`;
  }

  _computeOwnerLink(account?: AccountInfo) {
    if (!account) {
      return;
    }
    return GerritNav.getUrlForOwner(
      account.email ||
        account.username ||
        account.name ||
        `${account._account_id}`
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-account-link': GrAccountLink;
  }
}
