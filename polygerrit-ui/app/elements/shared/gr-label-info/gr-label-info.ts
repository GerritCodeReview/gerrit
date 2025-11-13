/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../styles/gr-font-styles';
import '../../../styles/gr-voting-styles';
import '../../../styles/shared-styles';
import '../gr-icon/gr-icon';
import '../gr-vote-chip/gr-vote-chip';
import '../gr-account-chip/gr-account-chip';
import '../gr-button/gr-button';
import '../gr-tooltip-content/gr-tooltip-content';
import {
  AccountId,
  AccountInfo,
  ApprovalInfo,
  isDetailedLabelInfo,
  LabelInfo,
} from '../../../types/common';
import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {
  canReviewerVote,
  getApprovalInfo,
  hasNeutralStatus,
  hasVoted,
  valueString,
} from '../../../utils/label-util';
import {getAppContext} from '../../../services/app-context';
import {ParsedChangeInfo} from '../../../types/types';
import {fontStyles} from '../../../styles/gr-font-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {votingStyles} from '../../../styles/gr-voting-styles';
import {fireReload} from '../../../utils/event-util';
import {sortReviewers} from '../../../utils/attention-set-util';

declare global {
  interface HTMLElementTagNameMap {
    'gr-label-info': GrLabelInfo;
  }
}

@customElement('gr-label-info')
export class GrLabelInfo extends LitElement {
  @property({type: Object})
  labelInfo?: LabelInfo;

  @property({type: String})
  label = '';

  @property({type: Object})
  change?: ParsedChangeInfo;

  @property({type: Object})
  account?: AccountInfo;

  /**
   * A user is able to delete a vote iff the mutable property is true and the
   * reviewer that left the vote exists in the list of removable_reviewers
   * received from the backend.
   */
  @property({type: Boolean})
  mutable = false;

  /**
   * if true - show all CC and reviewers who already voted and reviewers who can
   * vote on label.
   * if false - show only all CC and reviewers who already voted
   */
  @property({type: Boolean})
  showAllReviewers = true;

  @state() private deleteButtonDisabled = false;

  private readonly restApiService = getAppContext().restApiService;

  private readonly reporting = getAppContext().reportingService;

  static override get styles() {
    return [
      sharedStyles,
      fontStyles,
      votingStyles,
      css`
        .hidden {
          display: none;
        }
        /* Note that most of the .voteChip styles are coming from the
         gr-voting-styles include. */
        .voteChip {
          display: flex;
          justify-content: center;
          margin-right: var(--spacing-s);
          padding: 1px;
        }
        gr-tooltip-content {
          display: block;
        }
        gr-button {
          vertical-align: top;
        }
        gr-button::part(md-text-button) {
          height: var(--line-height-normal);
          width: var(--line-height-normal);
          padding: 0;
        }
        gr-button[disabled] gr-icon {
          color: var(--border-color);
        }
        gr-icon {
          font-size: calc(var(--line-height-normal) - 2px);
        }
        .reviewer-row {
          padding-top: var(--spacing-s);
        }
        .reviewer-row:first-of-type {
          padding-top: 0;
        }
        .reviewer-row gr-account-chip,
        .reviewer-row gr-tooltip-content {
          display: inline-block;
          vertical-align: top;
        }
        .reviewer-row .no-votes {
          color: var(--deemphasized-text-color);
          margin-left: var(--spacing-xs);
        }
        gr-vote-chip {
          --gr-vote-chip-width: 14px;
          --gr-vote-chip-height: 14px;
        }
      `,
    ];
  }

  override render() {
    const labelInfo = this.labelInfo;
    if (!labelInfo) return;
    return html`<div>
      ${this.computeVoters(labelInfo).map(reviewer =>
        this.renderReviewerVote(reviewer)
      )}
    </div>`;
  }

