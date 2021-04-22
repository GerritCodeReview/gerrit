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
import '../../../styles/shared-styles';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-reviewer-list_html';
import {isSelf, isServiceUser} from '../../../utils/account-util';
import {hasAttention} from '../../../utils/attention-set-util';
import {html} from 'lit-html';
import {
  customElement as customPolymerELement, // hello
  property as pproperty,
  computed,
  observe,
} from '@polymer/decorators';
import {customElement, property} from 'lit-element';
import {
  ChangeInfo,
  ServerInfo,
  LabelNameToValueMap,
  AccountInfo,
  ApprovalInfo,
  Reviewers,
  AccountId,
  DetailedLabelInfo,
  EmailAddress,
  AccountDetailInfo,
} from '../../../types/common';
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import {GrAccountChip} from '../../shared/gr-account-chip/gr-account-chip';
import {hasOwnProperty} from '../../../utils/common-util';
import {isRemovableReviewer} from '../../../utils/change-util';
import {ReviewerState} from '../../../constants/constants';
import {appContext} from '../../../services/app-context';
import {GrLitElement} from '../../lit/gr-lit-element';

@customPolymerELement('gr-reviewer-list2')
export class GrReviewerList2 extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the "Add reviewer..." button is tapped.
   *
   * @event show-reply-dialog
   */

  @pproperty({type: Object})
  change?: ChangeInfo;

  @pproperty({type: Object})
  account?: AccountDetailInfo;

  @pproperty({type: Object})
  serverConfig?: ServerInfo;

  @pproperty({type: Boolean, reflectToAttribute: true})
  disabled = false;

  @pproperty({type: Boolean})
  mutable = false;

  @pproperty({type: Boolean})
  reviewersOnly = false;

  @pproperty({type: Boolean})
  ccsOnly = false;

  @pproperty({type: Array})
  _displayedReviewers: AccountInfo[] = [];

  @pproperty({type: Array})
  _reviewers: AccountInfo[] = [];

  @pproperty({type: Boolean})
  _showInput = false;

  @pproperty({type: Object})
  _xhrPromise?: Promise<Response | undefined>;

  private readonly restApiService = appContext.restApiService;

  @computed('ccsOnly')
  get _addLabel() {
    return this.ccsOnly ? 'Add CC' : 'Add reviewer';
  }

  @computed('_reviewers', '_displayedReviewers')
  get _hiddenReviewerCount() {
    // Polymer 2: check for undefined
    if (
      this._reviewers === undefined ||
      this._displayedReviewers === undefined
    ) {
      return undefined;
    }
    return this._reviewers.length - this._displayedReviewers.length;
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
  _permittedLabelsToNumericScores(labels: LabelNameToValueMap | undefined) {
    if (!labels) return [];
    return Object.keys(labels).map(label => {
      return {
        label,
        scores: labels[label].map(v => Number(v)),
      };
    });
  }

  /**
   * Returns hash of labels to max permitted score.
   *
   * @returns labels to max permitted scores hash
   */
  _getMaxPermittedScores(change: ChangeInfo) {
    return this._permittedLabelsToNumericScores(change.permitted_labels)
      .map(({label, scores}) => {
        return {
          [label]: scores.reduce((a, b) => Math.max(a, b)),
        };
      })
      .reduce((acc, i) => Object.assign(acc, i), {});
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
    const detailedLabel = change.labels[label] as DetailedLabelInfo;
    if (!detailedLabel.all) {
      return NaN;
    }
    const detailed = detailedLabel.all
      .filter(
        (approval: ApprovalInfo) =>
          reviewer._account_id === approval._account_id
      )
      .pop();
    if (!detailed) {
      return NaN;
    }
    if (hasOwnProperty(detailed, 'permitted_voting_range')) {
      if (!detailed.permitted_voting_range) return NaN;
      return detailed.permitted_voting_range.max;
    } else if (hasOwnProperty(detailed, 'value')) {
      // If preset, user can vote on the label.
      return 0;
    }
    return NaN;
  }

  _computeVoteableText(reviewer: AccountInfo, change: ChangeInfo) {
    if (!change || !change.labels) {
      return '';
    }
    const maxScores = [];
    const maxPermitted = this._getMaxPermittedScores(change);
    for (const label of Object.keys(change.labels)) {
      const maxScore = this._getReviewerPermittedScore(reviewer, change, label);
      if (isNaN(maxScore) || maxScore < 0) {
        continue;
      }
      if (maxScore > 0 && maxScore === maxPermitted[label]) {
        maxScores.push(`${label}: +${maxScore}`);
      } else {
        maxScores.push(`${label}`);
      }
    }
    return maxScores.join(', ');
  }

  @observe('change.reviewers.*', 'change.owner', 'serverConfig')
  _reviewersChanged(
    changeRecord: PolymerDeepPropertyChange<Reviewers, Reviewers>,
    owner: AccountInfo,
    serverConfig: ServerInfo
  ) {
    // Polymer 2: check for undefined
    if (
      changeRecord === undefined ||
      owner === undefined ||
      serverConfig === undefined ||
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
    this._reviewers = result
      .filter(reviewer => reviewer._account_id !== owner._account_id)
      // Sort order:
      // 1. The user themselves
      // 2. Human users in the attention set.
      // 3. Other human users.
      // 4. Service users.
      .sort((r1, r2) => {
        if (this.account) {
          if (isSelf(r1, this.account)) return -1;
          if (isSelf(r2, this.account)) return 1;
        }
        const a1 = hasAttention(serverConfig, r1, this.change!) ? 1 : 0;
        const a2 = hasAttention(serverConfig, r2, this.change!) ? 1 : 0;
        const s1 = isServiceUser(r1) ? -2 : 0;
        const s2 = isServiceUser(r2) ? -2 : 0;
        return a2 - a1 + s2 - s1;
      });

    if (this._reviewers.length > 8) {
      this._displayedReviewers = this._reviewers.slice(0, 6);
    } else {
      this._displayedReviewers = this._reviewers;
    }
  }

  _computeCanRemoveReviewer(reviewer: AccountInfo, mutable: boolean) {
    return mutable && isRemovableReviewer(this.change, reviewer);
  }

  _handleRemove(e: Event) {
    e.preventDefault();
    const target = (dom(e) as EventApi).rootTarget as GrAccountChip;
    if (!target.account || !this.change) {
      return;
    }
    const accountID = target.account._account_id || target.account.email;
    this.disabled = true;
    if (!accountID) return;
    this._xhrPromise = this._removeReviewer(accountID)
      .then((response: Response | undefined) => {
        this.disabled = false;
        if (!response || !response.ok) {
          return response;
        }
        if (!this.change || !this.change.reviewers) return;
        const reviewers = this.change.reviewers;
        for (const type of [ReviewerState.REVIEWER, ReviewerState.CC]) {
          const reviewerStateByType = reviewers[type] || [];
          reviewers[type] = reviewerStateByType;
          for (let i = 0; i < reviewerStateByType.length; i++) {
            if (
              reviewerStateByType[i]._account_id === accountID ||
              reviewerStateByType[i].email === accountID
            ) {
              this.splice('change.reviewers.' + type, i, 1);
              break;
            }
          }
        }
        return;
      })
      .catch((err: Error) => {
        this.disabled = false;
        throw err;
      });
  }

  _handleAddTap(e: Event) {
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

  _handleViewAll() {
    this._displayedReviewers = this._reviewers;
  }

  _removeReviewer(id: AccountId | EmailAddress): Promise<Response | undefined> {
    if (!this.change) return Promise.resolve(undefined);
    return this.restApiService.removeChangeReviewer(this.change._number, id);
  }
}

@customElement('gr-reviewer-list')
export class GrReviewerList extends GrLitElement {
  @property({type: Object})
  account?: AccountDetailInfo;

  @property({type: Object})
  change?: ChangeInfo;

  @property({type: Object})
  serverConfig?: ServerInfo;

  @property({type: Boolean})
  mutable = false;

  @property({type: Boolean})
  reviewersOnly = false;

  @property({type: Boolean})
  ccsOnly = false;

  @property({type: Boolean})//, reflectToAttribute: true})
  disabled = false;

  @property({type: Object})
  _xhrPromise?: Promise<Response | undefined>;

  @property({type: Array})
  _reviewers: AccountInfo[] = [];

  @property({type: Array})
  _displayedReviewers: AccountInfo[] = [];

  private readonly restApiService = appContext.restApiService;

  render() {
    const reviewers = this.getReviewers(
      this.change,
      this.account,
      this.serverConfig
    );
    let displayedReviewers = reviewers;
    if (this._reviewers.length > 8) {
      displayedReviewers = reviewers.slice(0, 6);
    }
    return html` ${displayedReviewers.map(
      reviewer => html`<gr-account-chip
        class="reviewer"
        .account=${reviewer}
        .change=${this.change}
        .on-remove=${this._handleRemove}
        .voteable-text=${this._computeVoteableText(reviewer, this.change)}
        .removable=${this.mutable && isRemovableReviewer(this.change, reviewer)}
      ></gr-account-chip>`
    )}`;
  }

  getReviewers(
    change?: ChangeInfo,
    account?: AccountDetailInfo,
    serverConfig?: ServerInfo
  ): AccountInfo[] {
    if (!change || !account || !serverConfig) return [];
    const owner = change?.owner;
    let result: AccountInfo[] = [];
    const reviewers = change.reviewers;
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
    return (
      result
        .filter(reviewer => reviewer._account_id !== owner._account_id)
        // Sort order:
        // 1. The user themselves
        // 2. Human users in the attention set.
        // 3. Other human users.
        // 4. Service users.
        .sort((r1, r2) => {
          if (account) {
            if (isSelf(r1, account)) return -1;
            if (isSelf(r2, account)) return 1;
          }
          const a1 = hasAttention(serverConfig, r1, this.change!) ? 1 : 0;
          const a2 = hasAttention(serverConfig, r2, this.change!) ? 1 : 0;
          const s1 = isServiceUser(r1) ? -2 : 0;
          const s2 = isServiceUser(r2) ? -2 : 0;
          return a2 - a1 + s2 - s1;
        })
    );
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
  _permittedLabelsToNumericScores(labels: LabelNameToValueMap | undefined) {
    if (!labels) return [];
    return Object.keys(labels).map(label => {
      return {
        label,
        scores: labels[label].map(v => Number(v)),
      };
    });
  }

  /**
   * Returns hash of labels to max permitted score.
   *
   * @returns labels to max permitted scores hash
   */
  _getMaxPermittedScores(change: ChangeInfo) {
    return this._permittedLabelsToNumericScores(change.permitted_labels)
      .map(({label, scores}) => {
        return {
          [label]: scores.reduce((a, b) => Math.max(a, b)),
        };
      })
      .reduce((acc, i) => Object.assign(acc, i), {});
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
    const detailedLabel = change.labels[label] as DetailedLabelInfo;
    if (!detailedLabel.all) {
      return NaN;
    }
    const detailed = detailedLabel.all
      .filter(
        (approval: ApprovalInfo) =>
          reviewer._account_id === approval._account_id
      )
      .pop();
    if (!detailed) {
      return NaN;
    }
    if (hasOwnProperty(detailed, 'permitted_voting_range')) {
      if (!detailed.permitted_voting_range) return NaN;
      return detailed.permitted_voting_range.max;
    } else if (hasOwnProperty(detailed, 'value')) {
      // If preset, user can vote on the label.
      return 0;
    }
    return NaN;
  }

  _computeVoteableText(reviewer: AccountInfo, change?: ChangeInfo) {
    if (!change || !change.labels) {
      return '';
    }
    const maxScores = [];
    const maxPermitted = this._getMaxPermittedScores(change);
    for (const label of Object.keys(change.labels)) {
      const maxScore = this._getReviewerPermittedScore(reviewer, change, label);
      if (isNaN(maxScore) || maxScore < 0) {
        continue;
      }
      if (maxScore > 0 && maxScore === maxPermitted[label]) {
        maxScores.push(`${label}: +${maxScore}`);
      } else {
        maxScores.push(`${label}`);
      }
    }
    return maxScores.join(', ');
  }

  _handleRemove(e: Event) {
    e.preventDefault();
    const target = (dom(e) as EventApi).rootTarget as GrAccountChip;
    if (!target.account || !this.change) {
      return;
    }
    const accountID = target.account._account_id || target.account.email;
    this.disabled = true;
    if (!accountID) return;
    this._xhrPromise = this._removeReviewer(accountID)
      .then((response: Response | undefined) => {
        this.disabled = false;
        if (!response || !response.ok) {
          return response;
        }
        if (!this.change || !this.change.reviewers) return;
        const reviewers = this.change.reviewers;
        for (const type of [ReviewerState.REVIEWER, ReviewerState.CC]) {
          const reviewerStateByType = reviewers[type] || [];
          reviewers[type] = reviewerStateByType;
          for (let i = 0; i < reviewerStateByType.length; i++) {
            if (
              reviewerStateByType[i]._account_id === accountID ||
              reviewerStateByType[i].email === accountID
            ) {
              this.splice('change.reviewers.' + type, i, 1);
              break;
            }
          }
        }
        return;
      })
      .catch((err: Error) => {
        this.disabled = false;
        throw err;
      });
  }

  _removeReviewer(id: AccountId | EmailAddress): Promise<Response | undefined> {
    if (!this.change) return Promise.resolve(undefined);
    return this.restApiService.removeChangeReviewer(this.change._number, id);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-reviewer-list2': GrReviewerList2;
    'gr-reviewer-list': GrReviewerList;
  }
}
