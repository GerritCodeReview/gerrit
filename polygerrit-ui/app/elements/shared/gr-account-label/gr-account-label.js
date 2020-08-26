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
import '@polymer/iron-icon/iron-icon.js';
import '../../../styles/shared-styles.js';
import '../gr-avatar/gr-avatar.js';
import '../gr-hovercard-account/gr-hovercard-account.js';
import '../gr-rest-api-interface/gr-rest-api-interface.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-account-label_html.js';
import {appContext} from '../../../services/app-context.js';
import {getDisplayName} from '../../../utils/display-name-util.js';
import {isServiceUser} from '../../../utils/account-util.js';

/**
 * @extends PolymerElement
 */
class GrAccountLabel extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-account-label'; }

  static get properties() {
    return {
      /**
       * @type {{ name: string, status: string }}
       */
      account: Object,
      _selfAccount: Object,
      /**
       * Optional ChangeInfo object, typically comes from the change page or
       * from a row in a list of search results. This is needed for some change
       * related features like adding the user as a reviewer.
       */
      change: Object,
      voteableText: String,
      /**
       * Should this user be considered to be in the attention set, regardless
       * of the current state of the change object?
       */
      forceAttention: {
        type: Boolean,
        value: false,
      },
      /**
       * Only show the first name in the account label.
       */
      firstName: {
        type: Boolean,
        value: false,
      },
      /**
       * Should attention set related features be shown in the component? Note
       * that the information whether the user is in the attention set or not is
       * part of the ChangeInfo object in the change property.
       */
      highlightAttention: {
        type: Boolean,
        value: false,
      },
      hideHovercard: {
        type: Boolean,
        value: false,
      },
      hideAvatar: {
        type: Boolean,
        value: false,
      },
      cancelLeftPadding: {
        type: Boolean,
        value: false,
        reflectToAttribute: true,
        computed: '_computeCancelLeftPadding(hideAvatar, _config, ' +
            'highlightAttention, account, change, forceAttention)',
      },
      hideStatus: {
        type: Boolean,
        value: false,
      },
      /**
       * This is a ServerInfo response object.
       */
      _config: {
        type: Object,
        value: null,
      },
    };
  }

  constructor() {
    super();
    this.reporting = appContext.reportingService;
  }

  /** @override */
  ready() {
    super.ready();
    this.$.restAPI.getConfig().then(config => { this._config = config; });
    this.$.restAPI.getAccount().then(account => {
      this._selfAccount = account;
    });
    this.addEventListener('attention-set-updated', e => {
      // For re-evaluation of everything that depends on 'change'.
      this.change = {...this.change};
    });
  }

  _isAttentionSetEnabled(config, highlight, account, change) {
    return !!config && !!config.change
        && !!config.change.enable_attention_set
        && !!highlight && !!change && !!account
        && !isServiceUser(account);
  }

  _computeCancelLeftPadding(
      hideAvatar, config, highlight, account, change, force) {
    return !hideAvatar &&
        !this._hasAttention(config, highlight, account, change, force);
  }

  _hasAttention(config, highlight, account, change, force) {
    if (force) return true;
    return this._isAttentionSetEnabled(config, highlight, account, change)
        && change.attention_set
        && change.attention_set.hasOwnProperty(account._account_id);
  }

  _computeName(account, config, firstName) {
    return getDisplayName(config, account, firstName);
  }

  _handleRemoveAttentionClick(e) {
    e.preventDefault();
    e.stopPropagation();

    this.dispatchEvent(new CustomEvent('show-alert', {
      detail: {
        message: 'Saving attention set update ...',
        dismissOnNavigation: true,
      },
      composed: true,
      bubbles: true,
    }));

    // We are deliberately updating the UI before making the API call. It is a
    // risk that we are taking to achieve a better UX for 99.9% of the cases.
    const selfName = getDisplayName(this._config, this._selfAccount);
    const reason = `Removed by ${selfName} by clicking the attention icon`;
    delete this.change.attention_set[this.account._account_id];
    // For re-evaluation of everything that depends on 'change'.
    this.change = {...this.change};

    this.reporting.reportInteraction('attention-icon-remove',
        this._reportingDetails());
    this.$.restAPI.removeFromAttentionSet(this.change._number,
        this.account._account_id, reason).then(obj => {
      this.dispatchEvent(
          new CustomEvent('hide-alert', {bubbles: true, composed: true})
      );
    });
  }

  _reportingDetails() {
    const targetId = this.account._account_id;
    const ownerId = (this.change && this.change.owner
        && this.change.owner._account_id) || -1;
    const selfId = (this._selfAccount && this._selfAccount._account_id) || -1;
    const reviewers = (
      this.change && this.change.reviewers && this.change.reviewers.REVIEWER ?
        [...this.change.reviewers.REVIEWER] : []);
    const reviewerIds = reviewers
        .map(r => r._account_id)
        .filter(rId => rId !== ownerId);
    return {
      actionByOwner: selfId === ownerId,
      actionByReviewer: reviewerIds.includes(selfId),
      targetIsOwner: targetId === ownerId,
      targetIsReviewer: reviewerIds.includes(targetId),
      targetIsSelf: targetId === selfId,
    };
  }
}

customElements.define(GrAccountLabel.is, GrAccountLabel);
