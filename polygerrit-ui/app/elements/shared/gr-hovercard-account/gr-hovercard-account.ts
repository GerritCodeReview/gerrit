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
import '../../../styles/gr-font-styles';
import '../../../styles/shared-styles';
import '../../../styles/gr-hovercard-styles';
import '../gr-avatar/gr-avatar';
import '../gr-button/gr-button';
import {HovercardBehaviorMixin} from '../gr-hovercard/gr-hovercard-behavior';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-hovercard-account_html';
import {appContext} from '../../../services/app-context';
import {accountKey, isSelf} from '../../../utils/account-util';
import {customElement, property} from '@polymer/decorators';
import {
  AccountInfo,
  ChangeInfo,
  ServerInfo,
  ReviewInput,
} from '../../../types/common';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {
  canHaveAttention,
  getAddedByReason,
  getLastUpdate,
  getReason,
  getRemovedByReason,
  hasAttention,
} from '../../../utils/attention-set-util';
import {ReviewerState} from '../../../constants/constants';
import {CURRENT} from '../../../utils/patch-set-util';
import {isInvolved, isRemovableReviewer} from '../../../utils/change-util';
import {assertIsDefined} from '../../../utils/common-util';

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = HovercardBehaviorMixin(PolymerElement);

@customElement('gr-hovercard-account')
export class GrHovercardAccount extends base {
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

  private readonly restApiService = appContext.restApiService;

  constructor() {
    super();
    this.reporting = appContext.reportingService;
  }

  override connectedCallback() {
    super.connectedCallback();
    this.restApiService.getConfig().then(config => {
      this._config = config;
    });
    this.restApiService.getAccount().then(account => {
      this._selfAccount = account;
    });
  }

  _computeText(account?: AccountInfo, selfAccount?: AccountInfo) {
    if (!account || !selfAccount) return '';
    return isSelf(account, selfAccount) ? 'Your' : 'Their';
  }

  get isAttentionEnabled() {
    return (
      !!this.highlightAttention &&
      !!this.change &&
      canHaveAttention(this.account)
    );
  }

  get hasUserAttention() {
    return hasAttention(this.account, this.change);
  }

  _computeReason(change?: ChangeInfo) {
    return getReason(this._config, this.account, change);
  }

  _computeLastUpdate(change?: ChangeInfo) {
    return getLastUpdate(this.account, change);
  }

  /** 3rd parameter is just for *triggering* re-computation. */
  _showReviewerOrCCActions(
    account?: AccountInfo,
    change?: ChangeInfo,
    _?: unknown
  ) {
    return !!this._selfAccount && isRemovableReviewer(change, account);
  }

  _getReviewerState(account: AccountInfo, change: ChangeInfo) {
    if (
      change.reviewers[ReviewerState.REVIEWER]?.some(
        (reviewer: AccountInfo) => reviewer._account_id === account._account_id
      )
    ) {
      return ReviewerState.REVIEWER;
    }
    return ReviewerState.CC;
  }

  _computeReviewerOrCCText(account?: AccountInfo, change?: ChangeInfo) {
    if (!change || !account) return '';
    return this._getReviewerState(account, change) === ReviewerState.REVIEWER
      ? 'Reviewer'
      : 'CC';
  }

  _computeChangeReviewerOrCCText(account?: AccountInfo, change?: ChangeInfo) {
    if (!change || !account) return '';
    return this._getReviewerState(account, change) === ReviewerState.REVIEWER
      ? 'Move Reviewer to CC'
      : 'Move CC to Reviewer';
  }

