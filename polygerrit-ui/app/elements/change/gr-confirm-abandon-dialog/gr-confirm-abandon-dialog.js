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
(function() {
  'use strict';

  /**
    * @appliesMixin Gerrit.FireMixin
    * @appliesMixin Gerrit.KeyboardShortcutMixin
    */
  class GrConfirmAbandonDialog extends Polymer.mixinBehaviors( [
    Gerrit.FireBehavior,
    Gerrit.KeyboardShortcutBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-confirm-abandon-dialog'; }
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
        message: String,
      };
    }

    get keyBindings() {
      return {
        'ctrl+enter meta+enter': '_handleEnterKey',
      };
    }

    resetFocus() {
      this.$.messageInput.textarea.focus();
    }

    _handleEnterKey(e) {
      this._confirm();
    }

    _handleConfirmTap(e) {
      e.preventDefault();
      e.stopPropagation();
      this._confirm();
    }

    _confirm() {
      this.fire('confirm', {reason: this.message}, {bubbles: false});
    }

    _handleCancelTap(e) {
      e.preventDefault();
      e.stopPropagation();
      this.fire('cancel', null, {bubbles: false});
    }
  }

  customElements.define(GrConfirmAbandonDialog.is, GrConfirmAbandonDialog);
})();
