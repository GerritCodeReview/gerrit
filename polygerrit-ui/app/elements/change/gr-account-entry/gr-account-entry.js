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
    is: 'gr-account-entry',

    /**
     * Fired when an account is entered.
     *
     * @event add
     */

    properties: {
      borderless: Boolean,
      change: Object,
      placeholder: String,

      suggestFrom: {
        type: Number,
        value: 3,
      },

      filter: {
        type: Function,
        value: function() {
          return this.notOwnerOrReviewer.bind(this);
        },
      },

      query: {
        type: Function,
        value: function() {
          return this._getReviewerSuggestions.bind(this);
        },
      },

      _reviewers: {
        type: Array,
        value: function() { return []; },
      },
    },

    observers: [
      '_reviewersChanged(change.reviewers.*, change.owner)',
    ],

    focus: function() {
      this.$.input.focus();
    },

    clear: function() {
      this.$.input.clear();
    },

    _handleInputCommit: function(e) {
      this.fire('add', {value: e.detail.value});
    },

    _reviewersChanged: function(changeRecord, owner) {
      var reviewerSet = {};
      reviewerSet[owner._account_id] = true;
      var addReviewers = function(reviewers) {
        if (!reviewers) {
          return;
        }
        reviewers.forEach(function(reviewer) {
          reviewerSet[reviewer._account_id] = true;
        }.bind(this));
      };

      var reviewers = changeRecord.base;
      addReviewers(reviewers.CC);
      addReviewers(reviewers.REVIEWER);
      this._reviewers = reviewerSet;
    },

    notOwnerOrReviewer: function(reviewer) {
      var account = reviewer.account;
      if (!account) { return true; }
      return !this._reviewers[reviewer.account._account_id];
    },

    _makeSuggestion: function(reviewer) {
      if (reviewer.account) {
        return {
          name: reviewer.account.name + ' (' + reviewer.account.email + ')',
          value: reviewer,
        };
      } else if (reviewer.group) {
        return {
          name: reviewer.group.name + ' (group)',
          value: reviewer,
        };
      }
    },

    _getReviewerSuggestions: function(input) {
      var xhr = this.$.restAPI.getChangeSuggestedReviewers(
          this.change._number, input);

      this._lastAutocompleteRequest = xhr;

      return xhr.then(function(reviewers) {
        if (!reviewers) { return []; }
        return reviewers
            .filter(this.filter)
            .map(this._makeSuggestion);
      }.bind(this));
    },
  });
})();
