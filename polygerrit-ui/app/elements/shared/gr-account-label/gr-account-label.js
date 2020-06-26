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
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-account-label_html.js';
import {DisplayNameBehavior} from '../../../behaviors/gr-display-name-behavior/gr-display-name-behavior.js';

/**
 * @extends PolymerElement
 */
class GrAccountLabel extends mixinBehaviors( [
  DisplayNameBehavior,
], GestureEventListeners(
    LegacyElementMixin(
        PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-account-label'; }

  static get properties() {
    return {
      /**
       * @type {{ name: string, status: string }}
       */
      account: Object,
      /**
       * Optional ChangeInfo object, typically comes from the change page or
       * from a row in a list of search results. This is needed for some change
       * related features like adding the user as a reviewer.
       */
      change: Object,
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
      blurred: {
        type: Boolean,
        value: false,
        reflectToAttribute: true,
      },
      hideHovercard: {
        type: Boolean,
        value: false,
      },
      hideAvatar: {
        type: Boolean,
        value: false,
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

  /** @override */
  ready() {
    super.ready();
    this.$.restAPI.getConfig().then(config => { this._config = config; });
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

  _computeShowAttentionIcon(config, highlightAttention, account, change) {
    return this.isAttentionSetEnabled && this.hasAttention;
  }

  _computeName(account, config) {
    return this.getDisplayName(config, account);
  }
}

customElements.define(GrAccountLabel.is, GrAccountLabel);
