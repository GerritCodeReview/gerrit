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

  const CHANGE_ID_ERROR = {
    MISMATCH: 'mismatch',
    MISSING: 'missing',
  };
  const CHANGE_ID_REGEX_PATTERN = /^Change-Id\:\s(I[0-9a-f]{8,40})/gm;
  const COMMENT_SAVE = 'Saving... Try again after all comments are saved.';

  const MIN_LINES_FOR_COMMIT_COLLAPSE = 30;
  const DEFAULT_NUM_FILES_SHOWN = 200;

  // Maximum length for patch set descriptions.
  const PATCH_DESC_MAX_LENGTH = 500;
  const REVIEWERS_REGEX = /^R=/gm;
  const MIN_CHECK_INTERVAL_SECS = 0;

  // These are the same as the breakpoint set in CSS. Make sure both are changed
  // together.
  const BREAKPOINT_RELATED_SMALL = '50em';
  const BREAKPOINT_RELATED_MED = '60em';

  // In the event that the related changes medium width calculation is too close
  // to zero, provide some height.
  const MINIMUM_RELATED_MAX_HEIGHT = 100;

  const SMALL_RELATED_HEIGHT = 400;

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

    /**
     * Fired if being logged in is required.
     *
     * @event show-auth-required
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
        value() { return {}; },
      },
      backPage: String,
      hasParent: Boolean,
      serverConfig: {
        type: Object,
        observer: '_startUpdateCheckTimer',
      },
      keyEventTarget: {
        type: Object,
        value() { return document.body; },
      },
      _diffPrefs: Object,
      _numFilesShown: {
        type: Number,
        observer: '_numFilesShownChanged',
      },
      _account: {
        type: Object,
        value: {},
      },
      _canStartReview: {
        type: Boolean,
        computed: '_computeCanStartReview(_loggedIn, _change, _account)',
      },
      _comments: Object,
      _change: {
        type: Object,
        observer: '_changeChanged',
      },
      _commitInfo: Object,
      _files: Object,
      _changeNum: String,
      _diffDrafts: {
        type: Object,
        value() { return {}; },
      },
      _editingCommitMessage: {
        type: Boolean,
        value: false,
      },
      _hideEditCommitMessage: {
        type: Boolean,
        computed: '_computeHideEditCommitMessage(_loggedIn, ' +
            '_editingCommitMessage, _change)',
      },
      _latestCommitMessage: {
        type: String,
        value: '',
      },
      _lineHeight: Number,
      _changeIdCommitMessageError: {
        type: String,
        computed:
          '_computeChangeIdCommitMessageError(_latestCommitMessage, _change)',
      },
      _patchRange: {
        type: Object,
        observer: '_updateSelected',
      },
      _relatedChangesLoading: {
        type: Boolean,
        value: true,
      },
      _currentRevisionActions: Object,
      _allPatchSets: {
        type: Array,
        computed: 'computeAllPatchSets(_change, _change.revisions.*)',
      },
      _loggedIn: {
        type: Boolean,
        value: false,
      },
      _loading: Boolean,
      _projectConfig: Object,
      _rebaseOnCurrent: Boolean,
      _replyButtonLabel: {
        type: String,
        value: 'Reply',
        computed: '_computeReplyButtonLabel(_diffDrafts.*, _canStartReview)',
      },
      _selectedPatchSet: String,
      _initialLoadComplete: {
        type: Boolean,
        value: false,
      },
      _descriptionReadOnly: {
        type: Boolean,
        computed: '_computeDescriptionReadOnly(_loggedIn, _change, _account)',
      },
      _replyDisabled: {
        type: Boolean,
        value: true,
        computed: '_computeReplyDisabled(serverConfig)',
      },
      _changeStatus: {
        type: String,
        computed: '_computeChangeStatus(_change, _patchRange.patchNum)',
      },
      _commitCollapsed: {
        type: Boolean,
        value: true,
      },
      _relatedChangesCollapsed: {
        type: Boolean,
        value: true,
      },
      _updateCheckTimerHandle: Number,
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.KeyboardShortcutBehavior,
      Gerrit.PatchSetBehavior,
      Gerrit.RESTClientBehavior,
    ],

    observers: [
      '_labelsChanged(_change.labels.*)',
      '_paramsAndChangeChanged(params, _change)',
    ],

    keyBindings: {
      'shift+r': '_handleCapitalRKey',
      'a': '_handleAKey',
      'd': '_handleDKey',
      's': '_handleSKey',
      'u': '_handleUKey',
      'x': '_handleXKey',
      'z': '_handleZKey',
      ',': '_handleCommaKey',
    },

    attached() {
      this._getLoggedIn().then(loggedIn => {
        this._loggedIn = loggedIn;
        if (loggedIn) {
          this.$.restAPI.getAccount().then(acct => {
            this._account = acct;
          });
        }
      });

      this._numFilesShown = this.viewState.numFilesShown ?
          this.viewState.numFilesShown : DEFAULT_NUM_FILES_SHOWN;

      this.addEventListener('comment-save', this._handleCommentSave.bind(this));
      this.addEventListener('comment-discard',
          this._handleCommentDiscard.bind(this));
      this.addEventListener('editable-content-save',
          this._handleCommitMessageSave.bind(this));
      this.addEventListener('editable-content-cancel',
          this._handleCommitMessageCancel.bind(this));
      this.listen(window, 'scroll', '_handleScroll');
      this.listen(document, 'visibilitychange', '_handleVisibilityChange');
    },

    detached() {
      this.unlisten(window, 'scroll', '_handleScroll');
      this.unlisten(document, 'visibilitychange', '_handleVisibilityChange');

      if (this._updateCheckTimerHandle) {
        this._cancelUpdateCheckTimer();
      }
    },

    _computePrefsButtonHidden(prefs, loggedIn) {
      return !loggedIn || !prefs;
    },

    _handleEditCommitMessage(e) {
      this._editingCommitMessage = true;
      this.$.commitMessageEditor.focusTextarea();
    },

    _handleCommitMessageSave(e) {
      const message = e.detail.content;

      this.$.jsAPI.handleCommitMessage(this._change, message);

      this.$.commitMessageEditor.disabled = true;
      this._saveCommitMessage(message).then(resp => {
        this.$.commitMessageEditor.disabled = false;
        if (!resp.ok) { return; }

        this._latestCommitMessage = this._prepareCommitMsgForLinkify(message);
        this._editingCommitMessage = false;
        this._reloadWindow();
      }).catch(err => {
        this.$.commitMessageEditor.disabled = false;
      });
    },

    _reloadWindow() {
      window.location.reload();
    },

    _handleCommitMessageCancel(e) {
      this._editingCommitMessage = false;
    },

    _saveCommitMessage(message) {
      return this.$.restAPI.saveChangeCommitMessageEdit(
          this._changeNum, message).then(resp => {
            if (!resp.ok) { return resp; }

            return this.$.restAPI.publishChangeEdit(this._changeNum);
          });
    },

    _computeHideEditCommitMessage(loggedIn, editing, change) {
      if (!loggedIn || editing || change.status === this.ChangeStatus.MERGED) {
        return true;
      }

      return false;
    },

    _handlePrefsTap(e) {
      e.preventDefault();
      this.$.fileList.openDiffPrefs();
    },

    _handleCommentSave(e) {
      if (!e.target.comment.__draft) { return; }

      const draft = e.target.comment;
      draft.patch_set = draft.patch_set || this._patchRange.patchNum;

      // The use of path-based notification helpers (set, push) can’t be used
      // because the paths could contain dots in them. A new object must be
      // created to satisfy Polymer’s dirty checking.
      // https://github.com/Polymer/polymer/issues/3127
      const diffDrafts = Object.assign({}, this._diffDrafts);
      if (!diffDrafts[draft.path]) {
        diffDrafts[draft.path] = [draft];
        this._diffDrafts = diffDrafts;
        return;
      }
      for (let i = 0; i < this._diffDrafts[draft.path].length; i++) {
        if (this._diffDrafts[draft.path][i].id === draft.id) {
          diffDrafts[draft.path][i] = draft;
          this._diffDrafts = diffDrafts;
          return;
        }
      }
      diffDrafts[draft.path].push(draft);
      diffDrafts[draft.path].sort((c1, c2) => {
        // No line number means that it’s a file comment. Sort it above the
        // others.
        return (c1.line || -1) - (c2.line || -1);
      });
      this._diffDrafts = diffDrafts;
    },

    _handleCommentDiscard(e) {
      if (!e.target.comment.__draft) { return; }

      const draft = e.target.comment;
      if (!this._diffDrafts[draft.path]) {
        return;
      }
      let index = -1;
      for (let i = 0; i < this._diffDrafts[draft.path].length; i++) {
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
      const diffDrafts = Object.assign({}, this._diffDrafts);
      diffDrafts[draft.path].splice(index, 1);
      if (diffDrafts[draft.path].length === 0) {
        delete diffDrafts[draft.path];
      }
      this._diffDrafts = diffDrafts;
    },

    _handlePatchChange(e) {
      if (e.target.value === 'edit') {
        return 0;
      }
      this._changePatchNum(parseInt(e.target.value, 10), true);
    },

    _handleReplyTap(e) {
      e.preventDefault();
      this._openReplyDialog();
    },

    _handleDownloadTap(e) {
      e.preventDefault();
      this.$.downloadOverlay.open().then(() => {
        this.$.downloadOverlay
            .setFocusStops(this.$.downloadDialog.getFocusStops());
        this.$.downloadDialog.focus();
      });
    },

    _handleDownloadDialogClose(e) {
      this.$.downloadOverlay.close();
    },

    _handleMessageReply(e) {
      const msg = e.detail.message.message;
      const quoteStr = msg.split('\n').map(
          line => { return '> ' + line; }).join('\n') + '\n\n';

      if (quoteStr !== this.$.replyDialog.quote) {
        this.$.replyDialog.draft = quoteStr;
      }
      this.$.replyDialog.quote = quoteStr;
      this._openReplyDialog();
    },

    _handleReplyOverlayOpen(e) {
      // This is needed so that focus is not set on the reply overlay
      // when the suggestion overaly from gr-autogrow-textarea opens.
      if (e.target === this.$.replyOverlay) {
        this.$.replyDialog.focus();
      }
    },

    _handleReplySent(e) {
      this.$.replyOverlay.close();
      this._reload();
    },

    _handleReplyCancel(e) {
      this.$.replyOverlay.close();
    },

    _handleReplyAutogrow(e) {
      this.$.replyOverlay.refit();
    },

    _handleShowReplyDialog(e) {
      let target = this.$.replyDialog.FocusTarget.REVIEWERS;
      if (e.detail.value && e.detail.value.ccsOnly) {
        target = this.$.replyDialog.FocusTarget.CCS;
      }
      this._openReplyDialog(target);
    },

    _handleScroll() {
      this.debounce('scroll', () => {
        this.viewState.scrollTop = document.body.scrollTop;
      }, 150);
    },

    _paramsChanged(value) {
      if (value.view !== this.tagName.toLowerCase()) {
        this._initialLoadComplete = false;
        return;
      }

      const patchChanged = this._patchRange &&
          (value.patchNum !== undefined && value.basePatchNum !== undefined) &&
          (this._patchRange.patchNum !== value.patchNum ||
          this._patchRange.basePatchNum !== value.basePatchNum);

      if (this._changeNum !== value.changeNum) {
        this._initialLoadComplete = false;
      }

      const patchRange = {
        patchNum: value.patchNum,
        basePatchNum: value.basePatchNum || 'PARENT',
      };

      if (this._initialLoadComplete && patchChanged) {
        if (patchRange.patchNum == null) {
          patchRange.patchNum = this.computeLatestPatchName(this._allPatchSets);
        }
        this._patchRange = patchRange;
        this._reloadPatchNumDependentResources().then(() => {
          this.$.jsAPI.handleEvent(this.$.jsAPI.EventType.SHOW_CHANGE, {
            change: this._change,
            patchNum: patchRange.patchNum,
          });
        });
        return;
      }

      this._changeNum = value.changeNum;
      this._patchRange = patchRange;
      this.$.relatedChanges.clear();

      this._reload().then(() => {
        this._performPostLoadTasks();
      });
    },

    _performPostLoadTasks() {
      // Allow the message list and related changes to render before scrolling.
      // Related changes are loaded here (after everything else) because they
      // take the longest and are secondary information. Because the element may
      // alter the total height of the page, the call to potentially scroll to
      // a linked message is performed after related changes is fully loaded.
      this.$.relatedChanges.reload().then(() => {
        this.async(() => {
          if (this.viewState.scrollTop) {
            document.documentElement.scrollTop =
                document.body.scrollTop = this.viewState.scrollTop;
          } else {
            this._maybeScrollToMessage(window.location.hash);
          }
        }, 1);
      });

      this._maybeShowReplyDialog();

      this._maybeShowRevertDialog();

      this.$.jsAPI.handleEvent(this.$.jsAPI.EventType.SHOW_CHANGE, {
        change: this._change,
        patchNum: this._patchRange.patchNum,
      });

      this._initialLoadComplete = true;
    },

    _paramsAndChangeChanged(value) {
      // If the change number or patch range is different, then reset the
      // selected file index.
      const patchRangeState = this.viewState.patchRange;
      if (this.viewState.changeNum !== this._changeNum ||
          patchRangeState.basePatchNum !== this._patchRange.basePatchNum ||
          patchRangeState.patchNum !== this._patchRange.patchNum) {
        this._resetFileListViewState();
      }
    },

    _numFilesShownChanged(numFilesShown) {
      this.viewState.numFilesShown = numFilesShown;
    },

    _maybeScrollToMessage(hash) {
      const msgPrefix = '#message-';
      if (hash.startsWith(msgPrefix)) {
        this.$.messageList.scrollToMessage(hash.substr(msgPrefix.length));
      }
    },

    _getLocationSearch() {
      // Not inlining to make it easier to test.
      return window.location.search;
    },

    _getUrlParameter(param) {
      const pageURL = this._getLocationSearch().substring(1);
      const vars = pageURL.split('&');
      for (let i = 0; i < vars.length; i++) {
        const name = vars[i].split('=');
        if (name[0] == param) {
          return name[0];
        }
      }
      return null;
    },

    _maybeShowRevertDialog() {
      Gerrit.awaitPluginsLoaded()
          .then(this._getLoggedIn.bind(this))
          .then(loggedIn => {
            if (!loggedIn || this._change.status !== this.ChangeStatus.MERGED) {
            // Do not display dialog if not logged-in or the change is not
            // merged.
              return;
            }
            if (this._getUrlParameter('revert')) {
              this.$.actions.showRevertDialog();
            }
          });
    },

    _maybeShowReplyDialog() {
      this._getLoggedIn().then(loggedIn => {
        if (!loggedIn) { return; }

        if (this.viewState.showReplyDialog) {
          this._openReplyDialog();
          // TODO(kaspern@): Find a better signal for when to call center.
          this.async(() => { this.$.replyOverlay.center(); }, 100);
          this.async(() => { this.$.replyOverlay.center(); }, 1000);
          this.set('viewState.showReplyDialog', false);
        }
      });
    },

    _resetFileListViewState() {
      this.set('viewState.selectedFileIndex', 0);
      this.set('viewState.scrollTop', 0);
      if (!!this.viewState.changeNum &&
          this.viewState.changeNum !== this._changeNum) {
        // Reset the diff mode to null when navigating from one change to
        // another, so that the user's preference is restored.
        this.set('viewState.diffMode', null);
        this.set('_numFilesShown', DEFAULT_NUM_FILES_SHOWN);
      }
      this.set('viewState.changeNum', this._changeNum);
      this.set('viewState.patchRange', this._patchRange);
    },

    _changeChanged(change) {
      if (!change || !this._patchRange || !this._allPatchSets) { return; }
      this.set('_patchRange.basePatchNum',
          this._patchRange.basePatchNum || 'PARENT');
      this.set('_patchRange.patchNum',
          this._patchRange.patchNum ||
              this.computeStrictLatestPatchNum(this._allPatchSets));

      this._updateSelected();

      const title = change.subject + ' (' + change.change_id.substr(0, 9) + ')';
      this.fire('title-change', {title});
    },

    /**
     * Change active patch to the provided patch num.
     * @param {number} patchNum the patchn number to be viewed.
     * @param {boolean} opt_forceParams When set to true, the resulting URL will
     *     always include the patch range, even if the requested patchNum is
     *     known to be the latest.
     */
    _changePatchNum(patchNum, opt_forceParams) {
      if (!opt_forceParams) {
        let currentPatchNum;
        if (this._change.current_revision) {
          currentPatchNum =
              this._change.revisions[this._change.current_revision]._number;
        } else {
          currentPatchNum = this.computeLatestPatchNum(this._allPatchSets);
        }
        if (patchNum === currentPatchNum &&
            this._patchRange.basePatchNum === 'PARENT') {
          page.show(this.changePath(this._changeNum));
          return;
        }
      }
      const patchExpr = this._patchRange.basePatchNum === 'PARENT' ? patchNum :
          this._patchRange.basePatchNum + '..' + patchNum;
      page.show(this.changePath(this._changeNum) + '/' + patchExpr);
    },

    _computeChangePermalink(changeNum) {
      return this.getBaseUrl() + '/' + changeNum;
    },

    _computeChangeStatus(change, patchNum) {
      let statusString = this.changeStatusString(change);
      if (change.status === this.ChangeStatus.NEW) {
        const rev = this.getRevisionByPatchNum(change.revisions, patchNum);
        if (rev && rev.draft === true) {
          statusString = 'Draft';
        }
      }
      return statusString;
    },

    _privateChanges(change) {
      return change.is_private ? ' (Private)' : '';
    },

    _computeShowCommitInfo(changeStatus, current_revision) {
      return changeStatus === 'Merged' && current_revision;
    },

    _computeMergedCommitInfo(current_revision, revisions) {
      const rev = revisions[current_revision];
      if (!rev || !rev.commit) { return {}; }
      // CommitInfo.commit is optional. Set commit in all cases to avoid error
      // in <gr-commit-info>. @see Issue 5337
      if (!rev.commit.commit) { rev.commit.commit = current_revision; }
      return rev.commit;
    },

    _computeChangeIdClass(displayChangeId) {
      return displayChangeId === CHANGE_ID_ERROR.MISMATCH ? 'warning' : '';
    },

    _computeTitleAttributeWarning(displayChangeId) {
      if (displayChangeId === CHANGE_ID_ERROR.MISMATCH) {
        return 'Change-Id mismatch';
      } else if (displayChangeId === CHANGE_ID_ERROR.MISSING) {
        return 'No Change-Id in commit message';
      }
    },

    _computeChangeIdCommitMessageError(commitMessage, change) {
      if (!commitMessage) { return CHANGE_ID_ERROR.MISSING; }

      // Find the last match in the commit message:
      let changeId;
      let changeIdArr;

      while (changeIdArr = CHANGE_ID_REGEX_PATTERN.exec(commitMessage)) {
        changeId = changeIdArr[1];
      }

      if (changeId) {
        // A change-id is detected in the commit message.

        if (changeId === change.change_id) {
          // The change-id found matches the real change-id.
          return null;
        }
        // The change-id found does not match the change-id.
        return CHANGE_ID_ERROR.MISMATCH;
      }
      // There is no change-id in the commit message.
      return CHANGE_ID_ERROR.MISSING;
    },

    _computePatchInfoClass(patchNum, allPatchSets) {
      if (parseInt(patchNum, 10) ===
          this.computeLatestPatchNum(allPatchSets)) {
        return '';
      }
      return 'patchInfo--oldPatchSet';
    },

    /**
     * Determines if a patch number should be disabled based on value of the
     * basePatchNum from gr-file-list.
     * @param {Number} patchNum Patch number available in dropdown
     * @param {Number|String} basePatchNum Base patch number from file list
     * @return {Boolean}
     */
    _computePatchSetDisabled(patchNum, basePatchNum) {
      basePatchNum = basePatchNum === 'PARENT' ? 0 : basePatchNum;
      return parseInt(patchNum, 10) <= parseInt(basePatchNum, 10);
    },

    _computeLabelNames(labels) {
      return Object.keys(labels).sort();
    },

    _computeLabelValues(labelName, labels) {
      const result = [];
      const t = labels[labelName];
      if (!t) { return result; }
      const approvals = t.all || [];
      for (label of approvals) {
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

    _computeReplyButtonLabel(changeRecord, canStartReview) {
      if (canStartReview) {
        return 'Start review';
      }

      const drafts = (changeRecord && changeRecord.base) || {};
      const draftCount = Object.keys(drafts).reduce((count, file) => {
        return count + drafts[file].length;
      }, 0);

      let label = 'Reply';
      if (draftCount > 0) {
        label += ' (' + draftCount + ')';
      }
      return label;
    },

    _handleAKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) {
        return;
      }
      this._getLoggedIn().then(isLoggedIn => {
        if (!isLoggedIn) {
          this.fire('show-auth-required');
          return;
        }

        e.preventDefault();
        this._openReplyDialog();
      });
    },

    _handleDKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this.$.downloadOverlay.open();
    },

    _handleCapitalRKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }
      e.preventDefault();
      page.show('/c/' + this._change._number);
    },

    _handleSKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this.$.changeStar.toggleStar();
    },

    _handleUKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      e.preventDefault();
      this._determinePageBack();
    },

    _handleXKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this.$.messageList.handleExpandCollapse(true);
    },

    _handleZKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this.$.messageList.handleExpandCollapse(false);
    },

    _handleCommaKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this.$.fileList.openDiffPrefs();
    },

    _determinePageBack() {
      // Default backPage to '/' if user came to change view page
      // via an email link, etc.
      page.show(this.backPage || '/');
    },

    _handleLabelRemoved(splices, path) {
      for (const splice of splices) {
        for (const removed of splice.removed) {
          const changePath = path.split('.');
          const labelPath = changePath.splice(0, changePath.length - 2);
          const labelDict = this.get(labelPath);
          if (labelDict.approved &&
              labelDict.approved._account_id === removed._account_id) {
            this._reload();
            return;
          }
        }
      }
    },

    _labelsChanged(changeRecord) {
      if (!changeRecord) { return; }
      if (changeRecord.value && changeRecord.value.indexSplices) {
        this._handleLabelRemoved(changeRecord.value.indexSplices,
            changeRecord.path);
      }
      this.$.jsAPI.handleEvent(this.$.jsAPI.EventType.LABEL_CHANGE, {
        change: this._change,
      });
    },

    _openReplyDialog(opt_section) {
      if (this.$.restAPI.hasPendingDiffDrafts()) {
        this.dispatchEvent(new CustomEvent('show-alert',
            {detail: {message: COMMENT_SAVE}, bubbles: true}));
        return;
      }
      this.$.replyOverlay.open().then(() => {
        this.$.replyOverlay.setFocusStops(this.$.replyDialog.getFocusStops());
        this.$.replyDialog.open(opt_section);
        Polymer.dom.flush();
        this.$.replyOverlay.center();
      });
    },

    _handleReloadChange(e) {
      return this._reload().then(() => {
        // If the change was rebased, we need to reload the page with the
        // latest patch.
        if (e.detail.action === 'rebase') {
          page.show(this.changePath(this._changeNum));
        }
      });
    },

    _handleGetChangeDetailError(response) {
      this.fire('page-error', {response});
    },

    _getDiffDrafts() {
      return this.$.restAPI.getDiffDrafts(this._changeNum).then(
          drafts => {
            return this._diffDrafts = drafts;
          });
    },

    _getLoggedIn() {
      return this.$.restAPI.getLoggedIn();
    },

    _getProjectConfig() {
      return this.$.restAPI.getProjectConfig(this._change.project).then(
          config => {
            this._projectConfig = config;
          });
    },

    _updateRebaseAction(revisionActions) {
      if (revisionActions && revisionActions.rebase) {
        revisionActions.rebase.rebaseOnCurrent =
            !!revisionActions.rebase.enabled;
        revisionActions.rebase.enabled = true;
      }
      return revisionActions;
    },

    _prepareCommitMsgForLinkify(msg) {
      // TODO(wyatta) switch linkify sequence, see issue 5526.
      // This is a zero-with space. It is added to prevent the linkify library
      // from including R= as part of the email address.
      return msg.replace(REVIEWERS_REGEX, 'R=\u200B');
    },

    _getChangeDetail() {
      const detailCompletes = this.$.restAPI.getChangeDetail(
          this._changeNum, this._handleGetChangeDetailError.bind(this));
      const editCompletes = this._getEdit();

      return Promise.all([detailCompletes, editCompletes]).then(r => {
        const [change, edit] = r;
        if (!change) {
          return '';
        }
        if (edit) {
          change.revisions[edit.commit.commit] = {
            _number: 0,
            basePatchSetNumber: edit.base_patch_set_number,
            commit: edit.commit,
            fetch: edit.fetch,
          };
        }

        // Issue 4190: Coalesce missing topics to null.
        if (!change.topic) {
          change.topic = null;
        }
        if (!change.reviewer_updates) {
          change.reviewer_updates = null;
        }
        const latestRevisionSha = this._getLatestRevisionSHA(change);
        const currentRevision = change.revisions[latestRevisionSha];
        if (currentRevision.commit && currentRevision.commit.message) {
          this._latestCommitMessage = this._prepareCommitMsgForLinkify(
              currentRevision.commit.message);
        } else {
          this._latestCommitMessage = null;
        }
        const lineHeight = getComputedStyle(this).lineHeight;
        this._lineHeight = lineHeight.slice(0, lineHeight.length - 2);

        this._change = change;
        if (!this._patchRange || !this._patchRange.patchNum ||
            this._patchRange.patchNum === currentRevision._number) {
          // CommitInfo.commit is optional, and may need patching.
          if (!currentRevision.commit.commit) {
            currentRevision.commit.commit = latestRevisionSha;
          }
          this._commitInfo = currentRevision.commit;
          this._currentRevisionActions =
              this._updateRebaseAction(currentRevision.actions);
          // TODO: Fetch and process files.
        }
      });
    },

    _getComments() {
      return this.$.restAPI.getDiffComments(this._changeNum).then(
          comments => {
            this._comments = comments;
          });
    },

    _getEdit() {
      return this.$.restAPI.getChangeEdit(this._changeNum, true);
    },

    _getLatestCommitMessage() {
      return this.$.restAPI.getChangeCommitInfo(this._changeNum,
          this.computeLatestPatchNum(this._allPatchSets)).then(
          commitInfo => {
            this._latestCommitMessage =
                    this._prepareCommitMsgForLinkify(commitInfo.message);
          });
    },

    _getLatestRevisionSHA(change) {
      if (change.current_revision) {
        return change.current_revision;
      }
      // current_revision may not be present in the case where the latest rev is
      // a draft and the user doesn’t have permission to view that rev.
      let latestRev = null;
      let latestPatchNum = -1;
      for (const rev in change.revisions) {
        if (!change.revisions.hasOwnProperty(rev)) { continue; }

        if (change.revisions[rev]._number > latestPatchNum) {
          latestRev = rev;
          latestPatchNum = change.revisions[rev]._number;
        }
      }
      return latestRev;
    },

    _getCommitInfo() {
      return this.$.restAPI.getChangeCommitInfo(
          this._changeNum, this._patchRange.patchNum).then(
          commitInfo => {
            this._commitInfo = commitInfo;
          });
    },

    _reloadDiffDrafts() {
      this._diffDrafts = {};
      this._getDiffDrafts().then(() => {
        if (this.$.replyOverlay.opened) {
          this.async(() => { this.$.replyOverlay.center(); }, 1);
        }
      });
    },

    _reload() {
      this._loading = true;
      this._relatedChangesCollapsed = true;

      this._getLoggedIn().then(loggedIn => {
        if (!loggedIn) { return; }

        this._reloadDiffDrafts();
      });

      const detailCompletes = this._getChangeDetail().then(() => {
        this._loading = false;
        this._getProjectConfig();
      });
      this._getComments();

      if (this._patchRange.patchNum) {
        return Promise.all([
          this._reloadPatchNumDependentResources(),
          detailCompletes,
        ]).then(() => {
          return this.$.actions.reload();
        });
      } else {
        // The patch number is reliant on the change detail request.
        return detailCompletes.then(() => {
          this.$.fileList.reload();
          if (!this._latestCommitMessage) {
            this._getLatestCommitMessage();
          }
        });
      }
    },

    /**
     * Kicks off requests for resources that rely on the patch range
     * (`this._patchRange`) being defined.
     */
    _reloadPatchNumDependentResources() {
      return Promise.all([
        this._getCommitInfo(),
        this.$.fileList.reload(),
      ]);
    },

    _updateSelected() {
      this._selectedPatchSet = this._patchRange.patchNum;
    },

    _computePatchSetDescription(change, patchNum) {
      const rev = this.getRevisionByPatchNum(change.revisions, patchNum);
      return (rev && rev.description) ?
          rev.description.substring(0, PATCH_DESC_MAX_LENGTH) : '';
    },

    _computePatchSetCommentsString(allComments, patchNum) {
      let numComments = 0;
      let numUnresolved = 0;
      for (const file in allComments) {
        if (allComments.hasOwnProperty(file)) {
          numComments += this.$.fileList.getCommentsForPath(
              allComments, patchNum, file).length;
          numUnresolved += this.$.fileList.computeUnresolvedNum(
              allComments, {}, patchNum, file);
        }
      }
      let commentsStr = '';
      if (numComments > 0) {
        commentsStr = '(' + numComments + ' comments';
        if (numUnresolved > 0) {
          commentsStr += ', ' + numUnresolved + ' unresolved';
        }
        commentsStr += ')';
      }
      return commentsStr;
    },

    _computeDescriptionPlaceholder(readOnly) {
      return (readOnly ? 'No' : 'Add a') + ' patch set description';
    },

    _handleDescriptionChanged(e) {
      const desc = e.detail.trim();
      const rev = this.getRevisionByPatchNum(this._change.revisions,
          this._selectedPatchSet);
      const sha = this._getPatchsetHash(this._change.revisions, rev);
      this.$.restAPI.setDescription(this._changeNum,
          this._selectedPatchSet, desc)
          .then(res => {
            if (res.ok) {
              this.set(['_change', 'revisions', sha, 'description'], desc);
            }
          });
    },


    /**
     * @param {Object} revisions The revisions object keyed by revision hashes
     * @param {Object} patchSet A revision already fetched from {revisions}
     * @return {string} the SHA hash corresponding to the revision.
     */
    _getPatchsetHash(revisions, patchSet) {
      for (const rev in revisions) {
        if (revisions.hasOwnProperty(rev) &&
            revisions[rev] === patchSet) {
          return rev;
        }
      }
    },

    _computeCanStartReview(loggedIn, change, account) {
      return !!(loggedIn && change.work_in_progress &&
          change.owner._account_id === account._account_id);
    },

    _computeDescriptionReadOnly(loggedIn, change, account) {
      return !(loggedIn && (account._account_id === change.owner._account_id));
    },

    _computeReplyDisabled() { return false; },

    _computeChangePermalinkAriaLabel(changeNum) {
      return 'Change ' + changeNum;
    },

    _computeCommitClass(collapsed, commitMessage) {
      if (this._computeCommitToggleHidden(commitMessage)) { return ''; }
      return collapsed ? 'collapsed' : '';
    },

    _computeRelatedChangesClass(collapsed, loading) {
      if (!loading && !this.customStyle['--relation-chain-max-height']) {
        this._updateRelatedChangeMaxHeight();
      }
      return collapsed ? 'collapsed' : '';
    },

    _computeCollapseText(collapsed) {
      // Symbols are up and down triangles.
      return collapsed ? '\u25bc Show more' : '\u25b2 Show less';
    },

    _toggleCommitCollapsed() {
      this._commitCollapsed = !this._commitCollapsed;
      if (this._commitCollapsed) {
        window.scrollTo(0, 0);
      }
    },

    _toggleRelatedChangesCollapsed() {
      this._relatedChangesCollapsed = !this._relatedChangesCollapsed;
      if (this._relatedChangesCollapsed) {
        window.scrollTo(0, 0);
      }
    },

    _computeCommitToggleHidden(commitMessage) {
      if (!commitMessage) { return true; }
      return commitMessage.split('\n').length < MIN_LINES_FOR_COMMIT_COLLAPSE;
    },

    _getOffsetHeight(element) {
      return element.offsetHeight;
    },

    _getScrollHeight(element) {
      return element.scrollHeight;
    },

    /**
     * Get the line height of an element to the nearest integer.
     */
    _getLineHeight(element) {
      const lineHeightStr = getComputedStyle(element).lineHeight;
      return Math.round(lineHeightStr.slice(0, lineHeightStr.length - 2));
    },

    /**
     * New max height for the related changes section, shorter than the existing
     * change info height.
     */
    _updateRelatedChangeMaxHeight() {
      // Takes into account approximate height for the expand button and
      // bottom margin.
      const EXTRA_HEIGHT = 30;
      let newHeight;
      const hasCommitToggle =
          !this._computeCommitToggleHidden(this._latestCommitMessage);

      if (window.matchMedia(`(max-width: ${BREAKPOINT_RELATED_SMALL})`)
          .matches) {
        // In a small (mobile) view, give the relation chain some space.
        newHeight = SMALL_RELATED_HEIGHT;
      } else if (window.matchMedia(`(max-width: ${BREAKPOINT_RELATED_MED})`)
          .matches) {
        // Since related changes are below the commit message, but still next to
        // metadata, the height should be the height of the metadata minus the
        // height of the commit message to reduce jank. However, if that doesn't
        // result in enough space, instead use the MINIMUM_RELATED_MAX_HEIGHT.
        // Note: extraHeight is to take into account margin/padding.
        const medRelatedHeight = Math.max(
            this._getOffsetHeight(this.$.mainChangeInfo) -
            this._getOffsetHeight(this.$.commitMessage) - 2 * EXTRA_HEIGHT,
            MINIMUM_RELATED_MAX_HEIGHT);
        newHeight = medRelatedHeight;
      } else {
        if (hasCommitToggle) {
          // Make sure the content is lined up if both areas have buttons. If
          // the commit message is not collapsed, instead use the change info
          // height.
          newHeight = this._getOffsetHeight(this.$.commitMessage);
        } else {
          newHeight = this._getOffsetHeight(this.$.commitAndRelated) -
              EXTRA_HEIGHT;
        }
      }
      if (this.$.relatedChanges.hidden) {
        this.customStyle['--commit-message-max-width'] = 'none';
      }
      // Get the line height of related changes, and convert it to the nearest
      // integer.
      const lineHeight = this._getLineHeight(this.$.relatedChanges);

      // Figure out a new height that is divisible by the rounded line height.
      const remainder = newHeight % lineHeight;
      newHeight = newHeight - remainder;

      this.customStyle['--relation-chain-max-height'] = newHeight + 'px';

      // Update the max-height of the relation chain to this new height.
      if (hasCommitToggle) {
        this.customStyle['--related-change-btn-top-padding'] =
          remainder + 'px';
      }
      this.updateStyles();
    },

    _computeRelatedChangesToggleHidden() {
      return this._getScrollHeight(this.$.relatedChanges) <=
          this._getOffsetHeight(this.$.relatedChanges);
    },

    _startUpdateCheckTimer() {
      if (!this.serverConfig ||
          !this.serverConfig.change ||
          this.serverConfig.change.update_delay === undefined ||
          this.serverConfig.change.update_delay <= MIN_CHECK_INTERVAL_SECS) {
        return;
      }

      this._updateCheckTimerHandle = this.async(() => {
        this.fetchIsLatestKnown(this._change, this.$.restAPI)
            .then(latest => {
              if (latest) {
                this._startUpdateCheckTimer();
              } else {
                this._cancelUpdateCheckTimer();
                this.fire('show-alert', {
                  message: 'A newer patch has been uploaded.',
                  // Persist this alert.
                  dismissOnNavigation: true,
                  action: 'Reload',
                  callback: function() {
                    // Load the current change without any patch range.
                    location.href = this.getBaseUrl() + '/c/' +
                        this._change._number;
                  }.bind(this),
                });
              }
            });
      }, this.serverConfig.change.update_delay * 1000);
    },

    _cancelUpdateCheckTimer() {
      this.cancelAsync(this._updateCheckTimerHandle);
      this._updateCheckTimerHandle = null;
    },

    _handleVisibilityChange() {
      if (document.hidden && this._updateCheckTimerHandle) {
        this._cancelUpdateCheckTimer();
      } else if (!this._updateCheckTimerHandle) {
        this._startUpdateCheckTimer();
      }
    },

    _computeHeaderClass(change) {
      return change.work_in_progress ? 'header wip' : 'header';
    },
  });
})();
