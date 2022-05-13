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
import '../../shared/gr-account-chip/gr-account-chip';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-vote-chip/gr-vote-chip';
import {LitElement, html} from 'lit';
import {customElement, property, state} from 'lit/decorators';

import {
  ChangeInfo,
  AccountInfo,
  ApprovalInfo,
  AccountDetailInfo,
  isDetailedLabelInfo,
  LabelInfo,
} from '../../../types/common';
import {hasOwnProperty} from '../../../utils/common-util';
import {getApprovalInfo, getCodeReviewLabel} from '../../../utils/label-util';
import {sortReviewers} from '../../../utils/attention-set-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {css} from 'lit';
import {nothing} from 'lit';

@customElement('gr-reviewer-list')
export class GrReviewerList extends LitElement {
  /**
   * Fired when the "Add reviewer..." button is tapped.
   *
   * @event show-reply-dialog
   */

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
        .addReviewer iron-icon {
          color: inherit;
          --iron-icon-height: 18px;
          --iron-icon-width: 18px;
        }
        .controlsContainer {
          display: inline-block;
        }
        gr-button.addReviewer {
          --gr-button-padding: 1px 0px;
          vertical-align: top;
          top: 1px;
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
          ${this.displayedReviewers.map(reviewer =>
            this.renderAccountChip(reviewer)
          )}
          <div class="controlsContainer" ?hidden=${!this.mutable}>
            <gr-button
              link
              id="addReviewer"
              class="addReviewer"
              @click=${this.handleAddTap}
              title=${this.ccsOnly ? 'Add CC' : 'Add reviewer'}
              ><iron-icon icon="gr-icons:edit"></iron-icon
            ></gr-button>
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
        .voteableText=${this.computeVoteableText(reviewer)}
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

  /**
   * Returns max permitted score for reviewer.
   */
  private getReviewerPermittedScore(reviewer: AccountInfo, label: string) {
    // Note (issue 7874): sometimes the "all" list is not included in change
    // detail responses, even when DETAILED_LABELS is included in options.
    if (!this.change?.labels) {
      return NaN;
    }
    const detailedLabel = this.change.labels[label];
    if (!isDetailedLabelInfo(detailedLabel) || !detailedLabel.all) {
      return NaN;
    }
    const approvalInfo = getApprovalInfo(detailedLabel, reviewer);
    if (!approvalInfo) {
      return NaN;
    }
    if (hasOwnProperty(approvalInfo, 'permitted_voting_range')) {
      if (!approvalInfo.permitted_voting_range) return NaN;
      return approvalInfo.permitted_voting_range.max;
    } else if (hasOwnProperty(approvalInfo, 'value')) {
      // If present, user can vote on the label.
      return 0;
    }
    return NaN;
  }

  // private but used in tests
  computeVoteableText(reviewer: AccountInfo) {
    const change = this.change;
    if (!change || !change.labels) {
      return '';
    }
    const maxScores = [];
    for (const label of Object.keys(change.labels)) {
      const maxScore = this.getReviewerPermittedScore(reviewer, label);
      if (isNaN(maxScore) || maxScore < 0) {
        continue;
      }
      const scoreLabel = maxScore > 0 ? `+${maxScore}` : `${maxScore}`;
      maxScores.push(`${label}: ${scoreLabel}`);
    }
    return maxScores.join(', ');
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
      reviewersOnly: false,
      ccsOnly: false,
    };
    if (this.reviewersOnly) {
      value.reviewersOnly = true;
    }
    if (this.ccsOnly) {
      value.ccsOnly = true;
    }
    this.dispatchEvent(
      new CustomEvent('show-reply-dialog', {
        detail: {value},
        composed: true,
        bubbles: true,
      })
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-reviewer-list': GrReviewerList;
  }
}
