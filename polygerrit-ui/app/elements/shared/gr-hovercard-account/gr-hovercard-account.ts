/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-avatar/gr-avatar';
import '../gr-button/gr-button';
import '../gr-icon/gr-icon';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import {customElement, property} from 'lit/decorators.js';
import {AccountInfo, ChangeInfo} from '../../../types/common';
import {html, LitElement} from 'lit';
import {HovercardMixin} from '../../../mixins/hovercard-mixin/hovercard-mixin';
import {when} from 'lit/directives/when.js';
import './gr-hovercard-account-contents';

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = HovercardMixin(LitElement);

@customElement('gr-hovercard-account')
export class GrHovercardAccount extends base {
  @property({type: Object})
  account!: AccountInfo;

  /**
   * Optional ChangeInfo object, typically comes from the change page or
   * from a row in a list of search results. This is needed for some change
   * related features like adding the user as a reviewer.
   */
  @property({type: Object})
  change?: ChangeInfo;

  /**
   * Should attention set related features be shown in the component? Note
   * that the information whether the user is in the attention set or not is
   * part of the ChangeInfo object in the change property.
   */
  @property({type: Boolean})
  highlightAttention = false;

  override render() {
    return html`
      <div id="container" role="tooltip" tabindex="-1">
        ${when(
          this._isShowing,
          () =>
            html`<gr-hovercard-account-contents
              .account=${this.account}
              .change=${this.change}
              .highlightAttention=${this.highlightAttention}
              @link-clicked=${this.forceHide}
              @action-taken=${this.mouseHide}
              @attention-set-updated=${this.redirectEventToTarget}
              @hide-alert=${this.redirectEventToTarget}
              @show-alert=${this.redirectEventToTarget}
              @reload=${this.redirectEventToTarget}
            ></gr-hovercard-account-contents>`
        )}
      </div>
    `;
  }

  private redirectEventToTarget(e: CustomEvent<unknown>) {
    this.dispatchEventThroughTarget(e.type, e.detail);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-hovercard-account': GrHovercardAccount;
  }
}
