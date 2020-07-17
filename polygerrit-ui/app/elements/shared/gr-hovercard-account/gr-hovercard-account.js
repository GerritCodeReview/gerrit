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

import '@polymer/iron-icon/iron-icon.js';
import '../../../styles/shared-styles.js';
import '../gr-avatar/gr-avatar.js';
import '../gr-button/gr-button.js';
import '../gr-rest-api-interface/gr-rest-api-interface.js';
import {hovercardBehaviorMixin} from '../gr-hovercard/gr-hovercard-behavior.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-hovercard-account_html.js';
import {appContext} from '../../../services/app-context.js';

/** @extends PolymerElement */
class GrHovercardAccount extends GestureEventListeners(
    hovercardBehaviorMixin(LegacyElementMixin(
        PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-hovercard-account'; }

  static get properties() {
    return {
      /**
       * This is an AccountInfo response object.
       */
      account: Object,
      _selfAccount: Object,
      /**
       * Optional ChangeInfo object, typically comes from the change page or
       * from a row in a list of search results. This is needed for some change
       * related features like adding the user as a reviewer.
       */
      change: Object,
      /**
       * Explains which labels the user can vote on and which score they can
       * give.
       */
      voteableText: String,
      /**
       * Should attention set related features be shown in the component? Note
       * that the information whether the user is in the attention set or not is
       * part of the ChangeInfo object in the change property.
       */
      highlightAttention: {
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

  attached() {
    super.attached();
    this.$.restAPI.getConfig().then(config => {
      this._config = config;
    });
    this.$.restAPI.getAccount().then(account => {
      this._selfAccount = account;
    });
  }

  _computeText(account, selfAccount) {
    if (!account || !selfAccount) return '';
    return account._account_id === selfAccount._account_id ? 'Your' : 'Their';
  }

  get isAttentionSetEnabled() {
    return !!this._config && !!this._config.change
        && !!this._config.change.enable_attention_set
        && !!this.highlightAttention && !!this.change && !!this.account;
  }

  get hasAttention() {
    if (!this.isAttentionSetEnabled || !this.change.attention_set) return false;
    return this.change.attention_set.hasOwnProperty(this.account._account_id);
  }

  _computeShowLabelNeedsAttention(config, highlightAttention, account, change) {
    return this.isAttentionSetEnabled && this.hasAttention;
  }

  _computeShowActionAddToAttentionSet(config, highlightAttn, account, change) {
    return this.isAttentionSetEnabled && !this.hasAttention;
  }

  _computeShowActionRemoveFromAttentionSet(config, highlightAttention, account,
      change) {
    return this.isAttentionSetEnabled && this.hasAttention;
  }

  _handleClickAddToAttentionSet(e) {
    this.dispatchEvent(new CustomEvent('show-alert', {
      detail: {
        message: 'Adding user to attention set. Will be reloading ...',
        dismissOnNavigation: true,
      },
      composed: true,
      bubbles: true,
    }));
    this.reporting.reportInteraction('attention-hovercard-add',
        this._reportingDetails());
    this.$.restAPI.addToAttentionSet(this.change._number,
        this.account._account_id, 'manually added').then(obj => {
      this.dispatchEventThroughTarget('hide-alert');
      this.dispatchEventThroughTarget('reload');
    });
    this.hide();
  }

  _handleClickRemoveFromAttentionSet(e) {
    this.dispatchEvent(new CustomEvent('show-alert', {
      detail: {
        message: 'Removing user from attention set. Will be reloading ...',
        dismissOnNavigation: true,
      },
      composed: true,
      bubbles: true,
    }));
    this.reporting.reportInteraction('attention-hovercard-remove',
        this._reportingDetails());
    this.$.restAPI.removeFromAttentionSet(this.change._number,
        this.account._account_id, 'manually removed').then(obj => {
      this.dispatchEventThroughTarget('hide-alert');
      this.dispatchEventThroughTarget('reload');
    });
    this.hide();
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

customElements.define(GrHovercardAccount.is, GrHovercardAccount);
