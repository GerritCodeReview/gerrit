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
      _patchNum: String,
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
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
      Gerrit.RESTClientBehavior,
    ],

    ready: function() {
      app.accountReady.then(function() {
        this._loggedIn = app.loggedIn;
      }.bind(this));
      this._headerEl = this.$$('.header');
    },

    attached: function() {
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
        page.show(this._computeChangePath(this._changeNum));
        return;
      }
      page.show(this._computeChangePath(this._changeNum) + '/' + patchNum);
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
      this.$.replyDialog.reload().then(function() {
        this.async(function() { this.$.replyOverlay.center() }, 1);
      }.bind(this));
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
      if (value.view != this.tagName.toLowerCase()) { return; }

      this._changeNum = value.changeNum;
      this._patchNum = value.patchNum;
      if (this.viewState.changeNum != this._changeNum ||
          this.viewState.patchNum != this._patchNum) {
        this.set('viewState.selectedFileIndex', 0);
        this.set('viewState.changeNum', this._changeNum);
        this.set('viewState.patchNum', this._patchNum);
      }
      if (!this._changeNum) {
        return;
      }
      this._reload().then(function() {
        this.$.messageList.topMargin = this._headerEl.offsetHeight;

        // Allow the message list to render before scrolling.
        this.async(function() {
          var msgPrefix = '#message-';
          var hash = window.location.hash;
          if (hash.indexOf(msgPrefix) == 0) {
            this.$.messageList.scrollToMessage(hash.substr(msgPrefix.length));
          }
        }.bind(this), 1);

        app.accountReady.then(function() {
          if (!this._loggedIn) { return; }

          if (this.viewState.showReplyDialog) {
            this.$.replyOverlay.open();
            this.set('viewState.showReplyDialog', false);
          }
        }.bind(this));
      }.bind(this));
    },

    _changeChanged: function(change) {
      if (!change) { return; }
      this._patchNum = this._patchNum ||
          change.revisions[change.current_revision]._number;

      var title = change.subject + ' (' + change.change_id.substr(0, 9) + ')';
      this.fire('title-change', {title: title});
    },

    _computeChangePath: function(changeNum) {
      return '/c/' + changeNum;
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

    _computeDetailPath: function(changeNum) {
      return '/changes/' + changeNum + '/detail';
    },

    _computeCommitInfoPath: function(changeNum, patchNum) {
      return this.changeBaseURL(changeNum, patchNum) + '/commit?links';
    },

    _computeCommentsPath: function(changeNum) {
      return '/changes/' + changeNum + '/comments';
    },

    _computeProjectConfigPath: function(project) {
      return '/projects/' + encodeURIComponent(project) + '/config';
    },

    _computeDetailQueryParams: function() {
      var options = this.listChangesOptionsToHex(
          this.ListChangesOption.ALL_REVISIONS,
          this.ListChangesOption.CHANGE_ACTIONS,
          this.ListChangesOption.DOWNLOAD_COMMANDS
      );
      return {O: options};
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

    _handleKey: function(e) {
      if (this.shouldSupressKeyboardShortcut(e)) { return; }

      switch (e.keyCode) {
        case 65:  // 'a'
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
      page.show(this._computeChangePath(this._changeNum));
    },

    _reload: function() {
      var detailCompletes = this.$.detailXHR.generateRequest().completes;
      this.$.commentsXHR.generateRequest();
      var reloadPatchNumDependentResources = function() {
        return Promise.all([
          this.$.commitInfoXHR.generateRequest().completes,
          this.$.actions.reload(),
          this.$.fileList.reload(),
        ]);
      }.bind(this);
      var reloadDetailDependentResources = function() {
        return this.$.relatedChanges.reload();
      }.bind(this);

      this._resetHeaderEl();

      if (this._patchNum) {
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
