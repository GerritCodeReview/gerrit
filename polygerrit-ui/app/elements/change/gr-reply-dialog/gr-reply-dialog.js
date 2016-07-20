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
    is: 'gr-reply-dialog',

    /**
     * Fired when a reply is successfully sent.
     *
     * @event send
     */

    /**
     * Fired when the user presses the cancel button.
     *
     * @event cancel
     */

    properties: {
      change: Object,
      patchNum: String,
      revisions: Object,
      disabled: {
        type: Boolean,
        value: false,
        reflectToAttribute: true,
      },
      draft: {
        type: String,
        value: '',
      },
      diffDrafts: Object,
      labels: Object,
      permittedLabels: Object,

      _account: Object,
      _owners: Array,
      _reviewers: Array,
    },

    behaviors: [
      Gerrit.RESTClientBehavior,
    ],

    observers: [
      '_changeUpdated(change.*)',
    ],

    attached: function() {
      this._getAccount().then(function(account) {
        this._account = account;
      }.bind(this));
    },

    ready: function() {
      this.$.jsAPI.addElement(this.$.jsAPI.Element.REPLY_DIALOG, this);
    },

    focus: function() {
      this.async(function() {
        this.$.textarea.textarea.focus();
      }.bind(this));
    },

    getFocusStops: function() {
      return {
        start: this.$.reviewers.focusStart,
        end: this.$.cancelButton,
      };
    },

    setLabelValue: function(label, value) {
      var selectorEl = this.$$('iron-selector[data-label="' + label + '"]');
      // The selector may not be present if it’s not at the latest patch set.
      if (!selectorEl) { return; }
      var item = selectorEl.$$('gr-button[data-value="' + value + '"]');
      if (!item) { return; }
      selectorEl.selectIndex(selectorEl.indexOf(item));
    },

    send: function() {
      var obj = {
        drafts: 'PUBLISH_ALL_REVISIONS',
        labels: {},
      };
      for (var label in this.permittedLabels) {
        if (!this.permittedLabels.hasOwnProperty(label)) { continue; }

        var selectorEl = this.$$('iron-selector[data-label="' + label + '"]');

        // The selector may not be present if it’s not at the latest patch set.
        if (!selectorEl) { continue; }

        var selectedVal = selectorEl.selectedItem.getAttribute('data-value');
        selectedVal = parseInt(selectedVal, 10);
        obj.labels[label] = selectedVal;
      }
      if (this.draft != null) {
        obj.message = this.draft;
      }

      var newReviewers = this.$.reviewers.additions();
      newReviewers.forEach(function(reviewer) {
        var reviewerId;
        if (reviewer.account) {
          reviewerId = reviewer.account._account_id;
        } else if (reviewer.group) {
          reviewerId = reviewer.group.id;
        }
        if (!obj.reviewers) {
          obj.reviewers = [];
        }
        obj.reviewers.push({reviewer: reviewerId});
      });

      this.disabled = true;
      return this._saveReview(obj).then(function(response) {
        this.disabled = false;
        if (!response.ok) { return response; }

        this.draft = '';
        this.fire('send', null, {bubbles: false});
      }.bind(this)).catch(function(err) {
        this.disabled = false;
        throw err;
      }.bind(this));
    },

    _computeShowLabels: function(patchNum, revisions) {
      var num = parseInt(patchNum, 10);
      for (var rev in revisions) {
        if (revisions[rev]._number > num) {
          return false;
        }
      }
      return true;
    },

    _computeHideDraftList: function(drafts) {
      return Object.keys(drafts || {}).length == 0;
    },

    _computeDraftsTitle: function(drafts) {
      var total = 0;
      for (var file in drafts) {
        total += drafts[file].length;
      }
      if (total == 0) { return ''; }
      if (total == 1) { return '1 Draft'; }
      if (total > 1) { return total + ' Drafts'; }
    },

    _computeLabelValueTitle: function(labels, label, value) {
      return labels[label] && labels[label].values[value];
    },

    _computeLabelArray: function(labelsObj) {
      return Object.keys(labelsObj).sort();
    },

    _computeIndexOfLabelValue: function(
        labels, permittedLabels, labelName, account) {
      var t = labels[labelName];
      if (!t) { return null; }
      var labelValue = t.default_value;

      // Is there an existing vote for the current user? If so, use that.
      var votes = labels[labelName];
      if (votes.all && votes.all.length > 0) {
        for (var i = 0; i < votes.all.length; i++) {
          if (votes.all[i]._account_id == account._account_id) {
            labelValue = votes.all[i].value;
            break;
          }
        }
      }

      var len = permittedLabels[labelName] != null ?
          permittedLabels[labelName].length : 0;
      for (var i = 0; i < len; i++) {
        var val = parseInt(permittedLabels[labelName][i], 10);
        if (val == labelValue) {
          return i;
        }
      }
      return null;
    },

    _computePermittedLabelValues: function(permittedLabels, label) {
      return permittedLabels[label];
    },

    _changeUpdated: function(changeRecord) {
      if (!changeRecord.path || !changeRecord.base) {
        return;
      }

      if (changeRecord.path !== 'change' &&
          changeRecord.path !== 'change.reviewers.CC.splices' &&
          changeRecord.path !== 'change.reviewers.REVIEWER.splices') {
        return;
      }

      var owner = changeRecord.base.owner;
      this._owners = [owner];

      if (!changeRecord.base.reviewers) {
        return;
      }

      var reviewers = changeRecord.base.reviewers.REVIEWER || [];
      reviewers = reviewers.concat(changeRecord.base.reviewers.CC);
      reviewers = reviewers.filter(function(account) {
        return account && account._account_id !== owner._account_id;
      }.bind(this));
      this._reviewers = reviewers;
    },

    _getAccount: function() {
      return this.$.restAPI.getAccount();
    },

    _cancelTapHandler: function(e) {
      e.preventDefault();
      this.fire('cancel', null, {bubbles: false});
    },

    _sendTapHandler: function(e) {
      e.preventDefault();
      this.send();
    },

    _saveReview: function(review) {
      return this.$.restAPI.saveChangeReview(this.change._number, this.patchNum,
          review);
    },
  });
})();
