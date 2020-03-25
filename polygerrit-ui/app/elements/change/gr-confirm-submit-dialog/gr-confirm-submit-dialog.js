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

import '@polymer/iron-icon/iron-icon.js';
import '../../shared/gr-icons/gr-icons.js';
import '../../core/gr-navigation/gr-navigation.js';
import '../../shared/gr-dialog/gr-dialog.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator.js';
import '../../plugins/gr-endpoint-param/gr-endpoint-param.js';
import '../../../styles/shared-styles.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-confirm-submit-dialog_html.js';

/** @extends Polymer.Element */
class GrConfirmSubmitDialog extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-confirm-submit-dialog'; }
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

  static get properties() {
    return {
    /**
     * @type {{
     *    is_private: boolean,
     *    subject: string,
     *    revisions: Object
     *  }}
     */
      change: Object,

      /**
       * @type {{
       *    label: string,
       *  }}
       */
      action: Object,
    };
  }

  resetFocus(e) {
    this.$.dialog.resetFocus();
  }

  _computeHasChangeEdit(change) {
    return Object.values(change.revisions).some(rev => rev._number == 'edit');
  }

  _computeUnresolvedCommentsWarning(change) {
    const unresolvedCount = change.unresolved_comment_count;
    const plural = unresolvedCount > 1 ? 's' : '';
    return `Heads Up! ${unresolvedCount} unresolved comment${plural}.`;
  }

  _handleConfirmTap(e) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('confirm', {bubbles: false}));
  }

  _handleCancelTap(e) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('cancel', {bubbles: false}));
  }
}

customElements.define(GrConfirmSubmitDialog.is, GrConfirmSubmitDialog);
