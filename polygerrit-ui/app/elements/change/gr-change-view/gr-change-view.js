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
    is: 'gr-change-view',

    /**
     * Fired when the title of the page should change.
     *
     * @event title-change
     */

    /**
     * Fired if an error occurs when fetching the change data.
     *
     * @event page-error
     */

    properties: {
      /**
       * URL params passed from the router.
       */
      params: {
        type: Object,
        observer: '_paramsChanged',
      },
      viewState: {
        type: Object,
        notify: true,
        value: function() { return {}; },
      },
      serverConfig: Object,
      keyEventTarget: {
        type: Object,
        value: function() { return document.body; },
      },

      _comments: Object,
      _change: {
        type: Object,
        observer: '_changeChanged',
      },
      _commitInfo: Object,
      _changeNum: String,
      _diffDrafts: {
        type: Object,
        value: function() { return {}; },
      },
      _editingCommitMessage: {
        type: Boolean,
        value: false,
      },
      _hideEditCommitMessage: {
        type: Boolean,
        computed: '_computeHideEditCommitMessage(_loggedIn, ' +
            '_editingCommitMessage)',
      },
      _latestCommitMessage: {
        type: String,
        value: '',
      },
      _patchRange: Object,
      _allPatchSets: {
        type: Array,
        computed: '_computeAllPatchSets(_change)',
      },
      _loggedIn: {
        type: Boolean,
        value: false,
      },
      _loading: Boolean,
      _projectConfig: Object,
      _replyButtonLabel: {
        type: String,
        value: 'Reply',
        computed: '_computeReplyButtonLabel(_diffDrafts.*)',
      },
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
      Gerrit.RESTClientBehavior,
    ],

    observers: [
      '_labelsChanged(_change.labels.*)',
      '_paramsAndChangeChanged(params, _change)',
    ],

    attached: function() {
      this._getLoggedIn().then(function(loggedIn) {
        this._loggedIn = loggedIn;
      }.bind(this));

      this.addEventListener('comment-save', this._handleCommentSave.bind(this));
      this.addEventListener('comment-discard',
          this._handleCommentDiscard.bind(this));
      this.addEventListener('editable-content-save',
          this._handleCommitMessageSave.bind(this));
      this.addEventListener('editable-content-cancel',
          this._handleCommitMessageCancel.bind(this));
    },

    _handleEditCommitMessage: function(e) {
      this._editingCommitMessage = true;
      this.$.commitMessageEditor.focusTextarea();
    },

    _handleCommitMessageSave: function(e) {
      var message = e.detail.content;

      this.$.commitMessageEditor.disabled = true;
      this._saveCommitMessage(message).then(function(resp) {
        this.$.commitMessageEditor.disabled = false;
        if (!resp.ok) { return; }

        this._latestCommitMessage = message;
        this._editingCommitMessage = false;
        this._reloadWindow();
      }.bind(this)).catch(function(err) {
        this.$.commitMessageEditor.disabled = false;
      }.bind(this));
    },

    _reloadWindow: function() {
      window.location.reload();
    },

    _handleCommitMessageCancel: function(e) {
      this._editingCommitMessage = false;
    },

    _saveCommitMessage: function(message) {
      return this.$.restAPI.saveChangeCommitMessageEdit(
          this._changeNum, message).then(function(resp) {
            if (!resp.ok) { return resp; }

            return this.$.restAPI.publishChangeEdit(this._changeNum);
          }.bind(this));
    },

    _computeHideEditCommitMessage: function(loggedIn, editing) {
      if (!loggedIn || editing) { return true; }

      return false;
    },

    _handleCommentSave: function(e) {
      if (!e.target.comment.__draft) { return; }

      var draft = e.target.comment;
      draft.patch_set = draft.patch_set || this._patchRange.patchNum;

      // The use of path-based notification helpers (set, push) can’t be used
      // because the paths could contain dots in them. A new object must be
      // created to satisfy Polymer’s dirty checking.
      // https://github.com/Polymer/polymer/issues/3127
      // TODO(andybons): Polyfill for Object.assign in IE.
      var diffDrafts = Object.assign({}, this._diffDrafts);
      if (!diffDrafts[draft.path]) {
        diffDrafts[draft.path] = [draft];
        this._diffDrafts = diffDrafts;
        return;
      }
      for (var i = 0; i < this._diffDrafts[draft.path].length; i++) {
        if (this._diffDrafts[draft.path][i].id === draft.id) {
          diffDrafts[draft.path][i] = draft;
          this._diffDrafts = diffDrafts;
          return;
        }
      }
      diffDrafts[draft.path].push(draft);
      diffDrafts[draft.path].sort(function(c1, c2) {
        // No line number means that it’s a file comment. Sort it above the
        // others.
        return (c1.line || -1) - (c2.line || -1);
      });
      this._diffDrafts = diffDrafts;
    },

    _handleCommentDiscard: function(e) {
      if (!e.target.comment.__draft) { return; }

      var draft = e.target.comment;
      if (!this._diffDrafts[draft.path]) {
        return;
      }
      var index = -1;
      for (var i = 0; i < this._diffDrafts[draft.path].length; i++) {
        if (this._diffDrafts[draft.path][i].id === draft.id) {
          index = i;
          break;
        }
      }
      if (index === -1) {
        // It may be a draft that hasn’t been added to _diffDrafts since it was
        // never saved.
        return;
      }

      draft.patch_set = draft.patch_set || this._patchRange.patchNum;

      // The use of path-based notification helpers (set, push) can’t be used
      // because the paths could contain dots in them. A new object must be
      // created to satisfy Polymer’s dirty checking.
      // https://github.com/Polymer/polymer/issues/3127
      // TODO(andybons): Polyfill for Object.assign in IE.
      var diffDrafts = Object.assign({}, this._diffDrafts);
      diffDrafts[draft.path].splice(index, 1);
      if (diffDrafts[draft.path].length === 0) {
        delete diffDrafts[draft.path];
      }
      this._diffDrafts = diffDrafts;
    },

    _handlePatchChange: function(e) {
      this._changePatchNum(parseInt(e.target.value, 10));
    },

    _handleReplyTap: function(e) {
      e.preventDefault();
      this._openReplyDialog();
    },

    _handleDownloadTap: function(e) {
      e.preventDefault();
      this.$.downloadOverlay.open();
    },

    _handleDownloadDialogClose: function(e) {
      this.$.downloadOverlay.close();
    },

    _handleMessageReply: function(e) {
      var msg = e.detail.message.message;
      var quoteStr = msg.split('\n').map(
          function(line) { return '> ' + line; }).join('\n') + '\n\n';
      this.$.replyDialog.draft += quoteStr;
      this._openReplyDialog();
    },

    _handleReplyOverlayOpen: function(e) {
      this.$.replyDialog.focus();
    },

    _handleReplySent: function(e) {
      this.$.replyOverlay.close();
      this._reload();
    },

    _handleReplyCancel: function(e) {
      this.$.replyOverlay.close();
    },

    _handleReplyAutogrow: function(e) {
      this.$.replyOverlay.refit();
    },

    _handleShowReplyDialog: function(e) {
      var target = this.$.replyDialog.FocusTarget.REVIEWERS;
      if (e.detail.value && e.detail.value.ccsOnly) {
        target = this.$.replyDialog.FocusTarget.CCS;
      }
      this._openReplyDialog(target);
    },

    _paramsChanged: function(value) {
      if (value.view !== this.tagName.toLowerCase()) { return; }

      this._changeNum = value.changeNum;
      this._patchRange = {
        patchNum: value.patchNum,
        basePatchNum: value.basePatchNum || 'PARENT',
      };

      this._reload().then(function() {
        // Allow the message list to render before scrolling.
        this.async(function() {
          this._maybeScrollToMessage();
        }.bind(this), 1);

        this._maybeShowReplyDialog();

        this.$.jsAPI.handleEvent(this.$.jsAPI.EventType.SHOW_CHANGE, {
          change: this._change,
          patchNum: this._patchRange.patchNum,
        });
      }.bind(this));
    },

    _paramsAndChangeChanged: function(value) {
      // If the change number or patch range is different, then reset the
      // selected file index.
      var patchRangeState = this.viewState.patchRange;
      if (this.viewState.changeNum !== this._changeNum ||
          patchRangeState.basePatchNum !== this._patchRange.basePatchNum ||
          patchRangeState.patchNum !== this._patchRange.patchNum) {
        this._resetFileListViewState();
      }
    },

    _maybeScrollToMessage: function() {
      var msgPrefix = '#message-';
      var hash = window.location.hash;
      if (hash.indexOf(msgPrefix) === 0) {
        this.$.messageList.scrollToMessage(hash.substr(msgPrefix.length));
      }
    },

    _maybeShowReplyDialog: function() {
      this._getLoggedIn().then(function(loggedIn) {
        if (!loggedIn) { return; }

        if (this.viewState.showReplyDialog) {
          this._openReplyDialog();
          this.async(function() { this.$.replyOverlay.center(); }, 1);
          this.set('viewState.showReplyDialog', false);
        }
      }.bind(this));
    },

    _resetFileListViewState: function() {
      this.set('viewState.selectedFileIndex', 0);
      this.set('viewState.changeNum', this._changeNum);
      this.set('viewState.patchRange', this._patchRange);
    },

    _changeChanged: function(change) {
      if (!change) { return; }
      this.set('_patchRange.basePatchNum',
          this._patchRange.basePatchNum || 'PARENT');
      this.set('_patchRange.patchNum',
          this._patchRange.patchNum ||
              this._computeLatestPatchNum(this._allPatchSets));

      var title = change.subject + ' (' + change.change_id.substr(0, 9) + ')';
      this.fire('title-change', {title: title});
    },

    /**
     * Change active patch to the provided patch num.
     * @param {int} patchNum the patchn number to be viewed.
     */
    _changePatchNum: function(patchNum) {
      var currentPatchNum;
      if (this._change.current_revision) {
        currentPatchNum =
            this._change.revisions[this._change.current_revision]._number;
      } else {
        currentPatchNum = this._computeLatestPatchNum(this._allPatchSets);
      }
      if (patchNum === currentPatchNum) {
        page.show(this.changePath(this._changeNum));
        return;
      }
      page.show(this.changePath(this._changeNum) + '/' + patchNum);
    },

    _computeChangePermalink: function(changeNum) {
      return '/' + changeNum;
    },

    _computeChangeStatus: function(change, patchNum) {
      var statusString;
      if (change.status === this.ChangeStatus.NEW) {
        var rev = this._getRevisionNumber(change, patchNum);
        if (rev && rev.draft === true) {
          statusString = 'Draft';
        }
      } else {
        statusString = this.changeStatusString(change);
      }
      return statusString ? '(' + statusString + ')' : '';
    },

    _computeLatestPatchNum: function(allPatchSets) {
      return allPatchSets[allPatchSets.length - 1];
    },

    _computePatchInfoClass: function(patchNum, allPatchSets) {
      if (parseInt(patchNum, 10) ===
          this._computeLatestPatchNum(allPatchSets)) {
        return '';
      }
      return 'patchInfo--oldPatchSet';
    },

    _computeAllPatchSets: function(change) {
      var patchNums = [];
      for (var rev in change.revisions) {
        patchNums.push(change.revisions[rev]._number);
      }
      return patchNums.sort(function(a, b) {
        return a - b;
      });
    },

    _getRevisionNumber: function(change, patchNum) {
      for (var rev in change.revisions) {
        if (change.revisions[rev]._number == patchNum) {
          return change.revisions[rev];
        }
      }
    },

    _computePatchIndexIsSelected: function(index, patchNum) {
      return this._allPatchSets[index] == patchNum;
    },

    _computeLabelNames: function(labels) {
      return Object.keys(labels).sort();
    },

    _computeLabelValues: function(labelName, labels) {
      var result = [];
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

    _computeReplyButtonLabel: function(changeRecord) {
      var drafts = (changeRecord && changeRecord.base) || {};
      var draftCount = Object.keys(drafts).reduce(function(count, file) {
        return count + drafts[file].length;
      }, 0);

      var label = 'Reply';
      if (draftCount > 0) {
        label += ' (' + draftCount + ')';
      }
      return label;
    },

    _switchToMostRecentPatchNum: function() {
      this._getChangeDetail().then(function() {
        var patchNum = this._allPatchSets[this._allPatchSets.length - 1];
        if (patchNum !== this._patchRange.patchNum) {
          this._changePatchNum(patchNum);
        }
      }.bind(this));
    },

    _handleKey: function(e) {
      if (this.shouldSupressKeyboardShortcut(e)) { return; }
      switch (e.keyCode) {
        case 65:  // 'a'
          if (this._loggedIn && !e.shiftKey) {
            e.preventDefault();
            this._openReplyDialog();
          }
          break;
        case 82: // 'r'
          if (e.shiftKey) {
            e.preventDefault();
            this._switchToMostRecentPatchNum();
          }
          break;
        case 85:  // 'u'
          e.preventDefault();
          page.show('/');
          break;
      }
    },

    _labelsChanged: function(changeRecord) {
      if (!changeRecord) { return; }
      this.$.jsAPI.handleEvent(this.$.jsAPI.EventType.LABEL_CHANGE, {
        change: this._change,
      });
    },

    _openReplyDialog: function(opt_section) {
      this.$.replyOverlay.open().then(function() {
        this.$.replyOverlay.setFocusStops(this.$.replyDialog.getFocusStops());
        this.$.replyDialog.open(opt_section);
      }.bind(this));
    },

    _handleReloadChange: function() {
      page.show(this.changePath(this._changeNum));
    },

    _handleGetChangeDetailError: function(response) {
      this.fire('page-error', {response: response});
    },

    _getDiffDrafts: function() {
      return this.$.restAPI.getDiffDrafts(this._changeNum).then(
          function(drafts) {
            return this._diffDrafts = drafts;
          }.bind(this));
    },

    _getLoggedIn: function() {
      return this.$.restAPI.getLoggedIn();
    },

    _getProjectConfig: function() {
      return this.$.restAPI.getProjectConfig(this._change.project).then(
          function(config) {
            this._projectConfig = config;
          }.bind(this));
    },

    _getChangeDetail: function() {
      return this.$.restAPI.getChangeDetail(this._changeNum,
          this._handleGetChangeDetailError.bind(this)).then(
              function(change) {
                // Issue 4190: Coalesce missing topics to null.
                if (!change.topic) { change.topic = null; }
                if (!change.reviewer_updates) {
                  change.reviewer_updates = null;
                }
                this._change = change;
              }.bind(this));
    },

    _getComments: function() {
      return this.$.restAPI.getDiffComments(this._changeNum).then(
          function(comments) {
            this._comments = comments;
          }.bind(this));
    },

    _getLatestCommitMessage: function() {
      return this.$.restAPI.getChangeCommitInfo(this._changeNum,
          this._computeLatestPatchNum(this._allPatchSets)).then(
              function(commitInfo) {
                this._latestCommitMessage = commitInfo.message;
              }.bind(this));
    },

    _getCommitInfo: function() {
      return this.$.restAPI.getChangeCommitInfo(
          this._changeNum, this._patchRange.patchNum).then(
              function(commitInfo) {
                this._commitInfo = commitInfo;
              }.bind(this));
    },

    _reloadDiffDrafts: function() {
      this._diffDrafts = {};
      this._getDiffDrafts().then(function() {
        if (this.$.replyOverlay.opened) {
          this.async(function() { this.$.replyOverlay.center(); }, 1);
        }
      }.bind(this));
    },

    _reload: function() {
      this._loading = true;

      this._getLoggedIn().then(function(loggedIn) {
        if (!loggedIn) { return; }

        this._reloadDiffDrafts();
      }.bind(this));

      var detailCompletes = this._getChangeDetail().then(function() {
        this._loading = false;
      }.bind(this));
      this._getComments();

      var reloadPatchNumDependentResources = function() {
        return Promise.all([
          this._getCommitInfo(),
          this.$.fileList.reload(),
        ]);
      }.bind(this);
      var reloadDetailDependentResources = function() {
        if (!this._change) { return Promise.resolve(); }

        return Promise.all([
          this._getLatestCommitMessage(),
          this.$.actions.reload(),
          this.$.relatedChanges.reload(),
          this._getProjectConfig(),
        ]);
      }.bind(this);

      if (this._patchRange.patchNum) {
        return reloadPatchNumDependentResources().then(function() {
          return detailCompletes;
        }).then(reloadDetailDependentResources);
      } else {
        // The patch number is reliant on the change detail request.
        return detailCompletes.then(reloadPatchNumDependentResources).then(
            reloadDetailDependentResources);
      }
    },
  });
})();
