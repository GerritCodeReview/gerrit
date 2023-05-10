/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-account-chip/gr-account-chip';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-icon/gr-icon';
import '../../shared/gr-vote-chip/gr-vote-chip';
import {LitElement, html} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';

import {
  ChangeInfo,
  AccountInfo,
  ApprovalInfo,
  AccountDetailInfo,
  isDetailedLabelInfo,
  LabelInfo,
} from '../../../types/common';
import {getApprovalInfo, getCodeReviewLabel} from '../../../utils/label-util';
import {sortReviewers} from '../../../utils/attention-set-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {css} from 'lit';
import {nothing} from 'lit';
import {fire} from '../../../utils/event-util';
import {ShowReplyDialogEvent} from '../../../types/events';
import {repeat} from 'lit/directives/repeat.js';
import {accountKey} from '../../../utils/account-util';

@customElement('gr-reviewer-list')
export class GrReviewerList extends LitElement {
  @property({type: Object}) change?: ChangeInfo;

  @property({type: Object}) account?: AccountDetailInfo;

  @property({type: Boolean, reflect: true}) disabled = false;

  @property({type: Boolean}) mutable = false;

  @property({type: Boolean, attribute: 'reviewers-only'}) reviewersOnly = false;

  @property({type: Boolean, attribute: 'ccs-only'}) ccsOnly = false;

  @state() displayedReviewers: AccountInfo[] = [];

  @state() reviewers: AccountInfo[] = [];

  @state() hiddenReviewerCount?: number;

  @state() showAllReviewers = false;

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: block;
        }
        :host([disabled]) {
          opacity: 0.8;
          pointer-events: none;
        }
        .container {
          display: block;
          /* line-height-normal for the chips, 2px for the chip border, spacing-s
            for the gap between lines, negative bottom margin for eliminating the
            gap after the last line */
          line-height: calc(var(--line-height-normal) + 2px + var(--spacing-s));
          margin-bottom: calc(0px - var(--spacing-s));
        }
        .addReviewer gr-icon {
          color: inherit;
        }
        .controlsContainer {
          display: inline-block;
        }
        gr-button.addReviewer {
          vertical-align: top;
          --gr-button-padding: var(--spacing-s);
          --margin: calc(0px - var(--spacing-s));
        }
        gr-button {
          line-height: var(--line-height-normal);
          --gr-button-padding: 0px;
        }
        gr-account-chip {
          line-height: var(--line-height-normal);
          vertical-align: top;
          display: inline-block;
        }
        gr-vote-chip {
          --gr-vote-chip-width: 14px;
          --gr-vote-chip-height: 14px;
        }
      `,
    ];
  }

  override render() {
    this.displayedReviewers = this.computeDisplayedReviewers() ?? [];
    this.hiddenReviewerCount =
      this.reviewers.length - this.displayedReviewers.length;
    return html`
      <div class="container">
        <div>
          ${repeat(
            this.displayedReviewers,
            reviewer => accountKey(reviewer),
            reviewer => this.renderAccountChip(reviewer)
          )}
          <div class="controlsContainer" ?hidden=${!this.mutable}>
            <gr-button
              link
              id="addReviewer"
              class="addReviewer"
              @click=${this.handleAddTap}
              title=${this.ccsOnly ? 'Add CC' : 'Add reviewer'}
            >
              <div>
                <gr-icon icon="edit" filled small></gr-icon>
              </div>
            </gr-button>
          </div>
        </div>
        <gr-button
          class="hiddenReviewers"
          link=""
          ?hidden=${!this.hiddenReviewerCount}
          @click=${() => {
            this.showAllReviewers = true;
          }}
          >and ${this.hiddenReviewerCount} more</gr-button
        >
      </div>
    `;
  }

  private renderAccountChip(reviewer: AccountInfo) {
    const change = this.change;
    if (!change) return nothing;
    return html`
      <gr-account-chip
        class="reviewer"
        .account=${reviewer}
        .change=${change}
        highlightAttention
        .vote=${this.computeVote(reviewer)}
        .label=${this.computeCodeReviewLabel()}
      >
        <gr-vote-chip
          slot="vote-chip"
          .vote=${this.computeVote(reviewer)}
          .label=${this.computeCodeReviewLabel()}
          circle-shape
        ></gr-vote-chip>
      </gr-account-chip>
    `;
  }

  private computeVote(reviewer: AccountInfo): ApprovalInfo | undefined {
    const codeReviewLabel = this.computeCodeReviewLabel();
    if (!codeReviewLabel || !isDetailedLabelInfo(codeReviewLabel)) return;
    return getApprovalInfo(codeReviewLabel, reviewer);
  }

  private computeCodeReviewLabel(): LabelInfo | undefined {
    if (!this.change?.labels) return;
    return getCodeReviewLabel(this.change.labels);
  }

  private computeDisplayedReviewers() {
    if (this.change?.owner === undefined) {
      return;
    }
    let result: AccountInfo[] = [];
    const reviewers = this.change.reviewers;
    for (const key of Object.keys(reviewers)) {
      if (this.reviewersOnly && key !== 'REVIEWER') {
        continue;
      }
      if (this.ccsOnly && key !== 'CC') {
        continue;
      }
      if (key === 'REVIEWER' || key === 'CC') {
        result = result.concat(reviewers[key]!);
      }
    }
    this.reviewers = result
      .filter(
        reviewer => reviewer._account_id !== this.change?.owner._account_id
      )
      .sort((r1, r2) => sortReviewers(r1, r2, this.change, this.account));

    if (this.reviewers.length > 8 && !this.showAllReviewers) {
      return this.reviewers.slice(0, 6);
    } else {
      return this.reviewers;
    }
  }

  // private but used in tests
  handleAddTap(e: Event) {
    e.preventDefault();
    const value = {
      reviewersOnly: this.reviewersOnly,
      ccsOnly: this.ccsOnly,
    };
    fire(this, 'show-reply-dialog', {value});
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-reviewer-list': GrReviewerList;
  }
  interface HTMLElementEventMap {
    /** Fired when the "Add reviewer..." button is tapped. */
    'show-reply-dialog': ShowReplyDialogEvent;
  }
}
