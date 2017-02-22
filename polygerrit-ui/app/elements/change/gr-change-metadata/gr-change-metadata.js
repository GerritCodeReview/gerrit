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
      commitInfo: Object,
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
      _showLabelStatus: {
        type: Boolean,
        computed: '_computeShowLabelStatus(change)',
      },

      _assignee: Array,
    },

    behaviors: [
      Gerrit.RESTClientBehavior,
    ],

    observers: [
      '_changeChanged(change)',
      '_assigneeChanged(_assignee.*)',
    ],

    _changeChanged: function(change) {
      this._assignee = change.assignee ? [change.assignee] : [];
    },

    _assigneeChanged: function(assigneeRecord) {
      if (!this.change) { return; }
      var assignee = assigneeRecord.base;
      if (assignee.length) {
        var acct = assignee[0];
        if (this.change.assignee &&
            acct._account_id === this.change.assignee._account_id) { return; }
        this.set(['change', 'assignee'], acct);
        this.$.restAPI.setAssignee(this.change._number, acct._account_id);
      } else {
        if (!this.change.assignee) { return; }
        this.set(['change', 'assignee'], undefined);
        this.$.restAPI.deleteAssignee(this.change._number);
      }
    },

    _computeHideStrategy: function(change) {
      return !this.changeIsOpen(change.status);
    },

    /**
     * This is a whitelist of web link types that provide direct links to
     * the commit in the url property.
     */
    _isCommitWebLink: function(link) {
      return link.name === 'gitiles' || link.name === 'gitweb';
    },

    /**
     * @param {Object} commitInfo
     * @return {?Array} If array is empty, returns null instead so
     * an existential check can be used to hide or show the webLinks
     * section.
     */
    _computeWebLinks: function(commitInfo) {
      if (!commitInfo || !commitInfo.web_links) { return null }
      // We are already displaying these types of links elsewhere,
      // don't include in the metadata links section.
      var webLinks = commitInfo.web_links.filter(
          function(l) {return !this._isCommitWebLink(l); }.bind(this));

      return webLinks.length ? webLinks : null;
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
      this.$.restAPI.setChangeTopic(this.change._number, topic);
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

    _computeShowLabelStatus: function(change) {
      var isNewChange = change.status === this.ChangeStatus.NEW;
      var hasLabels = Object.keys(change.labels).length > 0;
      return isNewChange && hasLabels;
    },

    _computeSubmitStatus: function(labels) {
      var missingLabels = [];
      var output = '';
      for (var label in labels) {
        var obj = labels[label];
        if (!obj.optional && !obj.approved) {
          missingLabels.push(label);
        }
      }
      if (missingLabels.length) {
        output += 'Needs ';
        output += missingLabels.join(' and ');
        output += missingLabels.length > 1 ? ' labels' : ' label';
      } else {
        output = 'Ready to submit';
      }
      return output;
    },

    _computeTopicHref: function(topic) {
      var encodedTopic = encodeURIComponent('\"' + topic + '\"');
      return '/q/topic:' + encodeURIComponent(encodedTopic) +
          '+(status:open OR status:merged)';
    },

    _handleTopicRemoved: function() {
      this.set(['change', 'topic'], '');
      this.$.restAPI.setChangeTopic(this.change._number, null);
    },
  });
})();
