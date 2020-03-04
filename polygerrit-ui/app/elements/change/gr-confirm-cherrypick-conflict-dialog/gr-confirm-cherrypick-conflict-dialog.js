/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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

import '../../../behaviors/fire-behavior/fire-behavior.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-dialog/gr-dialog.js';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-confirm-cherrypick-conflict-dialog_html.js';

/**
 * @appliesMixin Gerrit.FireMixin
 * @extends Polymer.Element
 */
class GrConfirmCherrypickConflictDialog extends mixinBehaviors( [
  Gerrit.FireBehavior,
], GestureEventListeners(
    LegacyElementMixin(
        PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-confirm-cherrypick-conflict-dialog'; }

  /**
   * Fired when the confirm button is pressed.
   *
   * @event confirm
   */

  /**
   * Fired when the cancel button is pressed.
   *
   * @event cancel
   */

  _handleConfirmTap(e) {
    e.preventDefault();
    e.stopPropagation();
    this.fire('confirm', null, {bubbles: false});
  }

  _handleCancelTap(e) {
    e.preventDefault();
    e.stopPropagation();
    this.fire('cancel', null, {bubbles: false});
  }
}

customElements.define(GrConfirmCherrypickConflictDialog.is,
    GrConfirmCherrypickConflictDialog);
