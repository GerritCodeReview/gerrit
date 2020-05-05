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
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-hovercard-account_html.js';

const isAttentionSetEnabled = function(config, showAttention, account, change) {
  return config && config.change && config.change.enable_attention_set
      && showAttention && change && account && change.attention_set;
};

const hasAttention = function(account, change) {
  return change.attention_set.hasOwnProperty(account._account_id);
};

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
      showAttention: {
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

  /** @override */
  ready() {
    super.ready();
    this.$.restAPI.getConfig().then(config => { this._config = config; });
  }

  _computeShowLabelNeedsAttention(config, showAttention, account, change) {
    const enabled = isAttentionSetEnabled(config, showAttention, account,
        change);
    return enabled && hasAttention(account, change);
  }

  _computeShowActionAddToAttentionSet(config, showAttention, account, change) {
    const enabled = isAttentionSetEnabled(config, showAttention, account,
        change);
    return enabled && !hasAttention(account, change);
  }

  _computeShowActionRemoveFromAttentionSet(config, showAttention, account,
      change) {
    const enabled = isAttentionSetEnabled(config, showAttention, account,
        change);
    return enabled && hasAttention(account, change);
  }

  _handleClickAddToAttentionSet(e) {
    // TODO(brohlfs): Add a user notification while and after saving.
    console.warn('_handleClickAddToAttentionSet');
    this.$.restAPI.addToAttentionSet(265874, this.account._account_id,
        'manually added');
    this.hide();
  }

  _handleClickRemoveFromAttentionSet(e) {
    // TODO(brohlfs): Add a user notification while and after saving.
    console.warn('_handleClickRemoveFromAttentionSet');
    this.$.restAPI.removeFromAttentionSet(265874, this.account._account_id,
        'manually removed');
    this.hide();
  }
}

customElements.define(GrHovercardAccount.is, GrHovercardAccount);
