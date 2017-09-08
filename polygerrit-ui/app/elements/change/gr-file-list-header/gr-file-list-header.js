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

  // Maximum length for patch set descriptions.
  const PATCH_DESC_MAX_LENGTH = 500;

  Polymer({
    is: 'gr-file-list-header',

    properties: {
      account: Object,
      changeUrl: String,
      comments: Object,
      commitInfo: Object,
      loggedIn: Boolean,
      serverConfig: Object,
      shownFileCount: Number,
      diffViewMode: String,
      patchRange: {
        type: Object,
        observer: 'updateSelected',
      },
      revisions: Array,
      // Caps the number of files that can be shown and have the 'show diffs' /
      // 'hide diffs' buttons still be functional.
      _maxFilesForBulkActions: {
        type: Number,
        readOnly: true,
        value: 225,
      },
      _descriptionReadOnly: {
        type: Boolean,
        computed: '_computeDescriptionReadOnly(loggedIn, change, account)',
      },
      _selectedPatchSet: String,
      _diffAgainst: String,
    },

    behaviors: [
      // Gerrit.KeyboardShortcutBehavior,
      Gerrit.PatchSetBehavior,
      // Gerrit.RESTClientBehavior,
    ],

    _expandAllDiffs() {
      this.fire('expand-diffs');
    },

    _collapseAllDiffs() {
      this.fire('collapse-diffs');
    },

    updateSelected() {
      this._selectedPatchSet = this.patchRange.patchNum;
      this._diffAgainst = this.patchRange.basePatchNum;
    },

    _computeDescriptionPlaceholder(readOnly) {
      return (readOnly ? 'No' : 'Add a') + ' patch set description';
    },

    _computeDescriptionReadOnly(loggedIn, change, account) {
      return !(loggedIn && (account._account_id === change.owner._account_id));
    },

    _computePatchSetDescription(change, patchNum) {
      const rev = this.getRevisionByPatchNum(change.revisions, patchNum);
      return (rev && rev.description) ?
          rev.description.substring(0, PATCH_DESC_MAX_LENGTH) : '';
    },

    _computeBasePatchDisabled(patchNum, currentPatchNum) {
      return this.findSortedIndex(patchNum, this._sortedRevisions) >=
          this.findSortedIndex(currentPatchNum, this._sortedRevisions);
    },

    _computePrefsButtonHidden(prefs, loggedIn) {
      return !loggedIn || !prefs;
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

    _fileListActionsVisible(shownFileCount, maxFilesForBulkActions) {
      return shownFileCount <= maxFilesForBulkActions;
    },

    /**
     * Determines if a patch number should be disabled based on value of the
     * basePatchNum from gr-file-list.
     * @param {number} patchNum Patch number available in dropdown
     * @param {number|string} basePatchNum Base patch number from file list
     * @return {boolean}
     */
    _computePatchSetDisabled(patchNum, basePatchNum) {
      if (basePatchNum === 'PARENT') { return false; }

      return this.findSortedIndex(patchNum, this._sortedRevisions) <=
          this.findSortedIndex(basePatchNum, this._sortedRevisions);
    },

        /**
     * Change active patch to the provided patch num.
     * @param {number|string} basePatchNum the base patch to be viewed.
     * @param {number|string} patchNum the patch number to be viewed.
     * @param {boolean} opt_forceParams When set to true, the resulting URL will
     *     always include the patch range, even if the requested patchNum is
     *     known to be the latest.
     */
    _changePatchNum(patchNum, basePatchNum, opt_forceParams) {
      if (!opt_forceParams) {
        let currentPatchNum;
        if (this.change.current_revision) {
          currentPatchNum =
              this.change.revisions[this.change.current_revision]._number;
        } else {
          currentPatchNum = this.computeLatestPatchNum(this.allPatchSets);
        }
        if (this.patchNumEquals(patchNum, currentPatchNum) &&
            basePatchNum === 'PARENT') {
          Gerrit.Nav.navigateToChange(this.change);
          return;
        }
      }
      Gerrit.Nav.navigateToChange(this.change, patchNum,
          basePatchNum);
    },

    _handleBasePatchChange(e) {
      this._changePatchNum(this._selectedPatchSet, e.target.value, true);
    },

    _handlePatchChange(e) {
      this._changePatchNum(e.target.value, this._diffAgainst, true);
    },

    _handlePrefsTap(e) {
      e.preventDefault();
      this.fire('open-diff-prefs');
    },

    _handleDownloadTap(e) {
      e.preventDefault();
      this.fire('open-download-dialog');
    },
  });
})();
