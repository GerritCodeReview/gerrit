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
      branch: String,
      changeNumber: Number,
      hasParent: Boolean,
      rebaseOnCurrent: Boolean,
      _text: String,
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

    // This is called by gr-change-actions every time the rebase dialog is
    // re-opened. Unlike other autocompletes that make a request with each
    // updated input, this one gets all recent changes once and then filters
    // them by the input. The query is re-run each time the dialog is opened
    // in case there are new/updated changes in the generic query since the
    // last time it was run.
    fetchRecentChanges() {
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

    _getRecentChanges() {
      if (this._recentChanges) {
        return Promise.resolve(this._recentChanges);
      }
      return this.fetchRecentChanges();
    },

    _getChangeSuggestions(input) {
      return this._getRecentChanges().then(changes =>
          this._filterChanges(input, changes));
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

    /**
     * There is a subtle but important difference between setting the base to an
     * empty string and omitting it entirely from the payload. An empty string
     * implies that the parent should be cleared and the change should be
     * rebased on top of the target branch. Leaving out the base implies that it
     * should be rebased on top of its current parent.
     */
    _getSelectedBase() {
      if (this.$.rebaseOnParentInput.checked) { return null; }
      if (this.$.rebaseOnTipInput.checked) { return ''; }
      // Change numbers will have their description appended by the
      // autocomplete.
      return this._text.split(':')[0];
    },

    _handleConfirmTap(e) {
      e.preventDefault();
      e.stopPropagation();
      this.dispatchEvent(new CustomEvent('confirm',
          {detail: {base: this._getSelectedBase()}}));
      this._text = '';
    },

    _handleCancelTap(e) {
      e.preventDefault();
      e.stopPropagation();
      this.dispatchEvent(new CustomEvent('cancel'));
      this._text = '';
    },

    _handleRebaseOnOther() {
      this.$.parentInput.focus();
    },

    _handleEnterChangeNumberClick() {
      this.$.rebaseOnOtherInput.checked = true;
    },

    /**
     * Sets the default radio button based on the state of the app and
     * the corresponding value to be submitted.
     */
    _updateSelectedOption(rebaseOnCurrent, hasParent) {
      // Polymer 2: check for undefined
      if ([rebaseOnCurrent, hasParent].some(arg => arg === undefined)) {
        return;
      }

      if (this._displayParentOption(rebaseOnCurrent, hasParent)) {
        this.$.rebaseOnParentInput.checked = true;
      } else if (this._displayTipOption(rebaseOnCurrent, hasParent)) {
        this.$.rebaseOnTipInput.checked = true;
      } else {
        this.$.rebaseOnOtherInput.checked = true;
      }
    },
  });
})();
