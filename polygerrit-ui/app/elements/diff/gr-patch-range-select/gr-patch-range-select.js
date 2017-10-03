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
   *
   * @property {string} patchNum
   * @property {string} basePatchNum
   */

  Polymer({
    is: 'gr-patch-range-select',

    properties: {
      availablePatches: Array,
      _baseDropdownContent: {
        type: Object,
        computed: '_computeBaseDropdownContent(availablePatches, patchNum,' +
            '_sortedRevisions, revisions)',
      },
      _patchDropdownContent: {
        type: Object,
        computed: '_computePatchDropdownContent(availablePatches,' +
            'basePatchNum, _sortedRevisions, revisions)',
      },
      changeNum: String,
      comments: Array,
      /** @type {{ meta_a: !Array, meta_b: !Array}} */
      filesWeblinks: Object,
      patchNum: String,
      basePatchNum: String,
      revisions: Object,
      _sortedRevisions: Array,
    },

    observers: [
      '_updateSortedRevisions(revisions.*)',
    ],

    behaviors: [Gerrit.PatchSetBehavior],

    _computeBaseDropdownContent(availablePatches, patchNum, _sortedRevisions,
        revisions) {
      const dropdownContent = [];
      dropdownContent.push({
        text: 'Base',
        value: 'PARENT',
      });
      for (const basePatch of availablePatches) {
        const basePatchNum = basePatch.num;
        dropdownContent.push({
          disabled: this._computeLeftDisabled(
              basePatch.num, patchNum, _sortedRevisions),
          triggerText: `Patchset ${basePatchNum}`,
          text: `Patchset ${basePatchNum}` +
              this._computePatchSetCommentsString(this.comments, basePatchNum),
          mobileText: this._computeMobileText(basePatchNum, this.comments,
              revisions),
          bottomText: `${this._computePatchSetDescription(
              revisions, basePatchNum)}`,
          value: basePatch.num,
        });
      }
      return dropdownContent;
    },

    _computeMobileText(patchNum, comments, revisions) {
      return `${patchNum}` +
          `${this._computePatchSetCommentsString(this.comments, patchNum)}` +
          `${this._computePatchSetDescription(revisions, patchNum, true)}`;
    },

    _computePatchDropdownContent(availablePatches, basePatchNum,
        _sortedRevisions, revisions) {
      const dropdownContent = [];
      for (const patch of availablePatches) {
        const patchNum = patch.num;
        dropdownContent.push({
          disabled: this._computeRightDisabled(patchNum, basePatchNum,
              _sortedRevisions),
          triggerText: `Patchset ${patchNum}`,
          text: `Patchset ${patchNum}` +
              `${this._computePatchSetCommentsString(
                  this.comments, patchNum)}`,
          mobileText: this._computeMobileText(patchNum, this.comments,
              revisions),
          bottomText: `${this._computePatchSetDescription(
              revisions, patchNum)}`,
          value: patchNum,
        });
      }
      return dropdownContent;
    },

    _updateSortedRevisions(revisionsRecord) {
      const revisions = revisionsRecord.base;
      this._sortedRevisions = this.sortRevisions(Object.values(revisions));
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

    // Copied from gr-file-list
    // @todo(beckysiegel) clean up.
    _getCommentsForPath(comments, patchNum, path) {
      return (comments[path] || []).filter(c => {
        return this.patchNumEquals(c.patch_set, patchNum);
      });
    },

    // Copied from gr-file-list
    // @todo(beckysiegel) clean up.
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
        commentsStr = ' (' + numComments + ' comments';
        if (numUnresolved > 0) {
          commentsStr += ', ' + numUnresolved + ' unresolved';
        }
        commentsStr += ')';
      }
      return commentsStr;
    },

    /**
     * @param {!Array} revisions
     * @param {number|string} patchNum
     * @param {boolean=} opt_addFrontSpace
     */
    _computePatchSetDescription(revisions, patchNum, opt_addFrontSpace) {
      const rev = this.getRevisionByPatchNum(revisions, patchNum);
      return (rev && rev.description) ?
          (opt_addFrontSpace ? ' ' : '') +
          rev.description.substring(0, PATCH_DESC_MAX_LENGTH) : '';
    },

    /**
     * Catches value-change events from the patchset dropdowns and determines
     * whether or not a patch change event should be fired.
     */
    _handlePatchChange(e) {
      const detail = {patchNum: this.patchNum, basePatchNum: this.basePatchNum};
      const target = Polymer.dom(e).localTarget;

      if (target === this.$.patchNumDropdown) {
        detail.patchNum = e.detail.value;
      } else {
        detail.basePatchNum = e.detail.value;
      }

      this.dispatchEvent(
          new CustomEvent('patch-range-change', {detail, bubbles: false}));
    },
  });
})();
