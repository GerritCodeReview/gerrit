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

  var STORAGE_DEBOUNCE_INTERVAL_MS = 400;

  var FocusTarget = {
    ANY: 'any',
    BODY: 'body',
    CCS: 'cc',
    REVIEWERS: 'reviewers',
  };

  var ReviewerTypes = {
    REVIEWER: 'REVIEWER',
    CC: 'CC',
  };

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

    /**
     * Fired when the main textarea's value changes, which may have triggered
     * a change in size for the dialog.
     *
     * @event autogrow
     */

    properties: {
      change: Object,
      patchNum: String,
      disabled: {
        type: Boolean,
        value: false,
        reflectToAttribute: true,
      },
      draft: {
        type: String,
        value: '',
        observer: '_draftChanged',
      },
      quote: {
        type: String,
        value: '',
      },
      diffDrafts: Object,
      filterReviewerSuggestion: {
        type: Function,
        value: function() {
          return this._filterReviewerSuggestion.bind(this);
        },
      },
      permittedLabels: Object,
      serverConfig: Object,
      projectConfig: Object,

      _account: Object,
      _ccs: Array,
      _ccPendingConfirmation: {
        type: Object,
        observer: '_reviewerPendingConfirmationUpdated',
      },
      _labels: {
        type: Array,
        computed: '_computeLabels(change.labels.*, _account)',
      },
      _owner: Object,
      _pendingConfirmationDetails: Object,
      _reviewers: Array,
      _reviewerPendingConfirmation: {
        type: Object,
        observer: '_reviewerPendingConfirmationUpdated',
      },
      _previewFormatting: {
        type: Boolean,
        value: false,
        observer: '_handleHeightChanged',
      },
      _reviewersPendingRemove: {
        type: Object,
        value: {
          CC: [],
          REVIEWER: [],
        },
      },
    },

    FocusTarget: FocusTarget,

    behaviors: [
      Gerrit.RESTClientBehavior,
    ],

    observers: [
      '_changeUpdated(change.reviewers.*, change.owner, serverConfig)',
      '_ccsChanged(_ccs.splices)',
      '_reviewersChanged(_reviewers.splices)',
    ],

    attached: function() {
      this._getAccount().then(function(account) {
        this._account = account || {};
      }.bind(this));
    },

    ready: function() {
      this.$.jsAPI.addElement(this.$.jsAPI.Element.REPLY_DIALOG, this);
    },

    open: function(opt_focusTarget) {
      this._focusOn(opt_focusTarget);
      if (!this.draft || !this.draft.length) {
        this.draft = this._loadStoredDraft();
      }
    },

    focus: function() {
      this._focusOn(FocusTarget.ANY);
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

    _ccsChanged: function(splices) {
      if (splices && splices.indexSplices) {
        this._processReviewerChange(splices.indexSplices, ReviewerTypes.CC);
      }
    },

    _reviewersChanged: function(splices) {
      if (splices && splices.indexSplices) {
        this._processReviewerChange(splices.indexSplices,
            ReviewerTypes.REVIEWER);
      }
    },

    _processReviewerChange: function(indexSplices, type) {
      indexSplices.forEach(function(splice) {
        splice.removed.forEach(function(account) {
          if (!this._reviewersPendingRemove[type]) {
            console.err('Invalid type ' + type + ' for reviewer.');
            return;
          }
          this._reviewersPendingRemove[type].push(account);
        }.bind(this));
      }.bind(this));
    },

    /**
     * Resets the state of the _reviewersPendingRemove object, and removes
     * accounts if necessary.
     *
     * @param {Boolean} isCancel true if the action is a cancel.
     * @param {Object} opt_accountIdsTransferred map of account IDs that must
     *     not be removed, because they have been readded in another state.
     */
    _purgeReviewersPendingRemove: function(isCancel,
        opt_accountIdsTransferred) {
      var reviewerArr;
      var keep = opt_accountIdsTransferred || {};
      for (var type in this._reviewersPendingRemove) {
        if (this._reviewersPendingRemove.hasOwnProperty(type)) {
          if (!isCancel) {
            reviewerArr = this._reviewersPendingRemove[type];
            for (var i = 0; i < reviewerArr.length; i++) {
              if (!keep[reviewerArr[i]._account_id]) {
                this._removeAccount(reviewerArr[i], type);
              }
            }
          }
          this._reviewersPendingRemove[type] = [];
        }
      }
    },

    /**
     * Removes an account from the change, both on the backend and the client.
     * Does nothing if the account is a pending addition.
     *
     * @param {Object} account
     * @param {ReviewerTypes} type
     */
    _removeAccount: function(account, type) {
      if (account._pendingAdd) { return; }

      return this.$.restAPI.removeChangeReviewer(this.change._number,
          account._account_id).then(function(response) {
        if (!response.ok) { return response; }

        var reviewers = this.change.reviewers[type] || [];
        for (var i = 0; i < reviewers.length; i++) {
          if (reviewers[i]._account_id == account._account_id) {
            this.splice(['change', 'reviewers', type], i, 1);
            break;
          }
        }
      }.bind(this));
    },

    _mapReviewer: function(reviewer) {
      var reviewerId;
      var confirmed;
      if (reviewer.account) {
        reviewerId = reviewer.account._account_id;
      } else if (reviewer.group) {
        reviewerId = reviewer.group.id;
        confirmed = reviewer.group.confirmed;
      }
      return {reviewer: reviewerId, confirmed: confirmed};
    },

    send: function() {
      var obj = {
        drafts: 'PUBLISH_ALL_REVISIONS',
        labels: {},
      };

      for (var label in this.permittedLabels) {
        if (!this.permittedLabels.hasOwnProperty(label)) { continue; }

        var selectorEl = this.$$('iron-selector[data-label="' + label + '"]');

        // The user may have not voted on this label.
        if (!selectorEl || !selectorEl.selectedItem) { continue; }

        var selectedVal = selectorEl.selectedItem.getAttribute('data-value');
        selectedVal = parseInt(selectedVal, 10);

        // Only send the selection if the user changed it.
        var prevVal = this._getVoteForAccount(this.change.labels, label,
            this._account);
        if (prevVal !== null) {
          prevVal = parseInt(prevVal, 10);
        }
        if (selectedVal !== prevVal) {
          obj.labels[label] = selectedVal;
        }
      }
      if (this.draft != null) {
        obj.message = this.draft;
      }

      var accountAdditions = {};
      obj.reviewers = this.$.reviewers.additions().map(function(reviewer) {
        if (reviewer.account) {
          accountAdditions[reviewer.account._account_id] = true;
        }
        return this._mapReviewer(reviewer);
      }.bind(this));
      if (this.serverConfig.note_db_enabled) {
        this.$$('#ccs').additions().forEach(function(reviewer) {
          if (reviewer.account) {
            accountAdditions[reviewer.account._account_id] = true;
          }
          reviewer = this._mapReviewer(reviewer);
          reviewer.state = 'CC';
          obj.reviewers.push(reviewer);
        }.bind(this));
      }

      this.disabled = true;

      var errFn = this._handle400Error.bind(this);
      return this._saveReview(obj, errFn).then(function(response) {
        if (!response || !response.ok) {
          return response;
        }
        this.disabled = false;
        this.draft = '';
        this.fire('send', null, {bubbles: false});
        return accountAdditions;
      }.bind(this)).catch(function(err) {
        this.disabled = false;
        throw err;
      }.bind(this));
    },

    _focusOn: function(section) {
      if (section === FocusTarget.ANY) {
        section = this._chooseFocusTarget();
      }
      if (section === FocusTarget.BODY) {
        var textarea = this.$.textarea;
        textarea.async(textarea.textarea.focus.bind(textarea.textarea));
      } else if (section === FocusTarget.REVIEWERS) {
        var reviewerEntry = this.$.reviewers.focusStart;
        reviewerEntry.async(reviewerEntry.focus);
      } else if (section === FocusTarget.CCS) {
        var ccEntry = this.$$('#ccs').focusStart;
        ccEntry.async(ccEntry.focus);
      }
    },

    _chooseFocusTarget: function() {
      // If we are the owner and the reviewers field is empty, focus on that.
      if (this._account && this.change && this.change.owner &&
          this._account._account_id === this.change.owner._account_id &&
          (!this._reviewers || this._reviewers.length === 0)) {
        return FocusTarget.REVIEWERS;
      }

      // Default to BODY.
      return FocusTarget.BODY;
    },

    _handle400Error: function(response) {
      // A call to _saveReview could fail with a server error if erroneous
      // reviewers were requested. This is signalled with a 400 Bad Request
      // status. The default gr-rest-api-interface error handling would
      // result in a large JSON response body being displayed to the user in
      // the gr-error-manager toast.
      //
      // We can modify the error handling behavior by passing this function
      // through to restAPI as a custom error handling function. Since we're
      // short-circuiting restAPI we can do our own response parsing and fire
      // the server-error ourselves.
      //
      this.disabled = false;

      if (response.status !== 400) {
        // This is all restAPI does when there is no custom error handling.
        this.fire('server-error', {response: response});
        return response;
      }

      // Process the response body, format a better error message, and fire
      // an event for gr-event-manager to display.
      var jsonPromise = this.$.restAPI.getResponseObject(response);
      return jsonPromise.then(function(result) {
        var errors = [];
        ['reviewers', 'ccs'].forEach(function(state) {
          for (var input in result[state]) {
            var reviewer = result[state][input];
            if (!!reviewer.error) {
              errors.push(reviewer.error);
            }
          }
        });
        response = {
          ok: false,
          status: response.status,
          text: function() { return Promise.resolve(errors.join(', ')); },
        };
        this.fire('server-error', {response: response});
      }.bind(this));
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

    _computeLabels: function(labelRecord) {
      var labelsObj = labelRecord.base;
      if (!labelsObj) { return []; }
      return Object.keys(labelsObj).sort().map(function(key) {
        return {
          name: key,
          value: this._getVoteForAccount(labelsObj, key, this._account),
        };
      }.bind(this));
    },

    _getVoteForAccount: function(labels, labelName, account) {
      var votes = labels[labelName];
      if (votes.all && votes.all.length > 0) {
        for (var i = 0; i < votes.all.length; i++) {
          if (votes.all[i]._account_id == account._account_id) {
            return votes.all[i].value;
          }
        }
      }
      return null;
    },

    _computeIndexOfLabelValue: function(labels, permittedLabels, label) {
      if (!labels[label.name]) { return null; }
      var labelValue = label.value;
      var len = permittedLabels[label.name] != null ?
          permittedLabels[label.name].length : 0;
      for (var i = 0; i < len; i++) {
        var val = parseInt(permittedLabels[label.name][i], 10);
        if (val == labelValue) {
          return i;
        }
      }
      return null;
    },

    _computePermittedLabelValues: function(permittedLabels, label) {
      return permittedLabels[label];
    },

    _computeAnyPermittedLabelValues: function(permittedLabels, label) {
      return permittedLabels.hasOwnProperty(label);
    },

    _changeUpdated: function(changeRecord, owner, serverConfig) {
      this._rebuildReviewerArrays(changeRecord.base, owner, serverConfig);
    },

    _rebuildReviewerArrays: function(change, owner, serverConfig) {
      this._owner = owner;

      var reviewers = [];
      var ccs = [];

      for (var key in change) {
        if (key !== 'REVIEWER' && key !== 'CC') {
          console.warn('unexpected reviewer state:', key);
          continue;
        }
        change[key].forEach(function(entry) {
          if (entry._account_id === owner._account_id) {
            return;
          }
          switch (key) {
            case 'REVIEWER':
              reviewers.push(entry);
              break;
            case 'CC':
              ccs.push(entry);
              break;
          }
        });
      }

      if (serverConfig.note_db_enabled) {
        this._ccs = ccs;
      } else {
        this._ccs = [];
        reviewers = reviewers.concat(ccs);
      }
      this._reviewers = reviewers;
    },

    _accountOrGroupKey: function(entry) {
      return entry.id || entry._account_id;
    },

    _filterReviewerSuggestion: function(suggestion) {
      var entry;
      if (suggestion.account) {
        entry = suggestion.account;
      } else if (suggestion.group) {
        entry = suggestion.group;
      } else {
        console.warn('received suggestion that was neither account nor group:',
            suggestion);
      }
      if (entry._account_id === this._owner._account_id) {
        return false;
      }

      var key = this._accountOrGroupKey(entry);
      var finder = function(entry) {
        return this._accountOrGroupKey(entry) === key;
      }.bind(this);

      return this._reviewers.find(finder) === undefined &&
          this._ccs.find(finder) === undefined;
    },

    _getAccount: function() {
      return this.$.restAPI.getAccount();
    },

    _cancelTapHandler: function(e) {
      e.preventDefault();
      this.fire('cancel', null, {bubbles: false});
      this._purgeReviewersPendingRemove(true);
      this._rebuildReviewerArrays(this.change.reviewers, this._owner,
          this.serverConfig);
    },

    _sendTapHandler: function(e) {
      e.preventDefault();
      this.send().then(function(keep) {
        this._purgeReviewersPendingRemove(false, keep);
      }.bind(this));
    },

    _saveReview: function(review, opt_errFn) {
      return this.$.restAPI.saveChangeReview(this.change._number, this.patchNum,
          review, opt_errFn);
    },

    _reviewerPendingConfirmationUpdated: function(reviewer) {
      if (reviewer === null) {
        this.$.reviewerConfirmationOverlay.close();
      } else {
        this._pendingConfirmationDetails =
            this._ccPendingConfirmation || this._reviewerPendingConfirmation;
        this.$.reviewerConfirmationOverlay.open();
      }
    },

    _confirmPendingReviewer: function() {
      if (this._ccPendingConfirmation) {
        this.$$('#ccs').confirmGroup(this._ccPendingConfirmation.group);
        this._focusOn(FocusTarget.CCS);
      } else {
        this.$.reviewers.confirmGroup(this._reviewerPendingConfirmation.group);
        this._focusOn(FocusTarget.REVIEWERS);
      }
    },

    _cancelPendingReviewer: function() {
      this._ccPendingConfirmation = null;
      this._reviewerPendingConfirmation = null;

      var target =
          this._ccPendingConfirmation ? FocusTarget.CCS : FocusTarget.REVIEWERS;
      this._focusOn(target);
    },

    _getStorageLocation: function() {
      // Tests trigger this method without setting change.
      if (!this.change) { return {}; }
      return {
        changeNum: this.change._number,
        patchNum: this.patchNum,
        path: '@change',
      };
    },

    _loadStoredDraft: function() {
      var draft = this.$.storage.getDraftComment(this._getStorageLocation());
      return draft ? draft.message : '';
    },

    _draftChanged: function(newDraft, oldDraft) {
      this.debounce('store', function() {
        if (!newDraft.length && oldDraft) {
          // If the draft has been modified to be empty, then erase the storage
          // entry.
          this.$.storage.eraseDraftComment(this._getStorageLocation());
        } else if (newDraft.length) {
          this.$.storage.setDraftComment(this._getStorageLocation(),
              this.draft);
        }
      }, STORAGE_DEBOUNCE_INTERVAL_MS);
    },

    _handleHeightChanged: function(e) {
      // If the textarea resizes, we need to re-fit the overlay.
      this.debounce('autogrow', function() {
        this.fire('autogrow');
      });
    },
  });
})();
