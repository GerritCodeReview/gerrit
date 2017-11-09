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
            '_sortedRevisions, changeComments)',
      },
      _patchDropdownContent: {
        type: Object,
        computed: '_computePatchDropdownContent(availablePatches,' +
            'basePatchNum, _sortedRevisions, changeComments)',
      },
      changeNum: String,
      changeComments: Object,
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
        changeComments) {
      const dropdownContent = [];
      for (const basePatch of availablePatches) {
        const basePatchNum = basePatch.num;
        dropdownContent.push({
          disabled: this._computeLeftDisabled(
              basePatch.num, patchNum, _sortedRevisions),
          triggerText: `Patchset ${basePatchNum}`,
          text: `Patchset ${basePatchNum}` +
              this._computePatchSetCommentsString(changeComments, basePatchNum),
          mobileText: this._computeMobileText(basePatchNum,
              changeComments, _sortedRevisions),
          bottomText: `${this._computePatchSetDescription(
              _sortedRevisions, basePatchNum)}`,
          value: basePatch.num,
        });
      }
      dropdownContent.push({
        text: 'Base',
        value: 'PARENT',
      });
      return dropdownContent;
    },

    _computeMobileText(patchNum, changeComments, revisions) {
      return `${patchNum}` +
          `${this._computePatchSetCommentsString(changeComments, patchNum)}` +
          `${this._computePatchSetDescription(revisions, patchNum, true)}`;
    },

    _computePatchDropdownContent(availablePatches, basePatchNum,
        _sortedRevisions, changeComments) {
      const dropdownContent = [];
      for (const patch of availablePatches) {
        const patchNum = patch.num;
        dropdownContent.push({
          disabled: this._computeRightDisabled(basePatchNum, patchNum,
              _sortedRevisions),
          triggerText: `${patchNum === 'edit' ? '': 'Patchset '}` +
              patchNum,
          text: `${patchNum === 'edit' ? '': 'Patchset '}${patchNum}` +
              `${this._computePatchSetCommentsString(
                  changeComments, patchNum)}`,
          mobileText: this._computeMobileText(patchNum, changeComments,
              _sortedRevisions),
          bottomText: `${this._computePatchSetDescription(
              _sortedRevisions, patchNum)}`,
          value: patchNum,
        });
      }
      return dropdownContent;
    },

    _updateSortedRevisions(revisionsRecord) {
      const revisions = revisionsRecord.base;
      this._sortedRevisions = this.sortRevisions(Object.values(revisions));
    },

    /**
     * The basePatchNum should always be <= patchNum -- because sortedRevisions
     * is sorted in reverse order (higher patchset nums first), invalid base
     * patch nums have an index greater than the index of patchNum.
     * @param {number|string} basePatchNum The possible base patch num.
     * @param {number|string} patchNum The current selected patch num.
     * @param {!Array} sortedRevisions
     */
    _computeLeftDisabled(basePatchNum, patchNum, sortedRevisions) {
      return this.findSortedIndex(basePatchNum, sortedRevisions) <=
          this.findSortedIndex(patchNum, sortedRevisions);
    },

    /**
     * The basePatchNum should always be <= patchNum -- because sortedRevisions
     * is sorted in reverse order (higher patchset nums first), invalid patch
     * nums have an index greater than the index of basePatchNum.
     * In addition, if the current basePatchNum is 'PARENT', all patchNums are
     * valid.
     * @param {number|string} basePatchNum The current selected base patch num.
     * @param {number|string} patchNum The possible patch num.
     * @param {!Array} sortedRevisions
     */
    _computeRightDisabled(basePatchNum, patchNum, sortedRevisions) {
      if (basePatchNum === 'PARENT') { return false; }
      return this.findSortedIndex(basePatchNum, sortedRevisions) <=
          this.findSortedIndex(patchNum, sortedRevisions);
    },


    _computePatchSetCommentsString(changeComments, patchNum) {
      if (!changeComments) { return; }

      const commentCount = changeComments.computeCommentCount(patchNum);
      const commentString = GrCountStringFormatter.computePluralString(
          commentCount, 'comment');

      const unresolvedCount = changeComments.computeUnresolvedNum(patchNum);
      const unresolvedString = GrCountStringFormatter.computeString(
          unresolvedCount, 'unresolved');

      if (!commentString.length && !unresolvedString.length) {
        return '';
      }

      return ` (${commentString}` +
          // Add a comma + space if both comments and unresolved
          (commentString && unresolvedString ? ', ' : '') +
          `${unresolvedString})`;
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