  _handleChangeReviewerOrCCStatus() {
    assertIsDefined(this.change, 'change');
    // accountKey() throws an error if _account_id & email is not found, which
    // we want to check before showing reloading toast
    const _accountKey = accountKey(this.account);
    this.dispatchEventThroughTarget('show-alert', {
      message: 'Reloading page...',
    });
    const reviewInput: Partial<ReviewInput> = {};
    reviewInput.reviewers = [
      {
        reviewer: _accountKey,
        state:
          this._getReviewerState(this.account, this.change) === ReviewerState.CC
            ? ReviewerState.REVIEWER
            : ReviewerState.CC,
      },
    ];

    this.restApiService
      .saveChangeReview(this.change._number, CURRENT, reviewInput)
      .then(response => {
        if (!response || !response.ok) {
          throw new Error(
            'something went wrong when toggling' +
              this._getReviewerState(this.account, this.change!)
          );
        }
        this.dispatchEventThroughTarget('reload', {clearPatchset: true});
      });
  }

  _handleRemoveReviewerOrCC() {
    if (!this.change || !(this.account?._account_id || this.account?.email))
      throw new Error('Missing change or account.');
    this.dispatchEventThroughTarget('show-alert', {
      message: 'Reloading page...',
    });
    this.restApiService
      .removeChangeReviewer(
        this.change._number,
        (this.account?._account_id || this.account?.email)!
      )
      .then((response: Response | undefined) => {
        if (!response || !response.ok) {
          throw new Error('something went wrong when removing user');
        }
        this.dispatchEventThroughTarget('reload', {clearPatchset: true});
        return response;
      });
  }

  /** Parameters are just for *triggering* re-computation. */
  _computeShowLabelNeedsAttention(
    _1: unknown,
    _2: unknown,
    _3: unknown,
    _4: unknown
  ) {
    return this.isAttentionEnabled && this.hasUserAttention;
  }

  /** Parameters are just for *triggering* re-computation. */
  _computeShowActionAddToAttentionSet(
    _1: unknown,
    _2: unknown,
    _3: unknown,
    _4: unknown,
    _5: unknown
  ) {
    const involvedOrSelf =
      isInvolved(this.change, this._selfAccount) ||
      isSelf(this.account, this._selfAccount);
    return involvedOrSelf && this.isAttentionEnabled && !this.hasUserAttention;
  }

  /** Parameters are just for *triggering* re-computation. */
  _computeShowActionRemoveFromAttentionSet(
    _1: unknown,
    _2: unknown,
    _3: unknown,
    _4: unknown,
    _5: unknown
  ) {
    const involvedOrSelf =
      isInvolved(this.change, this._selfAccount) ||
      isSelf(this.account, this._selfAccount);
    return involvedOrSelf && this.isAttentionEnabled && this.hasUserAttention;
  }

  _handleClickAddToAttentionSet() {
    if (!this.change || !this.account._account_id) return;
    this.dispatchEventThroughTarget('show-alert', {
      message: 'Saving attention set update ...',
      dismissOnNavigation: true,
    });

    // We are deliberately updating the UI before making the API call. It is a
    // risk that we are taking to achieve a better UX for 99.9% of the cases.
    const reason = getAddedByReason(this._selfAccount, this._config);

    if (!this.change.attention_set) this.change.attention_set = {};
    this.change.attention_set[this.account._account_id] = {
      account: this.account,
      reason,
      reason_account: this._selfAccount,
    };
    this.dispatchEventThroughTarget('attention-set-updated');

    this.reporting.reportInteraction(
      'attention-hovercard-add',
      this._reportingDetails()
    );
    this.restApiService
      .addToAttentionSet(this.change._number, this.account._account_id, reason)
      .then(() => {
        this.dispatchEventThroughTarget('hide-alert');
      });
    this.hide();
  }

  _handleClickRemoveFromAttentionSet() {
    if (!this.change || !this.account._account_id) return;
    this.dispatchEventThroughTarget('show-alert', {
      message: 'Saving attention set update ...',
      dismissOnNavigation: true,
    });

    // We are deliberately updating the UI before making the API call. It is a
    // risk that we are taking to achieve a better UX for 99.9% of the cases.

    const reason = getRemovedByReason(this._selfAccount, this._config);
    if (this.change.attention_set)
      delete this.change.attention_set[this.account._account_id];
    this.dispatchEventThroughTarget('attention-set-updated');

    this.reporting.reportInteraction(
      'attention-hovercard-remove',
      this._reportingDetails()
    );
    this.restApiService
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
