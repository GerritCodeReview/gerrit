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
import '../../../styles/shared-styles';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {htmlTemplate} from './gr-reviewer-list_html';
import {LitElement, PropertyValues, html} from 'lit';
import {customElement, query, property, state} from 'lit/decorators';
import {ifDefined} from 'lit/directives/if-defined';

import {
  ChangeInfo,
  AccountInfo,
  ApprovalInfo,
  Reviewers,
  AccountId,
  EmailAddress,
  AccountDetailInfo,
  isDetailedLabelInfo,
  LabelInfo,
} from '../../../types/common';
import {GrAccountChip} from '../../shared/gr-account-chip/gr-account-chip';
import {hasOwnProperty} from '../../../utils/common-util';
import {isRemovableReviewer} from '../../../utils/change-util';
import {ReviewerState} from '../../../constants/constants';
import {getAppContext} from '../../../services/app-context';
import {fireAlert} from '../../../utils/event-util';
import {
  getApprovalInfo,
  getCodeReviewLabel,
  showNewSubmitRequirements,
} from '../../../utils/label-util';
import {sortReviewers} from '../../../utils/attention-set-util';
import {KnownExperimentId} from '../../../services/flags/flags';
import {LabelNameToValuesMap} from '../../../api/rest-api';
import {sharedStyles} from '../../../styles/shared-styles';
import {css} from 'lit';
import {nothing} from 'lit';

@customElement('gr-reviewer-list')
export class GrReviewerList extends LitElement {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the "Add reviewer..." button is tapped.
   *
   * @event show-reply-dialog
   */

  @property() change?: ChangeInfo;

  @property() account?: AccountDetailInfo;

  @property({reflect: true}) disabled = false;

  @property() mutable = false;

  @property() reviewersOnly = false;

  @property() ccsOnly = false;

  @state() displayedReviewers: AccountInfo[] = [];

  @state() reviewers: AccountInfo[] = [];

  @state() _showInput = false;

  @state() addLabel = '';

  @state() _hiddenReviewerCount?: number;

  private xhrPromise?: Promise<Response | undefined>;

  private readonly restApiService = getAppContext().restApiService;

