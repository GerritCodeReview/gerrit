/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

import '@polymer/iron-icon/iron-icon';
import '../../../styles/shared-styles';
import '../gr-avatar/gr-avatar';
import '../gr-button/gr-button';
import '../gr-rest-api-interface/gr-rest-api-interface';
import {hovercardBehaviorMixin} from '../gr-hovercard/gr-hovercard-behavior';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-hovercard-account_html';
import {appContext} from '../../../services/app-context';
import {accountKey} from '../../../utils/account-util';
import {getDisplayName} from '../../../utils/display-name-util';
import {customElement, property} from '@polymer/decorators';
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';
import {
  AccountInfo,
  ChangeInfo,
  ServerInfo,
  ReviewInput,
} from '../../../types/common';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {
  canHaveAttention,
  getLastUpdate,
  getReason,
  hasAttention,
  isAttentionSetEnabled,
} from '../../../utils/attention-set-util';
import {ReviewerState} from '../../../constants/constants';
import {
  computeLatestPatchNum,
  computeAllPatchSets,
} from '../../../utils/patch-set-util';
import {canRemoveReviewer} from '../../../utils/change-util';

export interface GrHovercardAccount {
  $: {
    restAPI: RestApiService & Element;
  };
}
@customElement('gr-hovercard-account')
export class GrHovercardAccount extends GestureEventListeners(
  hovercardBehaviorMixin(LegacyElementMixin(PolymerElement))
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object})
  account!: AccountInfo;

  @property({type: Object})
  _selfAccount?: AccountInfo;

  /**
   * Optional ChangeInfo object, typically comes from the change page or
   * from a row in a list of search results. This is needed for some change
   * related features like adding the user as a reviewer.
   */
  @property({type: Object})
  change?: ChangeInfo;

  /**
   * Explains which labels the user can vote on and which score they can
   * give.
   */
  @property({type: String})
  voteableText?: string;

  /**
   * Should attention set related features be shown in the component? Note
   * that the information whether the user is in the attention set or not is
   * part of the ChangeInfo object in the change property.
   */
  @property({type: Boolean})
  highlightAttention = false;

  @property({type: Object})
  _config?: ServerInfo;

  reporting: ReportingService;

  constructor() {
    super();
    this.reporting = appContext.reportingService;
  }

  attached() {
    super.attached();
    this.$.restAPI.getConfig().then(config => {
      this._config = config;
    });
    this.$.restAPI.getAccount().then(account => {
      this._selfAccount = account;
    });
  }

  _computeText(account?: AccountInfo, selfAccount?: AccountInfo) {
    if (!account || !selfAccount) return '';
    return account._account_id === selfAccount._account_id ? 'Your' : 'Their';
  }

  get isAttentionEnabled() {
    return (
      isAttentionSetEnabled(this._config) &&
      !!this.highlightAttention &&
      !!this.change &&
      canHaveAttention(this.account)
    );
  }

  get hasUserAttention() {
    return hasAttention(this._config, this.account, this.change);
  }

  _computeReason(change?: ChangeInfo) {
    return getReason(this.account, change);
  }

  _computeLastUpdate(change?: ChangeInfo) {
    return getLastUpdate(this.account, change);
  }

  _showReviewerOrCCActions(account: AccountInfo, change: ChangeInfo) {
    return canRemoveReviewer(!!Object.keys(account).length, change, account);
  }

  _getReviewerState(account: AccountInfo, change: ChangeInfo) {
    if (
      change.reviewers[ReviewerState.REVIEWER]?.some(
        (reviewer: AccountInfo) => {
          return reviewer._account_id === account._account_id;
        }
      )
    ) {
      return ReviewerState.REVIEWER;
    }
    return ReviewerState.CC;
  }

  _computeReviewerOrCCText(account: AccountInfo, change: ChangeInfo) {
    if (!change) return '';
    return this._getReviewerState(account, change) === ReviewerState.REVIEWER
      ? 'Reviewer'
      : 'CC';
  }

  _computeChangeReviewerOrCCText(account: AccountInfo, change: ChangeInfo) {
    if (!change) return '';
    return this._getReviewerState(account, change) === ReviewerState.REVIEWER
      ? 'Move Reviewer to CC'
      : 'Move CC to Reviewer';
  }

  _handleChangeReviewerOrCCStatus() {
    if (!this.change) {
      throw new Error('expected change object to be present');
    }
    if (!this.account?._account_id) {
      throw new Error('no account id found');
    }
    const reviewInput: Partial<ReviewInput> = {};
    reviewInput.reviewers = [
      {
        reviewer: accountKey(this.account),
        state: this._getReviewerState(this.account, this.change),
      },
    ];

    this.$.restAPI
      .saveChangeReview(
        this.change!._number,
        computeLatestPatchNum(computeAllPatchSets(this.change!))!,
        reviewInput
      )
      .then(response => {
        if (!response || !response.ok) {
          throw new Error('something went wrong when removing user');
        }
        this.dispatchEventThroughTarget('reload', {clearPatchset: true});
      });
  }

  _handleRemoveReviewerOrCC() {
    if (!this.change || !this.account?._account_id) return;
    this.$.restAPI
      .removeChangeReviewer(this.change._number, this.account._account_id)
      .then((response: Response | undefined) => {
        if (!response || !response.ok) {
          throw new Error('something went wrong when removing user');
        }
        this.dispatchEventThroughTarget('reload', {clearPatchset: true});
        return response;
      })
      .catch((err: Error) => {
        console.error(err);
        throw err;
      });
  }

  _computeShowLabelNeedsAttention() {
    return this.isAttentionEnabled && this.hasUserAttention;
  }

  _computeShowActionAddToAttentionSet() {
    return (
      this._selfAccount && this.isAttentionEnabled && !this.hasUserAttention
    );
  }

  _computeShowActionRemoveFromAttentionSet() {
    return (
      this._selfAccount && this.isAttentionEnabled && this.hasUserAttention
    );
  }

  _handleClickAddToAttentionSet() {
    if (!this.change || !this.account._account_id) return;
    this.dispatchEvent(
      new CustomEvent('show-alert', {
        detail: {
          message: 'Saving attention set update ...',
          dismissOnNavigation: true,
        },
        composed: true,
        bubbles: true,
      })
    );

    // We are deliberately updating the UI before making the API call. It is a
    // risk that we are taking to achieve a better UX for 99.9% of the cases.
    const selfName = getDisplayName(this._config, this._selfAccount);
    const reason = `Added by ${selfName} using the hovercard menu`;
    if (!this.change.attention_set) this.change.attention_set = {};
    this.change.attention_set[this.account._account_id] = {
      account: this.account,
      reason,
    };
    this.dispatchEventThroughTarget('attention-set-updated');

    this.reporting.reportInteraction(
      'attention-hovercard-add',
      this._reportingDetails()
    );
    this.$.restAPI
      .addToAttentionSet(this.change._number, this.account._account_id, reason)
      .then(() => {
        this.dispatchEventThroughTarget('hide-alert');
      });
    this.hide();
  }

  _handleClickRemoveFromAttentionSet() {
    if (!this.change || !this.account._account_id) return;
    this.dispatchEvent(
      new CustomEvent('show-alert', {
        detail: {
          message: 'Saving attention set update ...',
          dismissOnNavigation: true,
        },
        composed: true,
        bubbles: true,
      })
    );

    // We are deliberately updating the UI before making the API call. It is a
    // risk that we are taking to achieve a better UX for 99.9% of the cases.
    const selfName = getDisplayName(this._config, this._selfAccount);
    const reason = `Removed by ${selfName} using the hovercard menu`;
    if (this.change.attention_set)
      delete this.change.attention_set[this.account._account_id];
    this.dispatchEventThroughTarget('attention-set-updated');

    this.reporting.reportInteraction(
      'attention-hovercard-remove',
      this._reportingDetails()
    );
    this.$.restAPI
      .removeFromAttentionSet(
        this.change._number,
        this.account._account_id,
        reason
      )
      .then(() => {
        this.dispatchEventThroughTarget('hide-alert');
      });
    this.hide();
  }

  _reportingDetails() {
    const targetId = this.account._account_id;
    const ownerId =
      (this.change && this.change.owner && this.change.owner._account_id) || -1;
    const selfId = (this._selfAccount && this._selfAccount._account_id) || -1;
    const reviewers =
      this.change && this.change.reviewers && this.change.reviewers.REVIEWER
        ? [...this.change.reviewers.REVIEWER]
        : [];
    const reviewerIds = reviewers
      .map(r => r._account_id)
      .filter(rId => rId !== ownerId);
    return {
      actionByOwner: selfId === ownerId,
      actionByReviewer: selfId !== -1 && reviewerIds.includes(selfId),
      targetIsOwner: targetId === ownerId,
      targetIsReviewer: reviewerIds.includes(targetId),
      targetIsSelf: targetId === selfId,
    };
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-hovercard-account': GrHovercardAccount;
  }
}
