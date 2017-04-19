// Copyright (C) 2017 The Android Open Source Project
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
    is: 'gr-inline-edit',

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
        value: function() { return {}; },
      },
      _account: {
        type: Object,
        value: {},
      },
      _change: {
        type: Object,
        observer: '_changeChanged',
      },
      _editChange: Object,
      _path: {
        type: String,
      },
      _changeNum: String,
      _diffDrafts: {
        type: Object,
        value: function() { return {}; },
      },
      _editingEditChange: {
        type: Boolean,
        value: false,
      },
      _lineHeight: Number,
      _patchRange: {
        type: Object,
      },
      _currentRevisionActions: Object,
      _loggedIn: {
        type: Boolean,
        value: false,
      },
      _loading: {
        type: Boolean,
        value: true,
      },
      _initialLoadComplete: {
        type: Boolean,
        value: false,
      },
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.KeyboardShortcutBehavior,
      Gerrit.PatchSetBehavior,
      Gerrit.RESTClientBehavior,
    ],

    observers: [
      '_paramsAndChangeChanged(params, _change)',
    ],

    attached: function() {
      this._getLoggedIn().then(function(loggedIn) {
        this._loggedIn = loggedIn;
        if (loggedIn) {
          this.$.restAPI.getAccount().then(function(acct) {
            this._account = acct;
          }.bind(this));
        }
      }.bind(this));

      this.addEventListener('editable-content-save',
          this._handleEditContentSave.bind(this));
      this.addEventListener('editable-content-cancel',
          this._handleEditContentCancel.bind(this));
      this.listen(window, 'scroll', '_handleScroll');
    },

    detached: function() {
      this.unlisten(window, 'scroll', '_handleScroll');
    },

    _getChangeDetail: function(changeNum) {
      return this.$.restAPI.getDiffChangeDetail(changeNum).then(
          function(change) {
            this._change = change;
          }.bind(this));
    },

    _handleEditEditContent: function(e) {
      this._editingEditContent = true;
      //this.$.editContentEditor.focusTextarea();
    },

    _handleEditContentSave: function(e) {
      var content = e.detail.content;

      this.$.jsAPI.handleEditContent(this._change, this._path, content);

      this.$.editContentEditor.disabled = true;
      this._saveEditContent(this._path, content).then(function(resp) {
        this.$.editContentEditor.disabled = false;
        if (!resp.ok) { return; }

        this._editingCommitMessage = false;
      }.bind(this)).catch(function(err) {
        this.$.editContentEditor.disabled = false;
      }.bind(this));
    },

    _reloadWindow: function() {
      window.location.reload();
    },

    _handleEditContentCancel: function(e) {
      this._editingEditContent = false;
    },

    _saveEditContent: function(path, content) {
      return this.$.restAPI.saveChangeEdit(
          this._changeNum, path, content).then(function(resp) {
            if (!resp.ok) { return resp; }

            return this.$.restAPI.saveChangeEdit(this._changeNum, path, content);
          }.bind(this));
    },

    _handlePatchChange: function(e) {
      this._changePatchNum(parseInt(e.target.value, 10), true);
    },

    _computeChangePath: function(changeNum, patchRangeRecord, revisions) {
      return this._getChangePath(changeNum, patchRangeRecord.base, revisions);
    },

    _paramsChanged: function(value) {
      this._changeNum = value.changeNum;
      this._patchRange = {
        patchNum: value.patchNum,
        basePatchNum: value.basePatchNum || 'PARENT',
      };
      this._path = value.path;

      if (!this._patchRange.patchNum) {
        return;
      }

      var promises = [];

      promises.push(this._getChangeDetail(this._changeNum));

      Promise.all(promises).then(function() {
        this._loading = false;
      }.bind(this));
    },

    _performPostLoadTasks: function() {
      this.$.jsAPI.handleEvent(this.$.jsAPI.EventType.SHOW_CHANGE, {
        change: this._change,
        patchNum: this._patchRange.patchNum,
      });

      this._initialLoadComplete = true;
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

    _getLocationSearch: function() {
      // Not inlining to make it easier to test.
      return window.location.search;
    },

    _getUrlParameter: function(param) {
      var pageURL = this._getLocationSearch().substring(1);
      var vars = pageURL.split('&');
      for (var i = 0; i < vars.length; i++) {
        var name = vars[i].split('=');
        if (name[0] == param) {
          return name[0];
        }
      }
      return null;
    },

    _resetFileListViewState: function() {
      this.set('viewState.selectedFileIndex', 0);
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
     * @param {number} patchNum the patchn number to be viewed.
     * @param {boolean} opt_forceParams When set to true, the resulting URL will
     *     always include the patch range, even if the requested patchNum is
     *     known to be the latest.
     */
    _changePatchNum: function(patchNum, opt_forceParams) {
      if (!opt_forceParams) {
        var currentPatchNum;
        if (this._change.current_revision) {
          currentPatchNum =
              this._change.revisions[this._change.current_revision]._number;
        } else {
          currentPatchNum = this._computeLatestPatchNum(this._allPatchSets);
        }
        if (patchNum === currentPatchNum &&
            this._patchRange.basePatchNum === 'PARENT') {
          page.show(this.changePath(this._changeNum));
          return;
        }
      }
      var patchExpr = this._patchRange.basePatchNum === 'PARENT' ? patchNum :
          this._patchRange.basePatchNum + '..' + patchNum;
      page.show(this.changePath(this._changeNum) + '/' + patchExpr);
    },

    _computeChangePermalink: function(changeNum) {
      return this.getBaseUrl() + '/' + changeNum;
    },

    _computeChangeStatus: function(change, patchNum) {
      var statusString;
      if (change.status === this.ChangeStatus.NEW) {
        var rev = this.getRevisionByPatchNum(change.revisions, patchNum);
        if (rev && rev.draft === true) {
          statusString = 'Draft';
        }
      } else {
        statusString = this.changeStatusString(change);
      }
      return statusString || '';
    },

    _computeShowCommitInfo: function(changeStatus, current_revision) {
      return changeStatus === 'Merged' && current_revision;
    },

    _computeMergedCommitInfo: function(current_revision, revisions) {
      var rev = revisions[current_revision];
      if (!rev || !rev.commit) { return {}; }
      // CommitInfo.commit is optional. Set commit in all cases to avoid error
      // in <gr-commit-info>. @see Issue 5337
      if (!rev.commit.commit) { rev.commit.commit = current_revision; }
      return rev.commit;
    },

    _computeLatestPatchNum: function(allPatchSets) {
      return allPatchSets[allPatchSets.length - 1].num;
    },

    _computePatchInfoClass: function(patchNum, allPatchSets) {
      if (parseInt(patchNum, 10) ===
          this._computeLatestPatchNum(allPatchSets)) {
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
    _computePatchSetDisabled: function(patchNum, basePatchNum) {
      basePatchNum = basePatchNum === 'PARENT' ? 0 : basePatchNum;
      return parseInt(patchNum, 10) <= parseInt(basePatchNum, 10);
    },

    _computeAllPatchSets: function(change) {
      var patchNums = [];
      for (var commit in change.revisions) {
        if (change.revisions.hasOwnProperty(commit)) {
          patchNums.push({
            num: change.revisions[commit]._number,
            desc: change.revisions[commit].description,
          });
        }
      }
      return patchNums.sort(function(a, b) { return a.num - b.num; });
    },

    _determinePageBack: function() {
      // Default backPage to '/' if user came to change view page
      // via an email link, etc.
      page.show(this.backPage || '/');
    },

    _handleLabelRemoved: function(splices, path) {
      for (var i = 0; i < splices.length; i++) {
        var splice = splices[i];
        for (var j = 0; j < splice.removed.length; j++) {
          var removed = splice.removed[j];
          var changePath = path.split('.');
          var labelPath = changePath.splice(0, changePath.length - 2);
          var labelDict = this.get(labelPath);
          if (labelDict.approved &&
              labelDict.approved._account_id === removed._account_id) {
            this._reload();
            return;
          }
        }
      }
    },

    _labelsChanged: function(changeRecord) {
      if (!changeRecord) { return; }
      if (changeRecord.value.indexSplices) {
        this._handleLabelRemoved(changeRecord.value.indexSplices,
            changeRecord.path);
      }
      this.$.jsAPI.handleEvent(this.$.jsAPI.EventType.LABEL_CHANGE, {
        change: this._change,
      });
    },

    _handleGetChangeDetailError: function(response) {
      this.fire('page-error', {response: response});
    },

    _getLoggedIn: function() {
      return this.$.restAPI.getLoggedIn();
    },

    _getLatestRevisionSHA: function(change) {
      if (change.current_revision) {
        return change.current_revision;
      }
      // current_revision may not be present in the case where the latest rev is
      // a draft and the user doesn’t have permission to view that rev.
      var latestRev = null;
      var latestPatchNum = -1;
      for (var rev in change.revisions) {
        if (!change.revisions.hasOwnProperty(rev)) { continue; }

        if (change.revisions[rev]._number > latestPatchNum) {
          latestRev = rev;
          latestPatchNum = change.revisions[rev]._number;
        }
      }
      return latestRev;
    },

    _getCommitInfo: function() {
      return this.$.restAPI.getChangeCommitInfo(
          this._changeNum, this._patchRange.patchNum).then(
              function(commitInfo) {
                this._commitInfo = commitInfo;
              }.bind(this));
    },

    /**
     * @param {Object} revisions The revisions object keyed by revision hashes
     * @param {Object} patchSet A revision already fetched from {revisions}
     * @return {string} the SHA hash corresponding to the revision.
     */
    _getPatchsetHash: function(revisions, patchSet) {
      for (var rev in revisions) {
        if (revisions.hasOwnProperty(rev) &&
            revisions[rev] === patchSet) {
          return rev;
        }
      }
    },

    _computeDescriptionReadOnly: function(loggedIn, change, account) {
      return !(loggedIn && (account._account_id === change.owner._account_id));
    },

    _computeChangePermalinkAriaLabel: function(changeNum) {
      return 'Change ' + changeNum;
    },

    _getOffsetHeight: function(element) {
      return element.offsetHeight;
    },

    _getScrollHeight: function(element) {
      return element.scrollHeight;
    },
  });
})();
