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
      /**
       * Weird API usage requires this to be String or Null. Add this so
       * the closure compiler doesn't complain.
       * @type {?string} */
      base: String,
      branch: String,
      changeNumber: Number, 
      hasParent: Boolean,
      rebaseOnCurrent: Boolean,
      _inputText: String,
      _query: {
        type: Function,
        value() {
          return this._getChangeSuggestions.bind(this);
        },
      },
      _recentChanges: Array,
    },

    observers: [
      '_updateSelectedOption(rebaseOnCurrent, hasParent)',
    ],

    _getRecentChanges() {
      if (this._recentChanges) {
        return Promise.resolve(this._recentChanges);
      }
      return this.$.restAPI.getChanges(null, `is:open -age:90d`)
          .then(response => {
            const changes = [];
            for (const key in response) {
              if (!response.hasOwnProperty(key)) { continue; }
              changes.push({
                name: `${response[key]._number}: ${response[key].subject}`,
                value: response[key]._number,
              });
            }
            this._recentChanges = changes;
            return this._recentChanges;
          });
    },

    _getChangeSuggestions(input) {
      return this._getRecentChanges().then(changes => {
        return this._filterChanges(input, changes);
      });
    },

    _filterChanges(input, changes) {
      return changes.filter(change => change.name.includes(input) &&
                change.value !== this.changeNumber);
    },

    _displayParentOption(rebaseOnCurrent, hasParent) {
      return hasParent && rebaseOnCurrent;
    },

    _displayParentUpToDateMsg(rebaseOnCurrent, hasParent) {
      return hasParent && !rebaseOnCurrent;
    },

    _displayTipOption(rebaseOnCurrent, hasParent) {
      return !(!rebaseOnCurrent && !hasParent);
    },

    _handleConfirmTap(e) {
      e.preventDefault();
      this._inputText = '';
      this.fire('confirm', null, {bubbles: false});
    },

    _handleCancelTap(e) {
      e.preventDefault();
      this._inputText = '';
      this.fire('cancel', null, {bubbles: false});
    },

    _handleRebaseOnOther() {
      this.$.parentInput.focus();
    },

    /**
     * There is a subtle but important difference between setting the base to an
     * empty string and omitting it entirely from the payload. An empty string
     * implies that the parent should be cleared and the change should be
     * rebased on top of the target branch. Leaving out the base implies that it
     * should be rebased on top of its current parent.
     */
    _handleRebaseOnTip() {
      this.base = '';
    },

    _handleRebaseOnParent() {
      this.base = null;
    },

    _handleBaseSelected(e) {
      this.base = e.detail.value;
    },

    _handleEnterChangeNumberTap() {
      this.$.rebaseOnOtherInput.checked = true;
    },

    /**
     * Sets the default radio button based on the state of the app and
     * the corresponding value to be submitted.
     */
    _updateSelectedOption(rebaseOnCurrent, hasParent) {
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
