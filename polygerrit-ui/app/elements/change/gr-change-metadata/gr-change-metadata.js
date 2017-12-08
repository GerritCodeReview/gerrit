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

  const HASHTAG_ADD_MESSAGE = 'Add Hashtag';

  const SubmitTypeLabel = {
    FAST_FORWARD_ONLY: 'Fast Forward Only',
    MERGE_IF_NECESSARY: 'Merge if Necessary',
    REBASE_IF_NECESSARY: 'Rebase if Necessary',
    MERGE_ALWAYS: 'Always Merge',
    REBASE_ALWAYS: 'Rebase Always',
    CHERRY_PICK: 'Cherry Pick',
  };

  Polymer({
    is: 'gr-change-metadata',

    /**
     * Fired when the change topic is changed.
     *
     * @event topic-changed
     */

    properties: {
      /** @type {?} */
      change: Object,
      /** @type {?} */
      revision: Object,
      commitInfo: Object,
      mutable: Boolean,
      /**
       * @type {{ note_db_enabled: string }}
       */
      serverConfig: Object,
      _topicReadOnly: {
        type: Boolean,
        computed: '_computeTopicReadOnly(mutable, change)',
      },
      _hashtagReadOnly: {
        type: Boolean,
        computed: '_computeHashtagReadOnly(mutable, change)',
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
      _isWip: {
        type: Boolean,
        computed: '_computeIsWip(change)',
      },
      _newHashtag: String,

      _settingTopic: {
        type: Boolean,
        value: false,
      },
    },

    behaviors: [
      Gerrit.RESTClientBehavior,
    ],

    observers: [
      '_changeChanged(change)',
      '_assigneeChanged(_assignee.*)',
    ],

    _changeChanged(change) {
      this._assignee = change.assignee ? [change.assignee] : [];
    },

    _assigneeChanged(assigneeRecord) {
      if (!this.change) { return; }
      const assignee = assigneeRecord.base;
      if (assignee.length) {
        const acct = assignee[0];
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

    _computeHideStrategy(change) {
      return !this.changeIsOpen(change.status);
    },

    /**
     * @param {Object} commitInfo
     * @return {?Array} If array is empty, returns null instead so
     * an existential check can be used to hide or show the webLinks
     * section.
     */
    _computeWebLinks(commitInfo) {
      if (!commitInfo) { return null; }
      const weblinks = Gerrit.Nav.getChangeWeblinks(
          this.change ? this.change.repo : '',
          commitInfo.commit,
          {weblinks: commitInfo.web_links});
      return weblinks.length ? weblinks : null;
    },

    _computeStrategy(change) {
      return SubmitTypeLabel[change.submit_type];
    },

    _computeLabelNames(labels) {
      return Object.keys(labels).sort();
    },

    _computeLabelValues(labelName, _labels) {
      const result = [];
      const labels = _labels.base;
      const t = labels[labelName];
      if (!t) { return result; }
      const approvals = t.all || [];
      for (const label of approvals) {
        if (label.value && label.value != labels[labelName].default_value) {
          let labelClassName;
          let labelValPrefix = '';
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
      }
      return result;
    },

    _computeValueTooltip(change, score, labelName) {
      if (!change.labels[labelName] ||
          !change.labels[labelName].values ||
          !change.labels[labelName].values[score]) { return ''; }
      return change.labels[labelName].values[score];
    },

    _handleTopicChanged(e, topic) {
      const lastTopic = this.change.topic;
      if (!topic.length) { topic = null; }
      this._settingTopic = true;
      this.$.restAPI.setChangeTopic(this.change._number, topic)
          .then(newTopic => {
            this._settingTopic = false;
            this.set(['change', 'topic'], newTopic);
            if (newTopic !== lastTopic) {
              this.dispatchEvent(
                  new CustomEvent('topic-changed', {bubbles: true}));
            }
          });
    },

    _showAddTopic(changeRecord, settingTopic) {
      const hasTopic = !!changeRecord && !!changeRecord.base.topic;
      return !hasTopic && !settingTopic;
    },

    _showTopicChip(changeRecord, settingTopic) {
      const hasTopic = !!changeRecord && !!changeRecord.base.topic;
      return hasTopic && !settingTopic;
    },

    _handleHashtagChanged(e) {
      const lastHashtag = this.change.hashtag;
      if (!this._newHashtag.length) { return; }
      const newHashtag = this._newHashtag;
      this._newHashtag = '';
      this.$.restAPI.setChangeHashtag(
          this.change._number, {add: [newHashtag]}).then(newHashtag => {
            this.set(['change', 'hashtags'], newHashtag);
            if (newHashtag !== lastHashtag) {
              this.dispatchEvent(
                  new CustomEvent('hashtag-changed', {bubbles: true}));
            }
          });
    },

    _computeTopicReadOnly(mutable, change) {
      return !mutable || !change.actions.topic || !change.actions.topic.enabled;
    },

    _computeHashtagReadOnly(mutable, change) {
      return !mutable ||
          !change.actions.hashtags ||
          !change.actions.hashtags.enabled;
    },

    _computeAssigneeReadOnly(mutable, change) {
      return !mutable ||
          !change.actions.assignee ||
          !change.actions.assignee.enabled;
    },

    _computeTopicPlaceholder(_topicReadOnly) {
      return _topicReadOnly ? 'No Topic' : 'Add Topic';
    },

    _computeHashtagPlaceholder(_hashtagReadOnly) {
      return _hashtagReadOnly ? '' : HASHTAG_ADD_MESSAGE;
    },

    _computeShowReviewersByState(serverConfig) {
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
    _computeCanDeleteVote(reviewer, mutable) {
      if (!mutable) { return false; }
      for (let i = 0; i < this.change.removable_reviewers.length; i++) {
        if (this.change.removable_reviewers[i]._account_id ===
            reviewer._account_id) {
          return true;
        }
      }
      return false;
    },

    /**
     * Closure annotation for Polymer.prototype.splice is off.
     * For now, supressing annotations.
     *
     * TODO(beckysiegel) submit Polymer PR
     *
     * @suppress {checkTypes} */
    _onDeleteVote(e) {
      e.preventDefault();
      const target = Polymer.dom(e).rootTarget;
      target.disabled = true;
      const labelName = target.labelName;
      const accountID = parseInt(target.getAttribute('data-account-id'), 10);
      this._xhrPromise =
          this.$.restAPI.deleteVote(this.change._number, accountID, labelName)
          .then(response => {
            target.disabled = false;
            if (!response.ok) { return response; }
            const label = this.change.labels[labelName];
            const labels = label.all || [];
            for (let i = 0; i < labels.length; i++) {
              if (labels[i]._account_id === accountID) {
                for (const key in label) {
                  if (label.hasOwnProperty(key) &&
                      label[key]._account_id === accountID) {
                    // Remove special label field, keeping change label values
                    // in sync with the backend.
                    this.set(['change.labels', labelName, key], null);
                  }
                }
                this.splice(['change.labels', labelName, 'all'], i, 1);
                break;
              }
            }
          }).catch(err => {
            target.disabled = false;
            return;
          });
    },

    _computeShowLabelStatus(change) {
      const isNewChange = change.status === this.ChangeStatus.NEW;
      const hasLabels = Object.keys(change.labels).length > 0;
      return isNewChange && hasLabels;
    },

    _computeMissingLabels(labels) {
      const missingLabels = [];
      for (const label in labels) {
        if (!labels.hasOwnProperty(label)) { continue; }
        const obj = labels[label];
        if (!obj.optional && !obj.approved) {
          missingLabels.push(label);
        }
      }
      return missingLabels;
    },

    _computeMissingLabelsHeader(labels) {
      return 'Needs label' +
          (this._computeMissingLabels(labels).length > 1 ? 's' : '') + ':';
    },

    _showMissingLabels(labels) {
      return !!this._computeMissingLabels(labels).length;
    },

    _showMissingRequirements(labels, workInProgress) {
      return workInProgress || this._showMissingLabels(labels);
    },

    _computeProjectURL(project) {
      return Gerrit.Nav.getUrlForProjectChanges(project);
    },

    _computeBranchURL(project, branch) {
      return Gerrit.Nav.getUrlForBranch(branch, project,
          this.change.status == this.ChangeStatus.NEW ? 'open' :
              this.change.status.toLowerCase());
    },

    _computeTopicURL(topic) {
      return Gerrit.Nav.getUrlForTopic(topic);
    },

    _computeHashtagURL(hashtag) {
      return Gerrit.Nav.getUrlForHashtag(hashtag);
    },

    _handleTopicRemoved(e) {
      const target = Polymer.dom(e).rootTarget;
      target.disabled = true;
      this.$.restAPI.setChangeTopic(this.change._number, null).then(() => {
        target.disabled = false;
        this.set(['change', 'topic'], '');
        this.dispatchEvent(
            new CustomEvent('topic-changed', {bubbles: true}));
      }).catch(err => {
        target.disabled = false;
        return;
      });
    },

    _handleHashtagRemoved(e) {
      e.preventDefault();
      const target = Polymer.dom(e).rootTarget;
      target.disabled = true;
      this.$.restAPI.setChangeHashtag(this.change._number,
          {remove: [target.text]})
          .then(newHashtag => {
            target.disabled = false;
            this.set(['change', 'hashtags'], newHashtag);
          }).catch(err => {
            target.disabled = false;
            return;
          });
    },

    _computeIsWip(change) {
      return !!change.work_in_progress;
    },

    _computeShowUploaderHide(change) {
      return this._computeShowUploader(change) ? '' : 'hideDisplay';
    },

    _computeShowUploader(change) {
      if (!change.current_revision ||
          !change.revisions[change.current_revision]) {
        return null;
      }

      const rev = change.revisions[change.current_revision];

      if (!rev || !rev.uploader ||
        change.owner._account_id === rev.uploader._account_id) {
        return null;
      }

      return rev.uploader;
    },
  });
})();
