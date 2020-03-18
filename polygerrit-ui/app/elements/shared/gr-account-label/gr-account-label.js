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
import '../../../scripts/bundled-polymer.js';

import '@polymer/iron-icon/iron-icon.js';
import '../../../behaviors/gr-display-name-behavior/gr-display-name-behavior.js';
import '../../../behaviors/gr-tooltip-behavior/gr-tooltip-behavior.js';
import '../../../styles/shared-styles.js';
import '../gr-avatar/gr-avatar.js';
import '../gr-hovercard-account/gr-hovercard-account.js';
import '../gr-rest-api-interface/gr-rest-api-interface.js';
import '../../../scripts/util.js';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-account-label_html.js';

/**
 * @appliesMixin Gerrit.DisplayNameMixin
 * @appliesMixin Gerrit.TooltipMixin
 * @extends Polymer.Element
 */
class GrAccountLabel extends mixinBehaviors( [
  Gerrit.DisplayNameBehavior,
  Gerrit.TooltipBehavior,
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
      title: {
        type: String,
        reflectToAttribute: true,
        computed: '_computeAccountTitle(account, additionalText)',
      },
      voteableText: String,
      hasTooltip: {
        type: Boolean,
        reflectToAttribute: true,
        computed: '_computeHasTooltip(account)',
      },
      hideAvatar: {
        type: Boolean,
        value: false,
      },
      _serverConfig: {
        type: Object,
        value: null,
      },
    };
  }

  /** @override */
  ready() {
    super.ready();
    if (!this.additionalText) { this.additionalText = ''; }
    this.$.restAPI.getConfig()
        .then(config => { this._serverConfig = config; });
  }

  _computeName(account, config) {
    return this.getUserName(config, account, /* enableEmail */ true);
  }

  _computeAccountTitle(account, tooltip) {
    // Polymer 2: check for undefined
    if ([
      account,
      tooltip,
    ].some(arg => arg === undefined)) {
      return undefined;
    }

    if (!account) { return; }
    let result = '';
    if (this._computeName(account, this._serverConfig)) {
      result += this._computeName(account, this._serverConfig);
    }
    if (account.email) {
      result += ` <${account.email}>`;
    }
    if (this.additionalText) {
      result += ` ${this.additionalText}`;
    }

    // Show status in the label tooltip instead of
    // in a separate tooltip on status
    if (account.status) {
      result += ` (${account.status})`;
    }

    return result;
  }

  _computeHasTooltip(account) {
    // If an account has loaded to fire this method, then set to true.
    return false;
  }
}

customElements.define(GrAccountLabel.is, GrAccountLabel);
