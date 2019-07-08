// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function() {
  'use strict';

  Polymer({
    is: 'gr-confirm-rebase-dialog',

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
      base: String,
      branch: String,
      hasParent: Boolean,
      rebaseOnCurrent: Boolean,
    },

    observers: [
      '_updateSelectedOption(rebaseOnCurrent, hasParent)',
    ],

    _displayParentOption: function(rebaseOnCurrent, hasParent) {
      return hasParent && rebaseOnCurrent;
    },

    _displayParentUpToDateMsg: function(rebaseOnCurrent, hasParent) {
      return hasParent && !rebaseOnCurrent;
    },

    _displayTipOption: function(rebaseOnCurrent, hasParent) {
      return !(!rebaseOnCurrent && !hasParent);
    },

    _handleConfirmTap: function(e) {
      e.preventDefault();
      this.fire('confirm', null, {bubbles: false});
    },

    _handleCancelTap: function(e) {
      e.preventDefault();
      this.fire('cancel', null, {bubbles: false});
    },

    _handleRebaseOnOther: function(e) {
      this.$.parentInput.focus();
    },

    /**
     * There is a subtle but important difference between setting the base to an
     * empty string and omitting it entirely from the payload. An empty string
     * implies that the parent should be cleared and the change should be
     * rebased on top of the target branch. Leaving out the base implies that it
     * should be rebased on top of its current parent.
     */
    _handleRebaseOnTip: function(e) {
      this.base = '';
    },

    _handleRebaseOnParent: function(e) {
      this.base = null;
    },

    _handleEnterChangeNumberTap: function(e) {
      this.$.rebaseOnOtherInput.checked = true;
    },

    /**
     * Sets the default radio button based on the state of the app and
     * the corresponding value to be submitted.
     */
    _updateSelectedOption: function(rebaseOnCurrent, hasParent) {
      if (this._displayParentOption(rebaseOnCurrent, hasParent)) {
        this.$.rebaseOnParentInput.checked = true;
        this._handleRebaseOnParent();
      } else if (this._displayTipOption(rebaseOnCurrent, hasParent)) {
        this.$.rebaseOnTipInput.checked = true;
        this._handleRebaseOnTip();
      } else {
        this.$.rebaseOnOtherInput.checked = true;
        this._handleRebaseOnOther();
      }
    },
  });
})();
