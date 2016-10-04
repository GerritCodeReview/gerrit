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

  var SubmitTypeLabel = {
    FAST_FORWARD_ONLY: 'Fast Forward Only',
    MERGE_IF_NECESSARY: 'Merge if Necessary',
    REBASE_IF_NECESSARY: 'Rebase if Necessary',
    MERGE_ALWAYS: 'Always Merge',
    CHERRY_PICK: 'Cherry Pick',
  };

  Polymer({
    is: 'gr-change-metadata',

    properties: {
      change: Object,
      mutable: Boolean,
      serverConfig: Object,
      _topicReadOnly: {
        type: Boolean,
        computed: '_computeTopicReadOnly(mutable, change)',
      },
      _showReviewersByState: {
        type: Boolean,
        computed: '_computeShowReviewersByState(serverConfig)',
      },
    },

    behaviors: [
      Gerrit.RESTClientBehavior,
    ],

    _computeHideStrategy: function(change) {
      return !this.changeIsOpen(change.status);
    },

    _computeStrategy: function(change) {
      return SubmitTypeLabel[change.submit_type];
    },

    _computeLabelNames: function(labels) {
      return Object.keys(labels).sort();
    },

    _computeLabelValues: function(labelName, _labels) {
      var result = [];
      var labels = _labels.base;
      var t = labels[labelName];
      if (!t) { return result; }
      var approvals = t.all || [];
      approvals.forEach(function(label) {
        if (label.value && label.value != labels[labelName].default_value) {
          var labelClassName;
          var labelValPrefix = '';
          if (label.value > 0) {
            labelValPrefix = '+';
            labelClassName = 'approved';
          } else if (label.value < 0) {
            labelClassName = 'notApproved';
          }
          result.push({
            value: labelValPrefix + label.value,
            className: labelClassName,
            account: label,
          });
        }
      });
      return result;
    },

    _computeValueTooltip: function(score, labelName) {
      var values = this.change.labels[labelName].values;
      return values[score];
    },

    _handleTopicChanged: function(e, topic) {
      if (!topic.length) { topic = null; }
      this.$.restAPI.setChangeTopic(this.change.id, topic);
    },

    _computeTopicReadOnly: function(mutable, change) {
      return !mutable || !change.actions.topic || !change.actions.topic.enabled;
    },

    _computeTopicPlaceholder: function(_topicReadOnly) {
      return _topicReadOnly ? 'No Topic' : 'Click to add topic';
    },

    _computeShowReviewersByState: function(serverConfig) {
      return !!serverConfig.note_db_enabled;
    },

    /**
     * A user is able to delete a vote iff the mutable property is true and the
     * reviewer that left the vote exists in the list of removable_reviewers
     * received from the backend.
     *
     * @param {!Object} reviewer An object describing the reviewer that left the
     *     vote.
     * @param {boolean} mutable this.mutable describes whether the
     *     change-metadata section is modifiable by the current user.
     */
    _computeCanDeleteVote: function(reviewer, mutable) {
      if (!mutable) { return false; }
      for (var i = 0; i < this.change.removable_reviewers.length; i++) {
        if (this.change.removable_reviewers[i]._account_id ===
            reviewer._account_id) {
          return true;
        }
      }
      return false;
    },

    _onDeleteVote: function(e) {
      e.preventDefault();
      var target = Polymer.dom(e).rootTarget;
      var labelName = target.labelName;
      var accountID = parseInt(target.getAttribute('data-account-id'), 10);
      this._xhrPromise =
          this.$.restAPI.deleteVote(this.change.id, accountID, labelName)
          .then(function(response) {
        if (!response.ok) { return response; }

        var labels = this.change.labels[labelName].all || [];
        for (var i = 0; i < labels.length; i++) {
          if (labels[i]._account_id === accountID) {
            this.splice(['change.labels', labelName, 'all'], i, 1);
            break;
          }
        }
      }.bind(this));
    },
  });
})();
