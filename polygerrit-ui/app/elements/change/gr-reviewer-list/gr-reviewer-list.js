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
    is: 'gr-reviewer-list',

    /**
     * Fired when the "Add reviewer..." button is tapped.
     *
     * @event show-reply-dialog
     */

    properties: {
      change: Object,
      disabled: {
        type: Boolean,
        value: false,
        reflectToAttribute: true,
      },
      mutable: {
        type: Boolean,
        value: false,
      },
      reviewersOnly: {
        type: Boolean,
        value: false,
      },
      ccsOnly: {
        type: Boolean,
        value: false,
      },
      maxReviewersDisplayed: Number,

      _displayedReviewers: {
        type: Array,
        value() { return []; },
      },
      _reviewers: {
        type: Array,
        value() { return []; },
      },
      _showInput: {
        type: Boolean,
        value: false,
      },
      _addLabel: {
        type: String,
        computed: '_computeAddLabel(ccsOnly)',
      },
      _hiddenReviewerCount: {
        type: Number,
        computed: '_computeHiddenCount(_reviewers, _displayedReviewers)',
      },


      // Used for testing.
      _lastAutocompleteRequest: Object,
      _xhrPromise: Object,
    },

    observers: [
      '_reviewersChanged(change.reviewers.*, change.owner)',
    ],

    _reviewersChanged(changeRecord, owner) {
      let result = [];
      const reviewers = changeRecord.base;
      for (const key in reviewers) {
        if (this.reviewersOnly && key !== 'REVIEWER') {
          continue;
        }
        if (this.ccsOnly && key !== 'CC') {
          continue;
        }
        if (key === 'REVIEWER' || key === 'CC') {
          result = result.concat(reviewers[key]);
        }
      }
      this._reviewers = result.filter(reviewer => {
        return reviewer._account_id != owner._account_id;
      });

      // If there is one more than the max reviewers, don't show the 'show
      // more' button, because it takes up just as much space.
      if (this.maxReviewersDisplayed &&
          this._reviewers.length > this.maxReviewersDisplayed + 1) {
        this._displayedReviewers =
          this._reviewers.slice(0, this.maxReviewersDisplayed);
      } else {
        this._displayedReviewers = this._reviewers;
      }
    },

    _computeHiddenCount(reviewers, displayedReviewers) {
      return reviewers.length - displayedReviewers.length;
    },

    _computeCanRemoveReviewer(reviewer, mutable) {
      if (!mutable) { return false; }

      let current;
      for (let i = 0; i < this.change.removable_reviewers.length; i++) {
        current = this.change.removable_reviewers[i];
        if (current._account_id === reviewer._account_id ||
            (!reviewer._account_id && current.email === reviewer.email)) {
          return true;
        }
      }
      return false;
    },

    _handleRemove(e) {
      e.preventDefault();
      const target = Polymer.dom(e).rootTarget;
      if (!target.account) { return; }
      const accountID = target.account._account_id || target.account.email;
      this.disabled = true;
      this._xhrPromise = this._removeReviewer(accountID).then(response => {
        this.disabled = false;
        if (!response.ok) { return response; }

        const reviewers = this.change.reviewers;

        for (const type of ['REVIEWER', 'CC']) {
          reviewers[type] = reviewers[type] || [];
          for (let i = 0; i < reviewers[type].length; i++) {
            if (reviewers[type][i]._account_id == accountID ||
            reviewers[type][i].email == accountID) {
              this.splice('change.reviewers.' + type, i, 1);
              break;
            }
          }
        }
      }).catch(err => {
        this.disabled = false;
        throw err;
      });
    },

    _handleAddTap(e) {
      e.preventDefault();
      const value = {};
      if (this.reviewersOnly) {
        value.reviewersOnly = true;
      }
      if (this.ccsOnly) {
        value.ccsOnly = true;
      }
      this.fire('show-reply-dialog', {value});
    },

    _handleViewAll(e) {
      this._displayedReviewers = this._reviewers;
    },

    _removeReviewer(id) {
      return this.$.restAPI.removeChangeReviewer(this.change._number, id);
    },

    _computeAddLabel(ccsOnly) {
      return ccsOnly ? 'Add CC' : 'Add reviewer';
    },
  });
})();