  private readonly flagsService = getAppContext().flagsService;

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

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('ccsOnly')) {
      this.addLabel = this.ccsOnly ? 'Add CC' : 'Add reviewer';
    }
    if (
      changedProperties.has('reviewers') &&
      changedProperties.has('displayedReviewers')
    ) {
      if (
        this.reviewers === undefined ||
        this.displayedReviewers === undefined
      ) {
        this._hiddenReviewerCount = undefined;
      }
      this._hiddenReviewerCount =
        this.reviewers.length - this.displayedReviewers.length;
    }
  }

  override render() {
    return html`
      <div class="container">
        <div>
          ${this.displayedReviewers.map(reviewer =>
            this.renderAccountChip(reviewer)
          )}
          <div class="controlsContainer" hidden="${!this.mutable}">
            <gr-button
              link=""
              id="addReviewer"
              class="addReviewer"
              @click="${(e: Event) => this.handleAddTap(e)}"
              $title="${ifDefined(this.addLabel)}"
              ><iron-icon icon="gr-icons:edit"></iron-icon
            ></gr-button>
          </div>
        </div>
        <gr-button
          class="hiddenReviewers"
          link=""
          hidden="${!this._hiddenReviewerCount}"
          @click="${() => this.handleViewAll()}"
          >and ${this._hiddenReviewerCount} more</gr-button
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
        .account="${reviewer}"
        .change="${change}"
        @remove="${(e: Event) => this.handleRemove(e)}"
        .highlightAttention=${true}
        .voteable-text="${this.computeVoteableText(reviewer, change)}"
        .removable="${this.computeCanRemoveReviewer(reviewer, this.mutable)}"
        .vote="${this.computeVote(reviewer, change)}"
        .label="${this.computeCodeReviewLabel(change)}"
      >
        ${showNewSubmitRequirements(this.flagsService, this.change)
          ? html`<gr-vote-chip
              slot="vote-chip"
              .vote="${this.computeVote(reviewer, change)}"
              .label="${this.computeCodeReviewLabel(change)}"
              circle-shape
            ></gr-vote-chip>`
          : nothing}
      </gr-account-chip>
    `;
  }

  /**
   * Converts change.permitted_labels to an array of hashes of label keys to
   * numeric scores.
   * Example:
   * [{
   *   'Code-Review': ['-1', ' 0', '+1']
   * }]
   * will be converted to
   * [{
   *   label: 'Code-Review',
   *   scores: [-1, 0, 1]
   * }]
   */
  _permittedLabelsToNumericScores(labels: LabelNameToValuesMap | undefined) {
    if (!labels) return [];
    return Object.keys(labels).map(label => {
      return {
        label,
        scores: labels[label].map(v => Number(v)),
      };
    });
  }

  /**
   * Returns max permitted score for reviewer.
   */
  _getReviewerPermittedScore(
    reviewer: AccountInfo,
    change: ChangeInfo,
    label: string
  ) {
    // Note (issue 7874): sometimes the "all" list is not included in change
    // detail responses, even when DETAILED_LABELS is included in options.
    if (!change.labels) {
      return NaN;
    }
    const detailedLabel = change.labels[label];
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

  computeVoteableText(reviewer: AccountInfo, change: ChangeInfo) {
    if (!change || !change.labels) {
      return '';
    }
    const maxScores = [];
    for (const label of Object.keys(change.labels)) {
      const maxScore = this._getReviewerPermittedScore(reviewer, change, label);
      if (isNaN(maxScore) || maxScore < 0) {
        continue;
      }
      const scoreLabel = maxScore > 0 ? `+${maxScore}` : `${maxScore}`;
      maxScores.push(`${label}: ${scoreLabel}`);
    }
    return maxScores.join(', ');
  }

  computeVote(
    reviewer: AccountInfo,
    change?: ChangeInfo
  ): ApprovalInfo | undefined {
    const codeReviewLabel = this.computeCodeReviewLabel(change);
    if (!codeReviewLabel || !isDetailedLabelInfo(codeReviewLabel)) return;
    return getApprovalInfo(codeReviewLabel, reviewer);
  }

  computeCodeReviewLabel(change?: ChangeInfo): LabelInfo | undefined {
    if (!change || !change.labels) return;
    return getCodeReviewLabel(change.labels);
  }

  @observe('change.reviewers.*', 'change.owner')
  reviewersChanged(
    changeRecord: PolymerDeepPropertyChange<Reviewers, Reviewers>,
    owner: AccountInfo
  ) {
    // Polymer 2: check for undefined
    if (
      changeRecord === undefined ||
      owner === undefined ||
      this.change === undefined
    ) {
      return;
    }
    let result: AccountInfo[] = [];
    const reviewers = changeRecord.base;
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
      .filter(reviewer => reviewer._account_id !== owner._account_id)
      .sort((r1, r2) => sortReviewers(r1, r2, this.change, this.account));

    if (this.reviewers.length > 8) {
      this.displayedReviewers = this.reviewers.slice(0, 6);
    } else {
      this.displayedReviewers = this.reviewers;
    }
  }

  private computeCanRemoveReviewer(reviewer: AccountInfo, mutable: boolean) {
    if (this.flagsService.isEnabled(KnownExperimentId.SUBMIT_REQUIREMENTS_UI)) {
      return false;
    }
    return mutable && isRemovableReviewer(this.change, reviewer);
  }

  private handleRemove(e: Event) {
    e.preventDefault();
    const target = (dom(e) as EventApi).rootTarget as GrAccountChip;
    if (!target.account || !this.change?.reviewers) return;
    const accountID = target.account._account_id || target.account.email;
    if (!accountID) return;
    const reviewers = this.change.reviewers;
    let removedAccount: AccountInfo | undefined;
    let removedType: ReviewerState | undefined;
    for (const type of [ReviewerState.REVIEWER, ReviewerState.CC]) {
      const reviewerStateByType = reviewers[type] || [];
      reviewers[type] = reviewerStateByType;
      for (let i = 0; i < reviewerStateByType.length; i++) {
        if (
          reviewerStateByType[i]._account_id === accountID ||
          reviewerStateByType[i].email === accountID
        ) {
          removedAccount = reviewerStateByType[i];
          removedType = type;
          this.splice(`change.reviewers.${type}`, i, 1);
          break;
        }
      }
    }
    const curChange = this.change;
    this.disabled = true;
    this.xhrPromise = this.removeReviewer(accountID)
      .then(response => {
        this.disabled = false;
        if (!this.change?.reviewers || this.change !== curChange) return;
        if (!response?.ok) {
          this.push(`change.reviewers.${removedType}`, removedAccount);
          fireAlert(this, `Cannot remove a ${removedType}`);
          return response;
        }
        return;
      })
      .catch((err: Error) => {
        this.disabled = false;
        throw err;
      });
  }

  private handleAddTap(e: Event) {
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

  private handleViewAll() {
    this.displayedReviewers = this.reviewers;
  }

  private removeReviewer(
    id: AccountId | EmailAddress
  ): Promise<Response | undefined> {
    if (!this.change) return Promise.resolve(undefined);
    return this.restApiService.removeChangeReviewer(this.change._number, id);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-reviewer-list': GrReviewerList;
  }
}
