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
(function() {
  'use strict';

  class GrConfirmSubmitDialog extends Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element)) {
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
})();
