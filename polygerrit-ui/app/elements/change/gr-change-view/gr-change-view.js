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
      _diffDrafts: Object,
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
      _headerContainerEl: Object,
      _headerEl: Object,
      _projectConfig: Object,
      _boundScrollHandler: {
        type: Function,
        value: function() { return this._handleBodyScroll.bind(this); },
      },
      _replyButtonLabel: {
        type: String,
        value: 'Reply',
        computed: '_computeReplyButtonLabel(_diffDrafts)',
      },
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
      Gerrit.RESTClientBehavior,
    ],

    ready: function() {
      this._headerEl = this.$$('.header');
    },

    attached: function() {
      this._getLoggedIn().then(function(loggedIn) {
        this._loggedIn = loggedIn;
      }.bind(this));

      window.addEventListener('scroll', this._boundScrollHandler);
    },

    detached: function() {
      window.removeEventListener('scroll', this._boundScrollHandler);
    },

    _handleBodyScroll: function(e) {
      var containerEl = this._headerContainerEl ||
          this.$$('.headerContainer');

      // Calculate where the header is relative to the window.
      var top = containerEl.offsetTop;
      for (var offsetParent = containerEl.offsetParent;
           offsetParent;
           offsetParent = offsetParent.offsetParent) {
        top += offsetParent.offsetTop;
      }
      // The element may not be displayed yet, in which case do nothing.
      if (top == 0) { return; }

      this._headerEl.classList.toggle('pinned', window.scrollY >= top);
    },

    _resetHeaderEl: function() {
      var el = this._headerEl || this.$$('.header');
      this._headerEl = el;
      el.classList.remove('pinned');
    },

    _handlePatchChange: function(e) {
      var patchNum = e.target.value;
      var currentPatchNum =
          this._change.revisions[this._change.current_revision]._number;
      if (patchNum == currentPatchNum) {
        page.show(this.changePath(this._changeNum));
        return;
      }
      page.show(this.changePath(this._changeNum) + '/' + patchNum);
    },

    _handleReplyTap: function(e) {
      e.preventDefault();
      this.$.replyOverlay.open();
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
      this.$.replyOverlay.open();
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

    _paramsChanged: function(value) {
      if (value.view !== this.tagName.toLowerCase()) { return; }

      this._changeNum = value.changeNum;
      this._patchRange = {
        patchNum: value.patchNum,
        basePatchNum: value.basePatchNum || 'PARENT',
      };

      // If the change number or patch range is different, then reset the
      // selected file index.
      var patchRangeState = this.viewState.patchRange;
      if (this.viewState.changeNum !== this._changeNum ||
          patchRangeState.basePatchNum !== this._patchRange.basePatchNum ||
          patchRangeState.patchNum !== this._patchRange.patchNum) {
        this._resetFileListViewState();
      }

      this._reload().then(function() {
        this.$.messageList.topMargin = this._headerEl.offsetHeight;
        this.$.fileList.topMargin = this._headerEl.offsetHeight;

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
          this.$.replyOverlay.open();
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
              change.revisions[change.current_revision]._number);

      var title = change.subject + ' (' + change.change_id.substr(0, 9) + ')';
      this.fire('title-change', {title: title});
    },

    _computeChangePermalink: function(changeNum) {
      return '/' + changeNum;
    },

    _computeChangeStatus: function(change, patchNum) {
      var status = change.status;
      if (status == this.ChangeStatus.NEW) {
        var rev = this._getRevisionNumber(change, patchNum);
        // TODO(davido): Figure out, why sometimes revision is not there
        if (rev == undefined || !rev.draft) { return ''; }
        status = this.ChangeStatus.DRAFT;
      }
      return '(' + status.toLowerCase() + ')';
    },

    _computeLatestPatchNum: function(change) {
      return change.revisions[change.current_revision]._number;
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

    _computeReplyButtonHighlighted: function(drafts) {
      return Object.keys(drafts || {}).length > 0;
    },

    _computeReplyButtonLabel: function(drafts) {
      drafts = drafts || {};
      var draftCount = Object.keys(drafts).reduce(function(count, file) {
        return count + drafts[file].length;
      }, 0);

      var label = 'Reply';
      if (draftCount > 0) {
        label += ' (' + draftCount + ')';
      }
      return label;
    },

    _handleKey: function(e) {
      if (this.shouldSupressKeyboardShortcut(e)) { return; }

      switch (e.keyCode) {
        case 65:  // 'a'
          if (!this._loggedIn) { return; }

          e.preventDefault();
          this.$.replyOverlay.open();
          break;
        case 85:  // 'u'
          e.preventDefault();
          page.show('/');
          break;
      }
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
                this._change = change;
              }.bind(this));
    },

    _getComments: function() {
      return this.$.restAPI.getDiffComments(this._changeNum).then(
          function(comments) {
            this._comments = comments;
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
          this.$.actions.reload(),
          this.$.fileList.reload(),
        ]);
      }.bind(this);
      var reloadDetailDependentResources = function() {
        if (!this._change) { return Promise.resolve(); }

        return Promise.all([
          this.$.relatedChanges.reload(),
          this._getProjectConfig(),
        ]);
      }.bind(this);

      this._resetHeaderEl();

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
