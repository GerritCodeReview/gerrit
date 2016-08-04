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

      _reviewers: {
        type: Array,
        value: function() { return []; },
      },
      _showInput: {
        type: Boolean,
        value: false,
      },
      _addLabel: {
        type: String,
        computed: '_computeAddLabel(ccsOnly)',
      },

      // Used for testing.
      _lastAutocompleteRequest: Object,
      _xhrPromise: Object,
    },

    observers: [
      '_reviewersChanged(change.reviewers.*, change.owner)',
    ],

    _reviewersChanged: function(changeRecord, owner) {
      var result = [];
      var reviewers = changeRecord.base;
      for (var key in reviewers) {
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
      this._reviewers = result.filter(function(reviewer) {
        return reviewer._account_id != owner._account_id;
      });
    },

    _computeCanRemoveReviewer: function(reviewer, mutable) {
      if (!mutable) { return false; }

      for (var i = 0; i < this.change.removable_reviewers.length; i++) {
        if (this.change.removable_reviewers[i]._account_id ==
            reviewer._account_id) {
          return true;
        }
      }
      return false;
    },

    _handleRemove: function(e) {
      e.preventDefault();
      var target = Polymer.dom(e).rootTarget;
      var accountID = parseInt(target.getAttribute('data-account-id'), 10);
      this.disabled = true;
      this._xhrPromise =
          this._removeReviewer(accountID).then(function(response) {
        this.disabled = false;
        if (!response.ok) { return response; }

        var reviewers = this.change.reviewers;
        ['REVIEWER', 'CC'].forEach(function(type) {
          reviewers[type] = reviewers[type] || [];
          for (var i = 0; i < reviewers[type].length; i++) {
            if (reviewers[type][i]._account_id == accountID) {
              this.splice('change.reviewers.' + type, i, 1);
              break;
            }
          }
        }, this);
      }.bind(this)).catch(function(err) {
        this.disabled = false;
        throw err;
      }.bind(this));
    },

    _handleAddTap: function(e) {
      e.preventDefault();
      var value = {};
      if (this.reviewersOnly) {
        value.reviewersOnly = true;
      }
      if (this.ccsOnly) {
        value.ccsOnly = true;
      }
      this.fire('show-reply-dialog', {value: value});
    },

    _removeReviewer: function(id) {
      return this.$.restAPI.removeChangeReviewer(this.change._number, id);
    },

    _computeAddLabel: function(ccsOnly) {
      return ccsOnly ? 'Add CC' : 'Add reviewer';
    },
  });
})();
