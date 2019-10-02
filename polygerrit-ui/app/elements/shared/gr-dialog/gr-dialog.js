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

  Polymer({
    is: 'gr-dialog',

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

    properties: {
      confirmLabel: {
        type: String,
        value: 'Confirm',
      },
      // Supplying an empty cancel label will hide the button completely.
      cancelLabel: {
        type: String,
        value: 'Cancel',
      },
      disabled: {
        type: Boolean,
        value: false,
      },
      confirmOnEnter: {
        type: Boolean,
        value: false,
      },
    },

    behaviors: [
      Gerrit.FireBehavior,
    ],

    hostAttributes: {
      role: 'dialog',
    },

    _handleConfirm(e) {
      if (this.disabled) { return; }

      e.preventDefault();
      e.stopPropagation();
      this.fire('confirm', null, {bubbles: false});
    },

    _handleCancelTap(e) {
      e.preventDefault();
      e.stopPropagation();
      this.fire('cancel', null, {bubbles: false});
    },

    _handleKeydown(e) {
      if (this.confirmOnEnter && e.keyCode === 13) { this._handleConfirm(e); }
    },

    resetFocus() {
      this.$.confirm.focus();
    },

    _computeCancelClass(cancelLabel) {
      return cancelLabel.length ? '' : 'hidden';
    },
  });
})();
