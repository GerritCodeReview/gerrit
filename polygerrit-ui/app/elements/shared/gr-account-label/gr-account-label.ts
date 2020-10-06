/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import '../gr-hovercard-account/gr-hovercard-account';
import '../gr-rest-api-interface/gr-rest-api-interface';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-account-label_html';
import {appContext} from '../../../services/app-context';
import {getDisplayName} from '../../../utils/display-name-util';
import {isServiceUser} from '../../../utils/account-util';
import {customElement, property} from '@polymer/decorators';
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {ChangeInfo, AccountInfo, ServerInfo} from '../../../types/common';
import {hasOwnProperty} from '../../../utils/common-util';

export interface GrAccountLabel {
  $: {
    restAPI: RestApiService & Element;
  };
}
@customElement('gr-account-label')
export class GrAccountLabel extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
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
  change!: ChangeInfo;

  @property({type: String})
  voteableText?: string;

  /**
   * Should this user be considered to be in the attention set, regardless
   * of the current state of the change object?
   */
  @property({type: Boolean})
  forceAttention = false;

  /**
   * Only show the first name in the account label.
   */
  @property({type: Boolean})
  firstName = false;

  /**
   * Should attention set related features be shown in the component? Note
   * that the information whether the user is in the attention set or not is
   * part of the ChangeInfo object in the change property.
   */
  @property({type: Boolean})
  highlightAttention = false;

  @property({type: Boolean})
  hideHovercard = false;

  @property({type: Boolean})
  hideAvatar = false;

  @property({
    type: Boolean,
    reflectToAttribute: true,
    computed:
      '_computeCancelLeftPadding(hideAvatar, _config, ' +
      'highlightAttention, account, change, forceAttention)',
  })
  cancelLeftPadding = false;

  @property({type: Boolean})
  hideStatus = false;

  @property({type: Object})
  _config?: ServerInfo;

  reporting: ReportingService;

  constructor() {
    super();
    this.reporting = appContext.reportingService;
  }

  /** @override */
  ready() {
    super.ready();
    this.$.restAPI.getConfig().then(config => {
      this._config = config;
    });
    this.$.restAPI.getAccount().then(account => {
      this._selfAccount = account;
    });
    this.addEventListener('attention-set-updated', () => {
      // For re-evaluation of everything that depends on 'change'.
      this.change = {...this.change};
    });
  }

  _isAttentionSetEnabled(
    config: ServerInfo | undefined,
    highlight: boolean,
    account: AccountInfo,
    change: ChangeInfo
  ) {
    return (
      !!config &&
      !!config.change &&
      !!config.change.enable_attention_set &&
      !!highlight &&
      !!change &&
      !!account &&
      !isServiceUser(account)
    );
  }

  _computeCancelLeftPadding(
    hideAvatar: boolean,
    config: ServerInfo | undefined,
    highlight: boolean,
    account: AccountInfo,
    change: ChangeInfo,
    force: boolean
  ) {
    return (
      !hideAvatar &&
      !this._hasAttention(config, highlight, account, change, force)
    );
  }

  _hasAttention(
    config: ServerInfo | undefined,
    highlight: boolean,
    account: AccountInfo,
    change: ChangeInfo,
    force: boolean
  ) {
    return (
      force || this._hasUnforcedAttention(config, highlight, account, change)
    );
  }

  _hasUnforcedAttention(
    config: ServerInfo | undefined,
    highlight: boolean,
    account: AccountInfo,
    change: ChangeInfo
  ) {
    return (
      this._isAttentionSetEnabled(config, highlight, account, change) &&
      change.attention_set &&
      !!account._account_id &&
      hasOwnProperty(change.attention_set, account._account_id)
    );
  }

  _computeHasAttentionClass(
    config: ServerInfo | undefined,
    highlight: boolean,
    account: AccountInfo,
    change: ChangeInfo,
    force: boolean
  ) {
    return this._hasAttention(config, highlight, account, change, force)
      ? 'hasAttention'
      : '';
  }

  _computeName(
    account?: AccountInfo,
    config?: ServerInfo,
    firstName?: boolean
  ) {
    return getDisplayName(config, account, firstName);
  }

  _handleRemoveAttentionClick(e: MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
    if (!this.account._account_id) return;

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
    const reason = `Removed by ${selfName} by clicking the attention icon`;
    if (this.change.attention_set)
      delete this.change.attention_set[this.account._account_id];
    // For re-evaluation of everything that depends on 'change'.
    this.change = {...this.change};

    this.reporting.reportInteraction(
      'attention-icon-remove',
      this._reportingDetails()
    );
    this.$.restAPI
      .removeFromAttentionSet(
        this.change._number,
        this.account._account_id,
        reason
      )
      .then(() => {
        this.dispatchEvent(
          new CustomEvent('hide-alert', {bubbles: true, composed: true})
        );
      });
  }

  _reportingDetails() {
    const targetId = this.account._account_id;
    const ownerId =
      (this.change && this.change.owner && this.change.owner._account_id) || -1;
    const selfId = this._selfAccount?._account_id || -1;
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

  _computeAttentionIconTitle(
    config: ServerInfo | undefined,
    highlight: boolean,
    account: AccountInfo,
    change: ChangeInfo
  ) {
    return this._hasUnforcedAttention(config, highlight, account, change)
      ? 'Click to remove the user from the attention set'
      : 'Disabled. Use "Modify" to make changes.';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-account-label': GrAccountLabel;
  }
}
