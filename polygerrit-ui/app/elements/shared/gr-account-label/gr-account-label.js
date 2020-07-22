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
import {getDisplayName} from '../../../utils/display-name-util.js';

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
      /**
       * Optional ChangeInfo object, typically comes from the change page or
       * from a row in a list of search results. This is needed for some change
       * related features like adding the user as a reviewer.
       */
      change: Object,
      voteableText: String,
      /**
       * Should this user be considered to be in the attention set, regardless
       * of the current state of the change object? This can be used in a widget
       * that allows the user to make adjustments to the attention set.
       */
      forceAttention: {
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

  _isAttentionSetEnabled(config, highlight, account, change) {
    return !!config && !!config.change
        && !!config.change.enable_attention_set
        && !!highlight && !!change && !!account;
  }

  _hasAttention(config, highlight, account, change, force) {
    if (force) return true;
    return this._isAttentionSetEnabled(config, highlight, account, change)
        && change.attention_set
        && change.attention_set.hasOwnProperty(account._account_id);
  }

  _computeName(account, config) {
    return getDisplayName(config, account);
  }
}

customElements.define(GrAccountLabel.is, GrAccountLabel);
