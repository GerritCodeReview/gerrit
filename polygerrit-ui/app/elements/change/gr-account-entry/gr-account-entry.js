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
      cc: {
        type: Boolean,
      },

      change: {
        type: Object,
      },

      disabled: {
        type: Boolean,
        value: false,
        reflectToAttribute: true,
      },

      _query: {
        type: Function,
        value: function() {
          return this._getReviewerSuggestions.bind(this);
        },
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
      var account = e.detail.value.account;
      this.fire('add', {account: account});
    },

    _reviewersChanged: function(changeRecord, owner) {
      var reviewers = changeRecord.base;
      var key = this.cc ? 'CC' : 'REVIEWER';
      var result = reviewers[key] || [];
      this._reviewers = result.filter(function(reviewer) {
        return reviewer._account_id != owner._account_id;
      });
    },

    _notInList: function(reviewer) {
      var account = reviewer.account;
      if (!account) { return true; }
      if (account._account_id === this.change.owner._account_id) {
        return false;
      }
      for (var i = 0; i < this._reviewers.length; i++) {
        if (account._account_id === this._reviewers[i]._account_id) {
          return false;
        }
      }
      return true;
    },

    _makeSuggestion: function(reviewer) {
      if (reviewer.account) {
        return {
          name: reviewer.account.name + ' (' + reviewer.account.email + ')',
          value: reviewer,
        };
      } else if (reviewer.group) {
        return {
          name: reviewer.group.name,
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
            .filter(this._notInList.bind(this))
            .map(this._makeSuggestion);
      }.bind(this));
    },
  });
})();
