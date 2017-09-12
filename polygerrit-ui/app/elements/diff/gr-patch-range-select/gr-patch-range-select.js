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

  /**
   * Fired when the patch range changes
   *
   * @event patch-range-change
   */

  Polymer({
    is: 'gr-patch-range-select',

    properties: {
      availablePatches: Array,
      changeNum: String,
      comments: Array,
      /** @type {{ meta_a: !Array, meta_b: !Array}} */
      filesWeblinks: Object,
      /** @type {?} */
      patchRange: Object,
      revisions: Object,
      _sortedRevisions: Array,
      _rightSelected: String,
      _leftSelected: String,
    },

    observers: [
      '_updateSortedRevisions(revisions.*)',
      '_updateSelected(patchRange.*)',
    ],

    behaviors: [Gerrit.PatchSetBehavior],

    _updateSelected() {
      this._rightSelected = this.patchRange.patchNum;
      this._leftSelected = this.patchRange.basePatchNum;
    },

    _updateSortedRevisions(revisionsRecord) {
      const revisions = revisionsRecord.base;
      this._sortedRevisions = this.sortRevisions(Object.values(revisions));
    },

    _handlePatchChange(e) {
      const leftPatch = this._leftSelected;
      const rightPatch = this._rightSelected;
      this.fire('patch-range-change', {rightPatch, leftPatch});
      e.target.blur();
    },

    _computeLeftDisabled(basePatchNum, patchNum, sortedRevisions) {
      return this.findSortedIndex(basePatchNum, sortedRevisions) >=
          this.findSortedIndex(patchNum, sortedRevisions);
    },

    _computeRightDisabled(patchNum, basePatchNum, sortedRevisions) {
      if (basePatchNum == 'PARENT') { return false; }

      return this.findSortedIndex(patchNum, sortedRevisions) <=
          this.findSortedIndex(basePatchNum, sortedRevisions);
    },

    // On page load, the dom-if for options getting added occurs after
    // the value was set in the select. This ensures that after they
    // are loaded, the correct value will get selected.  I attempted to
    // debounce these, but because they are detecting two different
    // events, sometimes the timing was off and one ended up missing.
    _synchronizeSelectionRight() {
      this.$.rightPatchSelect.value = this._rightSelected;
    },

    _synchronizeSelectionLeft() {
      this.$.leftPatchSelect.value = this._leftSelected;
    },

    // Copied from gr-file-list
    _getCommentsForPath(comments, patchNum, path) {
      return (comments[path] || []).filter(c => {
        return this.patchNumEquals(c.patch_set, patchNum);
      });
    },

    // Copied from gr-file-list
    _computeUnresolvedNum(comments, drafts, patchNum, path) {
      comments = this._getCommentsForPath(comments, patchNum, path);
      drafts = this._getCommentsForPath(drafts, patchNum, path);
      comments = comments.concat(drafts);

      // Create an object where every comment ID is the key of an unresolved
      // comment.

      const idMap = comments.reduce((acc, comment) => {
        if (comment.unresolved) {
          acc[comment.id] = true;
        }
        return acc;
      }, {});

      // Set false for the comments that are marked as parents.
      for (const comment of comments) {
        idMap[comment.in_reply_to] = false;
      }

      // The unresolved comments are the comments that still have true.
      const unresolvedLeaves = Object.keys(idMap).filter(key => {
        return idMap[key];
      });

      return unresolvedLeaves.length;
    },

    _computePatchSetCommentsString(allComments, patchNum) {
      // todo (beckysiegel) get comment strings for diff view also.
      if (!allComments) { return ''; }
      let numComments = 0;
      let numUnresolved = 0;
      for (const file in allComments) {
        if (allComments.hasOwnProperty(file)) {
          numComments += this._getCommentsForPath(
              allComments, patchNum, file).length;
          numUnresolved += this._computeUnresolvedNum(
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

    _computePatchSetDescription(revisions, patchNum) {
      const rev = this.getRevisionByPatchNum(revisions, patchNum);
      return (rev && rev.description) ?
          rev.description.substring(0, PATCH_DESC_MAX_LENGTH) : '';
    },
  });
})();