  renderReviewerVote(reviewer: AccountInfo) {
    const labelInfo = this.labelInfo;
    if (!labelInfo) return;
    const approvalInfo = isDetailedLabelInfo(labelInfo)
      ? getApprovalInfo(labelInfo, reviewer)
      : undefined;
    const noVoteYet =
      !hasVoted(labelInfo, reviewer) ||
      (isDetailedLabelInfo(labelInfo) &&
        hasNeutralStatus(labelInfo, approvalInfo));
    return html`<div class="reviewer-row">
      <gr-account-chip
        .account=${reviewer}
        .change=${this.change}
        .vote=${approvalInfo}
        .label=${labelInfo}
      >
        <gr-vote-chip
          slot="vote-chip"
          .vote=${approvalInfo}
          .label=${labelInfo}
          circle-shape
        ></gr-vote-chip
      ></gr-account-chip>
      ${noVoteYet
        ? this.renderVoteAbility(reviewer)
        : html`${this.renderRemoveVote(reviewer, approvalInfo)}`}
    </div>`;
  }

  private renderVoteAbility(reviewer: AccountInfo) {
    if (this.labelInfo && isDetailedLabelInfo(this.labelInfo)) {
      const approvalInfo = getApprovalInfo(this.labelInfo, reviewer);
      if (approvalInfo?.permitted_voting_range) {
        const {min, max} = approvalInfo.permitted_voting_range;
        return html`<span class="no-votes"
          >Can vote ${valueString(min)}/${valueString(max)}</span
        >`;
      }
    }
    return html`<span class="no-votes">No votes</span>`;
  }

  private renderRemoveVote(
    reviewer: AccountInfo,
    approvalInfo: ApprovalInfo | undefined
  ) {
    const accountId = reviewer._account_id;
    const canDeleteVote = this.canDeleteVote(
      reviewer,
      this.mutable,
      this.change,
      approvalInfo
    );
    if (!accountId || !canDeleteVote) return;

    return html`<gr-tooltip-content has-tooltip title="Remove vote">
      <gr-button
        link
        aria-label="Remove vote"
        @click=${() => this.onDeleteVote(accountId)}
        ?disabled=${this.deleteButtonDisabled}
        class="deleteBtn"
      >
        <gr-icon icon="delete" filled></gr-icon>
      </gr-button>
    </gr-tooltip-content>`;
  }

  /**
   * if showAllReviewers = true  @return all CC and reviewers who already voted
   * and reviewers who can vote on label
   * Btw. if label is QuickLabelInfo we cannot provide list of reviewers who can
   * vote on label
   *
   * if showAllReviewers = false @return just all CC and reviewers who already
   * voted
   *
   * private but used in test
   */
  computeVoters(labelInfo: LabelInfo) {
    const allReviewers = this.change?.reviewers['REVIEWER'] ?? [];
    return allReviewers
      .concat(this.change?.reviewers['CC'] ?? [])
      .filter(account => {
        if (this.showAllReviewers) {
          if (
            isDetailedLabelInfo(labelInfo) &&
            allReviewers.includes(account)
          ) {
            return canReviewerVote(labelInfo, account);
          } else {
            // labelInfo is QuickLabelInfo or account is from CC
            return hasVoted(labelInfo, account);
          }
        } else {
          // !showAllReviewers
          return hasVoted(labelInfo, account);
        }
      })
      .sort((r1, r2) =>
        sortReviewers(
          r1,
          r2,
          this.change?.attention_set,
          this.change?.labels,
          this.account
        )
      );
  }

  /**
   * A user is able to delete a vote iff the mutable property is true and the
   * reviewer that left the vote exists in the list of removable_labels
   * received from the backend.
   */
  private canDeleteVote(
    reviewer: AccountInfo,
    mutable: boolean,
    change?: ParsedChangeInfo,
    approvalInfo?: ApprovalInfo
  ) {
    if (
      !mutable ||
      !change ||
      !approvalInfo ||
      !approvalInfo.value ||
      !change.removable_labels
    ) {
      return false;
    }
    const removableAccounts =
      change.removable_labels[this.label]?.[valueString(approvalInfo.value)];
    if (!removableAccounts) {
      return false;
    }
    return removableAccounts.find(r => r._account_id === reviewer?._account_id);
  }

  private async onDeleteVote(accountId: AccountId) {
    if (!this.change || !accountId) return;

    this.deleteButtonDisabled = true;
    try {
      const response = await this.restApiService.deleteVote(
        this.change._number,
        accountId,
        this.label
      );
      if (response.ok && this.change) fireReload(this);
    } catch (err) {
      this.reporting.error('Delete vote', err as Error);
    } finally {
      this.deleteButtonDisabled = false;
    }
  }
}
