/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function() {
  'use strict';

  const CHANGE_ID_ERROR = {
    MISMATCH: 'mismatch',
    MISSING: 'missing',
  };
  const CHANGE_ID_REGEX_PATTERN = /^Change-Id\:\s(I[0-9a-f]{8,40})/gm;

  const MIN_LINES_FOR_COMMIT_COLLAPSE = 30;
  const DEFAULT_NUM_FILES_SHOWN = 200;

  const REVIEWERS_REGEX = /^(R|CC)=/gm;
  const MIN_CHECK_INTERVAL_SECS = 0;

  // These are the same as the breakpoint set in CSS. Make sure both are changed
  // together.
  const BREAKPOINT_RELATED_SMALL = '50em';
  const BREAKPOINT_RELATED_MED = '60em';

  // In the event that the related changes medium width calculation is too close
  // to zero, provide some height.
  const MINIMUM_RELATED_MAX_HEIGHT = 100;

  const SMALL_RELATED_HEIGHT = 400;

  const REPLY_REFIT_DEBOUNCE_INTERVAL_MS = 500;

  const TRAILING_WHITESPACE_REGEX = /[ \t]+$/gm;

  const ReloadToastMessage = {
    NEWER_REVISION: 'A newer patch set has been uploaded',
    RESTORED: 'This change has been restored',
    ABANDONED: 'This change has been abandoned',
    MERGED: 'This change has been merged',
    NEW_MESSAGE: 'There are new messages on this change',
  };

  const DiffViewMode = {
    SIDE_BY_SIDE: 'SIDE_BY_SIDE',
    UNIFIED: 'UNIFIED_DIFF',
  };

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
      /** @type {?} */
      viewState: {
        type: Object,
        notify: true,
        value() { return {}; },
        observer: '_viewStateChanged',
      },
      backPage: String,
      hasParent: Boolean,
      keyEventTarget: {
        type: Object,
        value() { return document.body; },
      },
      _commentThreads: Array,
      /** @type {?} */
      _serverConfig: {
        type: Object,
        observer: '_startUpdateCheckTimer',
      },
      _diffPrefs: Object,
      _numFilesShown: {
        type: Number,
        value: DEFAULT_NUM_FILES_SHOWN,
        observer: '_numFilesShownChanged',
      },
      _account: {
        type: Object,
        value: {},
      },
      /** @type {?} */
      _changeComments: Object,
      _canStartReview: {
        type: Boolean,
        computed: '_computeCanStartReview(_loggedIn, _change, _account)',
      },
      _comments: Object,
      /** @type {?} */
      _change: {
        type: Object,
        observer: '_changeChanged',
      },
      /** @type {?} */
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
      _diffAgainst: String,
      /** @type {?string} */
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
        /** @type {?} */
      _patchRange: {
        type: Object,
      },
      _filesExpanded: String,
      _basePatchNum: String,
      _selectedRevision: Object,
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
      /** @type {?} */
      _missingLabels: {
        type: Array,
        computed: '_computeMissingLabels(_change.labels)',
      },
      /** @type {?} */
      _projectConfig: Object,
      _rebaseOnCurrent: Boolean,
      _replyButtonLabel: {
        type: String,
        value: 'Reply',
        computed: '_computeReplyButtonLabel(_diffDrafts.*, _canStartReview)',
      },
      _selectedPatchSet: String,
      _shownFileCount: Number,
      _initialLoadComplete: {
        type: Boolean,
        value: false,
      },
      _replyDisabled: {
        type: Boolean,
        value: true,
        computed: '_computeReplyDisabled(_serverConfig)',
      },
      _changeStatus: {
        type: String,
        computed: 'changeStatusString(_change)',
      },
      _changeStatuses: {
        type: String,
        computed: '_computeChangeStatusChips(_change, _mergeable)',
      },
      _commitCollapsed: {
        type: Boolean,
        value: true,
      },
      _relatedChangesCollapsed: {
        type: Boolean,
        value: true,
      },
      /** @type {?number} */
      _updateCheckTimerHandle: Number,
      _editMode: {
        type: Boolean,
        computed: '_computeEditMode(_patchRange.*, params.*)',
      },
      _showRelatedToggle: {
        type: Boolean,
        value: false,
        observer: '_updateToggleContainerClass',
      },
      _parentIsCurrent: Boolean,
      _submitEnabled: Boolean,

      /** @type {?} */
      _mergeable: {
        type: Boolean,
        value: undefined,
      },
      _showMessagesView: {
        type: Boolean,
        value: true,
      },
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
      Gerrit.PatchSetBehavior,
      Gerrit.RESTClientBehavior,
    ],

    listeners: {
      'topic-changed': '_handleTopicChanged',
      // When an overlay is opened in a mobile viewport, the overlay has a full
      // screen view. When it has a full screen view, we do not want the
      // background to be scrollable. This will eliminate background scroll by
      // hiding most of the contents on the screen upon opening, and showing
      // again upon closing.
      'fullscreen-overlay-opened': '_handleHideBackgroundContent',
      'fullscreen-overlay-closed': '_handleShowBackgroundContent',
      'diff-comments-modified': '_handleReloadCommentThreads',
    },
    observers: [
      '_labelsChanged(_change.labels.*)',
      '_paramsAndChangeChanged(params, _change)',
      '_patchNumChanged(_patchRange.patchNum)',
    ],

    keyBindings: {
      'shift+r': '_handleCapitalRKey',
      'a': '_handleAKey',
      'd': '_handleDKey',
      'm': '_handleMKey',
      's': '_handleSKey',
      'u': '_handleUKey',
      'x': '_handleXKey',
      'z': '_handleZKey',
      ',': '_handleCommaKey',
    },

    attached() {
      this._getServerConfig().then(config => {
        this._serverConfig = config;
      });

      this._getLoggedIn().then(loggedIn => {
        this._loggedIn = loggedIn;
        if (loggedIn) {
          this.$.restAPI.getAccount().then(acct => {
            this._account = acct;
          });
        }
        this._setDiffViewMode();
      });

      this.addEventListener('comment-save', this._handleCommentSave.bind(this));
      this.addEventListener('comment-refresh', this._reloadDrafts.bind(this));
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

    get messagesList() {
      return this.$$('gr-messages-list');
    },

    get threadList() {
      return this.$$('gr-thread-list');
    },

    /**
     * @param {boolean=} opt_reset
     */
    _setDiffViewMode(opt_reset) {
      if (!opt_reset && this.viewState.diffViewMode) { return; }

      return this.$.restAPI.getPreferences().then( prefs => {
        if (!this.viewState.diffMode) {
          this.set('viewState.diffMode', prefs.default_diff_view);
        }
      }).then(() => {
        if (!this.viewState.diffMode) {
          this.set('viewState.diffMode', 'SIDE_BY_SIDE');
        }
      });
    },

    _handleMKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      if (this.viewState.diffMode === DiffViewMode.SIDE_BY_SIDE) {
        this.$.fileListHeader.setDiffViewMode(DiffViewMode.UNIFIED);
      } else {
        this.$.fileListHeader.setDiffViewMode(DiffViewMode.SIDE_BY_SIDE);
      }
    },

    _handleTabChange() {
      this._showMessagesView = this.$.commentTabs.selected === 0;
    },

    _handleEditCommitMessage(e) {
      this._editingCommitMessage = true;
      this.$.commitMessageEditor.focusTextarea();
    },

    _handleCommitMessageSave(e) {
      // Trim trailing whitespace from each line.
      const message = e.detail.content.replace(TRAILING_WHITESPACE_REGEX, '');

      this.$.jsAPI.handleCommitMessage(this._change, message);

      this.$.commitMessageEditor.disabled = true;
      this.$.restAPI.putChangeCommitMessage(
          this._changeNum, message).then(resp => {
            this.$.commitMessageEditor.disabled = false;
            if (!resp.ok) { return; }

            this._latestCommitMessage = this._prepareCommitMsgForLinkify(
                message);
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

    _computeChangeStatusChips(change, mergeable) {
      // Show no chips until mergeability is loaded.
      if (mergeable === null || mergeable === undefined) { return []; }

      const options = {
        includeDerived: true,
        mergeable: !!mergeable,
        submitEnabled: this._submitEnabled,
      };
      return this.changeStatuses(change, options);
    },

    _computeHideEditCommitMessage(loggedIn, editing, change) {
      if (!loggedIn || editing || change.status === this.ChangeStatus.MERGED) {
        return true;
      }

      return false;
    },

    _handleReloadCommentThreads() {
      // Get any new drafts that have been saved in the diff view and show
      // in the comment thread view.
      this._reloadDrafts().then(() => {
        this._commentThreads = this._changeComments.getAllThreadsForChange()
            .map(c => Object.assign({}, c));
        Polymer.dom.flush();
      });
    },

    _handleReloadDiffComments(e) {
      // Keeps the file list counts updated.
      this._reloadDrafts().then(() => {
        // Get any new drafts that have been saved in the thread view and show
        // in the diff view.
        this.$.fileList.reloadCommentsForThreadWithRootId(e.detail.rootId,
            e.detail.path);
        Polymer.dom.flush();
      });
    },

    _computeTotalCommentCounts(unresolvedCount, changeComments) {
      const draftCount = changeComments.computeDraftCount();
      const unresolvedString = GrCountStringFormatter.computeString(
          unresolvedCount, 'unresolved');
      const draftString = GrCountStringFormatter.computePluralString(
          draftCount, 'draft');

      return unresolvedString +
          // Add a comma and space if both unresolved and draft comments exist.
          (unresolvedString && draftString ? ', ' : '') +
          draftString;
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

    _handleReplyTap(e) {
      e.preventDefault();
      this._openReplyDialog(this.$.replyDialog.FocusTarget.ANY);
    },

    _handleOpenDiffPrefs() {
      this.$.fileList.openDiffPrefs();
    },

    _handleOpenIncludedInDialog() {
      this.$.includedInDialog.loadData().then(() => {
        Polymer.dom.flush();
        this.$.includedInOverlay.refit();
      });
      this.$.includedInOverlay.open();
    },

    _handleIncludedInDialogClose(e) {
      this.$.includedInOverlay.close();
    },

    _handleOpenDownloadDialog() {
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
      this.$.replyDialog.quote = quoteStr;
      this._openReplyDialog(this.$.replyDialog.FocusTarget.BODY);
    },

    _handleHideBackgroundContent() {
      this.$.mainContent.classList.add('overlayOpen');
    },

    _handleShowBackgroundContent() {
      this.$.mainContent.classList.remove('overlayOpen');
    },

    _handleReplySent(e) {
      this.$.replyOverlay.close();
      this._reload();
    },

    _handleReplyCancel(e) {
      this.$.replyOverlay.close();
    },

    _handleReplyAutogrow(e) {
      // If the textarea resizes, we need to re-fit the overlay.
      this.debounce('reply-overlay-refit', () => {
        this.$.replyOverlay.refit();
      }, REPLY_REFIT_DEBOUNCE_INTERVAL_MS);
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

    _setShownFiles(e) {
      this._shownFileCount = e.detail.length;
    },

    _expandAllDiffs() {
      this.$.fileList.expandAllDiffs();
    },

    _collapseAllDiffs() {
      this.$.fileList.collapseAllDiffs();
    },

    _paramsChanged(value) {
      // Change the content of the comment tabs back to messages list, but
      // do not yet change the tab itself. The animation of tab switching will
      // get messed up if changed here, because it requires the tabs to be on
      // the streen, and they are hidden shortly after this. The tab switching
      // animation will happen in post render tasks.
      this._showMessagesView = true;

      if (value.view !== Gerrit.Nav.View.CHANGE) {
        this._initialLoadComplete = false;
        return;
      }

      if (value.changeNum && value.project) {
        this.$.restAPI.setInProjectLookup(value.changeNum, value.project);
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

      this.$.fileList.collapseAllDiffs();
      this._patchRange = patchRange;

      if (this._initialLoadComplete && patchChanged) {
        if (patchRange.patchNum == null) {
          patchRange.patchNum = this.computeLatestPatchNum(this._allPatchSets);
        }
        this._reloadPatchNumDependentResources().then(() => {
          this._sendShowChangeEvent();
        });
        return;
      }

      this._changeNum = value.changeNum;
      this.$.relatedChanges.clear();

      this._reload().then(() => {
        this._performPostLoadTasks();
      });
    },

    _sendShowChangeEvent() {
      this.$.jsAPI.handleEvent(this.$.jsAPI.EventType.SHOW_CHANGE, {
        change: this._change,
        patchNum: this._patchRange.patchNum,
        info: {mergeable: this._mergeable},
      });
    },

    _performPostLoadTasks() {
      this.$.relatedChanges.reload();
      this._maybeShowReplyDialog();
      this._maybeShowRevertDialog();

      this._sendShowChangeEvent();

      // Selected has to be set after the paper-tabs are visible because
      // the selected underline depends on calculations made by the browser.
      this.$.commentTabs.selected = 0;

      this.async(() => {
        if (this.viewState.scrollTop) {
          document.documentElement.scrollTop =
              document.body.scrollTop = this.viewState.scrollTop;
        } else {
          this._maybeScrollToMessage(window.location.hash);
        }
        this._initialLoadComplete = true;
      });
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

    _viewStateChanged(viewState) {
      this._numFilesShown = viewState.numFilesShown ?
          viewState.numFilesShown : DEFAULT_NUM_FILES_SHOWN;
    },

    _numFilesShownChanged(numFilesShown) {
      this.viewState.numFilesShown = numFilesShown;
    },

    _maybeScrollToMessage(hash) {
      const msgPrefix = '#message-';
      if (hash.startsWith(msgPrefix)) {
        this.messagesList.scrollToMessage(hash.substr(msgPrefix.length));
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
          this._openReplyDialog(this.$.replyDialog.FocusTarget.ANY);
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
        this._setDiffViewMode(true);
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
              this.computeLatestPatchNum(this._allPatchSets));

      // Reset the related changes toggle in the event it was previously
      // displayed on an earlier change.
      this._showRelatedToggle = false;

      const title = change.subject + ' (' + change.change_id.substr(0, 9) + ')';
      this.fire('title-change', {title});
    },

    _computeChangeUrl(change) {
      return Gerrit.Nav.getUrlForChange(change);
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

    _computeLabelNames(labels) {
      return Object.keys(labels).sort();
    },

    _computeLabelValues(labelName, labels) {
      const result = [];
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
        this._openReplyDialog(this.$.replyDialog.FocusTarget.ANY);
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
      Gerrit.Nav.navigateToChange(this._change);
    },

    _handleSKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this.$.changeStar.toggleStar();
    },

    _handleUKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this._determinePageBack();
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
      Gerrit.Nav.navigateToRelativeUrl(this.backPage || '/');
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

    /**
     * @param {string=} opt_section
     */
    _openReplyDialog(opt_section) {
      this.$.replyOverlay.open().then(() => {
        this._resetReplyOverlayFocusStops();
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
          Gerrit.Nav.navigateToChange(this._change);
        }
      });
    },

    _handleGetChangeDetailError(response) {
      this.fire('page-error', {response});
    },

    _getLoggedIn() {
      return this.$.restAPI.getLoggedIn();
    },

    _getServerConfig() {
      return this.$.restAPI.getConfig();
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
        this._parentIsCurrent = !revisionActions.rebase.enabled;
        revisionActions.rebase.enabled = true;
      } else {
        this._parentIsCurrent = true;
      }
      return revisionActions;
    },

    _prepareCommitMsgForLinkify(msg) {
      // TODO(wyatta) switch linkify sequence, see issue 5526.
      // This is a zero-with space. It is added to prevent the linkify library
      // from including R= or CC= as part of the email address.
      return msg.replace(REVIEWERS_REGEX, '$1=\u200B');
    },

    /**
     * Utility function to make the necessary modifications to a change in the
     * case an edit exists.
     *
     * @param {!Object} change
     * @param {?Object} edit
     */
    _processEdit(change, edit) {
      if (!edit) { return; }
      change.revisions[edit.commit.commit] = {
        _number: this.EDIT_NAME,
        basePatchNum: edit.base_patch_set_number,
        commit: edit.commit,
        fetch: edit.fetch,
      };
      // If the edit is based on the most recent patchset, load it by
      // default, unless another patch set to load was specified in the URL.
      if (!this._patchRange.patchNum &&
          change.current_revision === edit.base_revision) {
        change.current_revision = edit.commit.commit;
        this._patchRange.patchNum = this.EDIT_NAME;
        // Because edits are fibbed as revisions and added to the revisions
        // array, and revision actions are always derived from the 'latest'
        // patch set, we must copy over actions from the patch set base.
        // Context: Issue 7243
        change.revisions[edit.commit.commit].actions =
            change.revisions[edit.base_revision].actions;
      }
    },

    _getChangeDetail() {
      const detailCompletes = this.$.restAPI.getChangeDetail(
          this._changeNum, this._handleGetChangeDetailError.bind(this));
      const editCompletes = this._getEdit();

      return Promise.all([detailCompletes, editCompletes])
          .then(([change, edit]) => {
            if (!change) {
              return '';
            }
            this._processEdit(change, edit);
            // Issue 4190: Coalesce missing topics to null.
            if (!change.topic) { change.topic = null; }
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

            // Update the submit enabled based on current revision.
            this._submitEnabled = this._isSubmitEnabled(currentRevision);

            const lineHeight = getComputedStyle(this).lineHeight;

            // Slice returns a number as a string, convert to an int.
            this._lineHeight =
                parseInt(lineHeight.slice(0, lineHeight.length - 2), 10);

            this._change = change;
            if (!this._patchRange || !this._patchRange.patchNum ||
                this.patchNumEquals(this._patchRange.patchNum,
                    currentRevision._number)) {
              // CommitInfo.commit is optional, and may need patching.
              if (!currentRevision.commit.commit) {
                currentRevision.commit.commit = latestRevisionSha;
              }
              this._commitInfo = currentRevision.commit;
              this._currentRevisionActions =
                      this._updateRebaseAction(currentRevision.actions);
              this._selectedRevision = currentRevision;
              // TODO: Fetch and process files.
            } else {
              this._selectedRevision =
                Object.values(this._change.revisions).find(
                    revision => revision._number ===
                      parseInt(this._patchRange.patchNum, 10));
            }
          });
    },

    _isSubmitEnabled(currentRevision) {
      return !!(currentRevision.actions && currentRevision.actions.submit &&
          currentRevision.actions.submit.enabled);
    },

    _getEdit() {
      return this.$.restAPI.getChangeEdit(this._changeNum, true);
    },

    _getLatestCommitMessage() {
      return this.$.restAPI.getChangeCommitInfo(this._changeNum,
          this.computeLatestPatchNum(this._allPatchSets)).then(commitInfo => {
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

    _reloadDraftsWithCallback(e) {
      return this._reloadDrafts().then(() => {
        return e.detail.resolve();
      });
    },

    /**
     * Fetches a new changeComment object, and data for all types of comments
     * (comments, robot comments, draft comments) is requested.
     */
    _reloadComments() {
      return this.$.commentAPI.loadAll(this._changeNum)
          .then(comments => {
            this._changeComments = comments;
            this._diffDrafts = Object.assign({}, this._changeComments.drafts);
            this._commentThreads = this._changeComments.getAllThreadsForChange()
              .map(c => Object.assign({}, c));
          });
    },

    /**
     * Fetches a new changeComment object, but only updated data for drafts is
     * requested.
     */
    _reloadDrafts() {
      return this.$.commentAPI.reloadDrafts(this._changeNum)
          .then(comments => {
            this._changeComments = comments;
            this._diffDrafts = Object.assign({}, this._changeComments.drafts);
          });
    },

    _reload() {
      this._loading = true;
      this._relatedChangesCollapsed = true;

      const detailCompletes = this._getChangeDetail().then(() => {
        this._loading = false;
        this._getProjectConfig();
      });

      this._reloadComments();

      if (this._patchRange.patchNum) {
        return Promise.all([
          this._reloadPatchNumDependentResources(),
          detailCompletes,
        ]).then(() => {
          return Promise.all([
            this._getMergeability(),
            this.$.actions.reload(),
          ]);
        });
      } else {
        // The patch number is reliant on the change detail request.
        return detailCompletes.then(() => {
          this.$.fileList.reload();
          if (!this._latestCommitMessage) {
            this._getLatestCommitMessage();
          }
          return this._getMergeability();
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

    _getMergeability() {
      // If the change is closed, it is not mergeable. Note: already merged
      // changes are obviously not mergeable, but the mergeability API will not
      // answer for abandoned changes.
      if (this._change.status === this.ChangeStatus.MERGED ||
          this._change.status === this.ChangeStatus.ABANDONED) {
        this._mergeable = false;
        return Promise.resolve();
      }

      this._mergeable = null;
      return this.$.restAPI.getMergeable(this._changeNum).then(m => {
        this._mergeable = m.mergeable;
      });
    },

    _computeCanStartReview(loggedIn, change, account) {
      return !!(loggedIn && change.work_in_progress &&
          change.owner._account_id === account._account_id);
    },

    _computeReplyDisabled() { return false; },

    _computeChangePermalinkAriaLabel(changeNum) {
      return 'Change ' + changeNum;
    },

    _computeCommitClass(collapsed, commitMessage) {
      if (this._computeCommitToggleHidden(commitMessage)) { return ''; }
      return collapsed ? 'collapsed' : '';
    },

    _computeRelatedChangesClass(collapsed) {
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
      const stylesToUpdate = {};

      // Get the line height of related changes, and convert it to the nearest
      // integer.
      const lineHeight = this._getLineHeight(this.$.relatedChanges);

      // Figure out a new height that is divisible by the rounded line height.
      const remainder = newHeight % lineHeight;
      newHeight = newHeight - remainder;

      stylesToUpdate['--relation-chain-max-height'] = newHeight + 'px';

      // Update the max-height of the relation chain to this new height.
      if (hasCommitToggle) {
        stylesToUpdate['--related-change-btn-top-padding'] = remainder + 'px';
      }

      this.updateStyles(stylesToUpdate);
    },

    _computeShowRelatedToggle() {
      // Make sure the max height has been applied, since there is now content
      // to populate.
      // TODO update to polymer 2.x syntax
      if (!this.getComputedStyleValue('--relation-chain-max-height')) {
        this._updateRelatedChangeMaxHeight();
      }
      // Prevents showMore from showing when click on related change, since the
      // line height would be positive, but related changes height is 0.
      if (!this._getScrollHeight(this.$.relatedChanges)) {
        return this._showRelatedToggle = false;
      }

      if (this._getScrollHeight(this.$.relatedChanges) >
          (this._getOffsetHeight(this.$.relatedChanges) +
          this._getLineHeight(this.$.relatedChanges))) {
        return this._showRelatedToggle = true;
      }
      this._showRelatedToggle = false;
    },

    _updateToggleContainerClass(showRelatedToggle) {
      if (showRelatedToggle) {
        this.$.relatedChangesToggle.classList.add('showToggle');
      } else {
        this.$.relatedChangesToggle.classList.remove('showToggle');
      }
    },

    _startUpdateCheckTimer() {
      if (!this._serverConfig ||
          !this._serverConfig.change ||
          this._serverConfig.change.update_delay === undefined ||
          this._serverConfig.change.update_delay <= MIN_CHECK_INTERVAL_SECS) {
        return;
      }

      this._updateCheckTimerHandle = this.async(() => {
        this.fetchChangeUpdates(this._change, this.$.restAPI).then(result => {
          let toastMessage = null;
          if (!result.isLatest) {
            toastMessage = ReloadToastMessage.NEWER_REVISION;
          } else if (result.newStatus === this.ChangeStatus.MERGED) {
            toastMessage = ReloadToastMessage.MERGED;
          } else if (result.newStatus === this.ChangeStatus.ABANDONED) {
            toastMessage = ReloadToastMessage.ABANDONED;
          } else if (result.newStatus === this.ChangeStatus.NEW) {
            toastMessage = ReloadToastMessage.RESTORED;
          } else if (result.newMessages) {
            toastMessage = ReloadToastMessage.NEW_MESSAGE;
          }

          if (!toastMessage) {
            this._startUpdateCheckTimer();
            return;
          }

          this._cancelUpdateCheckTimer();
          this.fire('show-alert', {
            message: toastMessage,
            // Persist this alert.
            dismissOnNavigation: true,
            action: 'Reload',
            callback: function() {
              // Load the current change without any patch range.
              Gerrit.Nav.navigateToChange(this._change);
            }.bind(this),
          });
        });
      }, this._serverConfig.change.update_delay * 1000);
    },

    _cancelUpdateCheckTimer() {
      if (this._updateCheckTimerHandle) {
        this.cancelAsync(this._updateCheckTimerHandle);
      }
      this._updateCheckTimerHandle = null;
    },

    _handleVisibilityChange() {
      if (document.hidden && this._updateCheckTimerHandle) {
        this._cancelUpdateCheckTimer();
      } else if (!this._updateCheckTimerHandle) {
        this._startUpdateCheckTimer();
      }
    },

    _handleTopicChanged() {
      this.$.relatedChanges.reload();
    },

    _computeHeaderClass(editMode) {
      const classes = ['header'];
      if (editMode) { classes.push('editMode'); }
      return classes.join(' ');
    },

    _computeEditMode(patchRangeRecord, paramsRecord) {
      if (paramsRecord.base && paramsRecord.base.edit) { return true; }

      const patchRange = patchRangeRecord.base || {};
      return this.patchNumEquals(patchRange.patchNum, this.EDIT_NAME);
    },

    _handleFileActionTap(e) {
      e.preventDefault();
      const controls = this.$.fileListHeader.$.editControls;
      const path = e.detail.path;
      switch (e.detail.action) {
        case GrEditConstants.Actions.DELETE.id:
          controls.openDeleteDialog(path);
          break;
        case GrEditConstants.Actions.OPEN.id:
          Gerrit.Nav.navigateToRelativeUrl(
              Gerrit.Nav.getEditUrlForDiff(this._change, path,
                  this._patchRange.patchNum));
          break;
        case GrEditConstants.Actions.RENAME.id:
          controls.openRenameDialog(path);
          break;
        case GrEditConstants.Actions.RESTORE.id:
          controls.openRestoreDialog(path);
          break;
      }
    },

    _computeCommitMessageKey(number, revision) {
      return `c${number}_rev${revision}`;
    },

    _patchNumChanged(patchNumStr) {
      if (!this._selectedRevision) {
        return;
      }
      const patchNum = parseInt(patchNumStr, 10);
      if (patchNum === this._selectedRevision._number) {
        return;
      }
      this._selectedRevision = Object.values(this._change.revisions).find(
          revision => revision._number === patchNum);
    },

    /**
     * If an edit exists already, load it. Otherwise, toggle edit mode via the
     * navigation API.
     */
    _handleEditTap() {
      const editInfo = Object.values(this._change.revisions).find(info =>
          info._number === this.EDIT_NAME);

      if (editInfo) {
        Gerrit.Nav.navigateToChange(this._change, this.EDIT_NAME);
        return;
      }

      // Avoid putting patch set in the URL unless a non-latest patch set is
      // selected.
      let patchNum;
      if (!this.patchNumEquals(this._patchRange.patchNum,
          this.computeLatestPatchNum(this._allPatchSets))) {
        patchNum = this._patchRange.patchNum;
      }
      Gerrit.Nav.navigateToChange(this._change, patchNum, null, true);
    },

    _handleStopEditTap() {
      Gerrit.Nav.navigateToChange(this._change, this._patchRange.patchNum);
    },

    _resetReplyOverlayFocusStops() {
      this.$.replyOverlay.setFocusStops(this.$.replyDialog.getFocusStops());
    },
  });
})();
