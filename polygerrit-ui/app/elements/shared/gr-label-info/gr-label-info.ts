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
  AccountInfo,
  LabelInfo,
  ApprovalInfo,
  AccountId,
  isDetailedLabelInfo,
} from '../../../types/common';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {GrButton} from '../gr-button/gr-button';
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
import {ifDefined} from 'lit/directives/if-defined.js';
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

  private readonly restApiService = getAppContext().restApiService;

  private readonly reporting = getAppContext().reportingService;

  // TODO(TS): not used, remove later
  _xhrPromise?: Promise<void>;

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
        gr-button::part(paper-button) {
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

<<<<<<< HEAD   (6a972b5d5f90228cd7bfff7a4b3474f15a038b08 Merge branch 'stable-3.10' into stable-3.11)
  private renderRemoveVote(reviewer: AccountInfo) {
||||||| BASE   (300b5cddf3c60c040867e281c93f624d505f0ae6 Merge "UI: Replace ".value" with "value" everywhere for md-o)
  private renderRemoveVote(reviewer: AccountInfo) {
    const accountId = reviewer._account_id;
    const canDeleteVote = this.canDeleteVote(
      reviewer,
      this.mutable,
      this.change
    );
    if (!accountId || !canDeleteVote) return;

=======
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

>>>>>>> CHANGE (ffc19983fbf9ccc5f07c65ce66d5eb4fa32f1cf7 Fix Delete vote button not checking removable_labels.)
    return html`<gr-tooltip-content has-tooltip title="Remove vote">
      <gr-button
        link
        aria-label="Remove vote"
        @click=${this.onDeleteVote}
        data-account-id=${ifDefined(reviewer._account_id as number | undefined)}
        class="deleteBtn ${this.computeDeleteClass(
          reviewer,
          this.mutable,
          this.change
        )}"
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
      .sort((r1, r2) => sortReviewers(r1, r2, this.change, this.account));
  }

  /**
   * A user is able to delete a vote iff the mutable property is true and the
   * reviewer that left the vote exists in the list of removable_labels
   * received from the backend.
   *
   * @param reviewer An object describing the reviewer that left the
   *     vote.
   */
<<<<<<< HEAD   (6a972b5d5f90228cd7bfff7a4b3474f15a038b08 Merge branch 'stable-3.10' into stable-3.11)
  private computeDeleteClass(
    reviewer: ApprovalInfo,
||||||| BASE   (300b5cddf3c60c040867e281c93f624d505f0ae6 Merge "UI: Replace ".value" with "value" everywhere for md-o)
  private canDeleteVote(
    reviewer: ApprovalInfo,
=======
  private canDeleteVote(
    reviewer: AccountInfo,
>>>>>>> CHANGE (ffc19983fbf9ccc5f07c65ce66d5eb4fa32f1cf7 Fix Delete vote button not checking removable_labels.)
    mutable: boolean,
    change?: ParsedChangeInfo,
    approvalInfo?: ApprovalInfo
  ) {
<<<<<<< HEAD   (6a972b5d5f90228cd7bfff7a4b3474f15a038b08 Merge branch 'stable-3.10' into stable-3.11)
    if (!mutable || !change || !change.removable_reviewers) {
      return 'hidden';
||||||| BASE   (300b5cddf3c60c040867e281c93f624d505f0ae6 Merge "UI: Replace ".value" with "value" everywhere for md-o)
    if (!mutable || !change || !change.removable_reviewers) {
      return false;
=======
    if (
      !mutable ||
      !change ||
      !approvalInfo ||
      !approvalInfo.value ||
      !change.removable_labels
    ) {
      return false;
>>>>>>> CHANGE (ffc19983fbf9ccc5f07c65ce66d5eb4fa32f1cf7 Fix Delete vote button not checking removable_labels.)
    }
<<<<<<< HEAD   (6a972b5d5f90228cd7bfff7a4b3474f15a038b08 Merge branch 'stable-3.10' into stable-3.11)
    const removable = change.removable_reviewers;
    if (removable.find(r => r._account_id === reviewer?._account_id)) {
      return '';
||||||| BASE   (300b5cddf3c60c040867e281c93f624d505f0ae6 Merge "UI: Replace ".value" with "value" everywhere for md-o)
    const removable = change.removable_reviewers;
    if (removable.find(r => r._account_id === reviewer?._account_id)) {
      return true;
=======
    const removableAccounts =
      change.removable_labels[this.label]?.[valueString(approvalInfo.value)];
    if (!removableAccounts) {
      return false;
>>>>>>> CHANGE (ffc19983fbf9ccc5f07c65ce66d5eb4fa32f1cf7 Fix Delete vote button not checking removable_labels.)
    }
<<<<<<< HEAD   (6a972b5d5f90228cd7bfff7a4b3474f15a038b08 Merge branch 'stable-3.10' into stable-3.11)
    return 'hidden';
||||||| BASE   (300b5cddf3c60c040867e281c93f624d505f0ae6 Merge "UI: Replace ".value" with "value" everywhere for md-o)
    return false;
=======
    return removableAccounts.find(r => r._account_id === reviewer?._account_id);
>>>>>>> CHANGE (ffc19983fbf9ccc5f07c65ce66d5eb4fa32f1cf7 Fix Delete vote button not checking removable_labels.)
  }

  /**
   * Closure annotation for Polymer.prototype.splice is off.
   * For now, suppressing annotations.
   */
  private onDeleteVote(e: MouseEvent) {
    if (!this.change) return;

    e.preventDefault();
    let target = e.composedPath()[0] as GrButton;
    while (!target.classList.contains('deleteBtn')) {
      if (!target.parentElement) {
        return;
      }
      target = target.parentElement as GrButton;
    }

    target.disabled = true;
    const accountID = Number(
      `${target.getAttribute('data-account-id')}`
    ) as AccountId;
    this._xhrPromise = this.restApiService
      .deleteVote(this.change._number, accountID, this.label)
      .then(response => {
        target.disabled = false;
        if (!response.ok) {
          return;
        }
        if (this.change) {
          fireReload(this);
        }
      })
      .catch(err => {
        this.reporting.error('Delete vote', err);
        target.disabled = false;
        return;
      });
  }

  _computeValueTooltip(labelInfo: LabelInfo | undefined, score: string) {
    if (
      !labelInfo ||
      !isDetailedLabelInfo(labelInfo) ||
      !labelInfo.values?.[score]
    ) {
      return '';
    }
    return labelInfo.values[score];
  }
}
