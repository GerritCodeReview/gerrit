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
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import '../../../styles/shared-styles';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-reviewer-list_html';
import {isServiceUser} from '../../../utils/account-util';
import {hasAttention} from '../../../utils/attention-set-util';
import {customElement, property, computed, observe} from '@polymer/decorators';
import {
  ChangeInfo,
  ServerInfo,
  LabelNameToValueMap,
  AccountInfo,
  ApprovalInfo,
  Reviewers,
  AccountId,
  DetailedLabelInfo,
} from '../../../types/common';
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import {GrAccountChip} from '../../shared/gr-account-chip/gr-account-chip';
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';
import {hasOwnProperty} from '../../../utils/common-util';
import {isRemovableReviewer} from '../../../utils/change-util';

export interface GrReviewerList {
  $: {
    restAPI: RestApiService & Element;
  };
}

@customElement('gr-reviewer-list')
export class GrReviewerList extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the "Add reviewer..." button is tapped.
   *
   * @event show-reply-dialog
   */

  @property({type: Object})
  change?: ChangeInfo;

  @property({type: Object})
  serverConfig?: ServerInfo;

  @property({type: Boolean, reflectToAttribute: true})
  disabled = false;

  @property({type: Boolean})
  mutable = false;

  @property({type: Boolean})
  reviewersOnly = false;

  @property({type: Boolean})
  ccsOnly = false;

  @property({type: Array})
  _displayedReviewers: AccountInfo[] = [];

  @property({type: Array})
  _reviewers: AccountInfo[] = [];

  @property({type: Boolean})
  _showInput = false;

  @property({type: Object})
  _xhrPromise?: Promise<Response | undefined>;

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
        scores: labels[label].map(v => parseInt(v, 10)),
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
    for (const key in reviewers) {
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
      // 1. Human users in the attention set.
      // 2. Other human users.
      // 3. Service users.
      .sort((r1, r2) => {
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
    const accountID = target.account._account_id;
    this.disabled = true;
    if (!accountID) return;
    this._xhrPromise = this._removeReviewer(accountID)
      .then((response: Response | undefined) => {
        this.disabled = false;
        if (!response || !response.ok) {
          return response;
        }
        if (!this.change || !this.change.reviewers) return;
        const reviewers: {[type: string]: AccountInfo[] | undefined} = this
          .change!.reviewers;
        for (const type of ['REVIEWER', 'CC']) {
          reviewers[type] = reviewers[type] || [];
          for (let i = 0; i < reviewers[type]!.length; i++) {
            if (reviewers[type]![i]._account_id === accountID) {
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

  _removeReviewer(id: AccountId): Promise<Response | undefined> {
    if (!this.change) return Promise.resolve(undefined);
    return this.$.restAPI.removeChangeReviewer(this.change._number, id);
  }
}
