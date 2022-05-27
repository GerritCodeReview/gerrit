/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../styles/gr-font-styles';
import '../../../styles/gr-voting-styles';
import '../../../styles/shared-styles';
import '../gr-vote-chip/gr-vote-chip';
import '../gr-account-chip/gr-account-chip';
import '../gr-button/gr-button';
import '../gr-icons/gr-icons';
import '../gr-tooltip-content/gr-tooltip-content';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {
  AccountInfo,
  LabelInfo,
  ApprovalInfo,
  AccountId,
  isDetailedLabelInfo,
} from '../../../types/common';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {GrButton} from '../gr-button/gr-button';
import {
  canVote,
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
import {ifDefined} from 'lit/directives/if-defined';
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
   * if true - show all reviewers that can vote on label
   * if false - show only reviewers that voted on label
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
        gr-button[disabled] iron-icon {
          color: var(--border-color);
        }
        iron-icon {
          height: calc(var(--line-height-normal) - 2px);
          width: calc(var(--line-height-normal) - 2px);
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
    const reviewers = (this.change?.reviewers['REVIEWER'] ?? [])
      .filter(reviewer => {
        if (this.showAllReviewers) {
          if (isDetailedLabelInfo(labelInfo)) {
            return canVote(labelInfo, reviewer);
          } else {
            // isQuickLabelInfo
            return hasVoted(labelInfo, reviewer);
          }
        } else {
          // !showAllReviewers
          return hasVoted(labelInfo, reviewer);
        }
      })
      .sort((r1, r2) => sortReviewers(r1, r2, this.change, this.account));
    return html`<div>
      ${reviewers.map(reviewer => this.renderReviewerVote(reviewer))}
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
        : html`${this.renderRemoveVote(reviewer)}`}
    </div>`;
  }

  private renderVoteAbility(reviewer: AccountInfo) {
    if (this.labelInfo && isDetailedLabelInfo(this.labelInfo)) {
      const approvalInfo = getApprovalInfo(this.labelInfo, reviewer);
      if (approvalInfo?.permitted_voting_range) {
        const {min, max} = approvalInfo?.permitted_voting_range;
        return html`<span class="no-votes"
          >Can vote ${valueString(min)}/${valueString(max)}</span
        >`;
      }
    }
    return html`<span class="no-votes">No votes</span>`;
  }

  private renderRemoveVote(reviewer: AccountInfo) {
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
        <iron-icon icon="gr-icons:delete"></iron-icon>
      </gr-button>
    </gr-tooltip-content>`;
  }

  /**
   * A user is able to delete a vote iff the mutable property is true and the
   * reviewer that left the vote exists in the list of removable_reviewers
   * received from the backend.
   *
   * @param reviewer An object describing the reviewer that left the
   *     vote.
   */
  private computeDeleteClass(
    reviewer: ApprovalInfo,
    mutable: boolean,
    change?: ParsedChangeInfo
  ) {
    if (!mutable || !change || !change.removable_reviewers) {
      return 'hidden';
    }
    const removable = change.removable_reviewers;
    if (removable.find(r => r._account_id === reviewer?._account_id)) {
      return '';
    }
    return 'hidden';
  }

  /**
   * Closure annotation for Polymer.prototype.splice is off.
   * For now, suppressing annotations.
   */
  private onDeleteVote(e: MouseEvent) {
    if (!this.change) return;

    e.preventDefault();
    let target = (dom(e) as EventApi).rootTarget as GrButton;
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
        this.reporting.error(err);
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
