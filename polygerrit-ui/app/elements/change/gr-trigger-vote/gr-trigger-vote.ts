/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-label-info/gr-label-info';
import '../../shared/gr-vote-chip/gr-vote-chip';
import '../gr-trigger-vote-hovercard/gr-trigger-vote-hovercard';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {ParsedChangeInfo} from '../../../types/types';
import {
  AccountInfo,
  isDetailedLabelInfo,
  isQuickLabelInfo,
  LabelInfo,
} from '../../../api/rest-api';
import {
  getAllUniqueApprovals,
  hasNeutralStatus,
} from '../../../utils/label-util';

@customElement('gr-trigger-vote')
export class GrTriggerVote extends LitElement {
  @property()
  label?: string;

  @property({type: Object})
  labelInfo?: LabelInfo;

  @property({type: Object})
  change?: ParsedChangeInfo;

  @property({type: Object})
  account?: AccountInfo;

  /**
   * If defined, trigger-vote is shown with this value instead of the latest
   * vote. This is useful for change log.
   */
  @property()
  displayValue?: string;

  @property({type: Boolean})
  mutable?: boolean;

  @property({type: Boolean, attribute: 'disable-hovercards'})
  disableHovercards = false;

  static override get styles() {
    return css`
      :host {
        display: block;
      }
      .container {
        box-sizing: border-box;
        border: 1px solid var(--border-color);
        border-radius: calc(var(--border-radius) + 2px);
        background-color: var(--background-color-primary);
        display: flex;
        padding: 0;
        padding-left: var(--spacing-s);
        padding-right: var(--spacing-xxs);
        align-items: center;
      }
      .label {
        padding-right: var(--spacing-s);
        font-weight: var(--font-weight-bold);
      }
      gr-vote-chip {
        --gr-vote-chip-width: 14px;
        --gr-vote-chip-height: 14px;
        margin-right: 0px;
        margin-left: var(--spacing-xs);
      }
      gr-vote-chip:first-of-type {
        margin-left: 0px;
      }
    `;
  }

  override render() {
    if (!this.labelInfo) return;
    return html`
      <div class="container">
        ${this.renderHovercard()}
        <span class="label">${this.label}</span>
        ${this.renderVotes()}
      </div>
    `;
  }

  private renderHovercard() {
    if (this.disableHovercards) return;
    return html`<gr-trigger-vote-hovercard
      .labelName=${this.label}
      .labelInfo=${this.labelInfo}
    >
      <gr-label-info
        slot="label-info"
        .change=${this.change}
        .account=${this.account}
        .mutable=${this.mutable}
        .label=${this.label}
        .labelInfo=${this.labelInfo}
        .showAllReviewers=${false}
      ></gr-label-info>
    </gr-trigger-vote-hovercard>`;
  }

  private renderVotes() {
    const {labelInfo} = this;
    if (!labelInfo) return;
    if (this.displayValue)
      return html`<gr-vote-chip
        .displayValue=${this.displayValue}
        .label=${labelInfo}
      ></gr-vote-chip>`;
    if (isDetailedLabelInfo(labelInfo)) {
      const approvals = getAllUniqueApprovals(labelInfo).filter(
        approval => !hasNeutralStatus(labelInfo, approval)
      );
      return approvals.map(
        approvalInfo => html`<gr-vote-chip
          .vote=${approvalInfo}
          .label=${labelInfo}
        ></gr-vote-chip>`
      );
    } else if (isQuickLabelInfo(labelInfo)) {
      return [html`<gr-vote-chip .label=${this.labelInfo}></gr-vote-chip>`];
    } else {
      return html``;
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-trigger-vote': GrTriggerVote;
  }
}
