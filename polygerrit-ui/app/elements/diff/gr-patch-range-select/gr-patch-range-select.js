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

  // Maximum length for patch set descriptions.
  const PATCH_DESC_MAX_LENGTH = 500;

  /**
   * @appliesMixin Gerrit.PatchSetMixin
   */
  /**
   * Fired when the patch range changes
   *
   * @event patch-range-change
   *
   * @property {string} patchNum
   * @property {string} basePatchNum
   */
  class GrPatchRangeSelect extends Polymer.mixinBehaviors( [
    Gerrit.PatchSetBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-patch-range-select'; }

    static get properties() {
      return {
        availablePatches: Array,
        _baseDropdownContent: {
          type: Object,
          computed: '_computeBaseDropdownContent(availablePatches, patchNum,' +
            '_sortedRevisions, changeComments, revisionInfo)',
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
        revisionInfo: Object,
        _sortedRevisions: Array,
      };
    }

    static get observers() {
      return [
        '_updateSortedRevisions(revisions.*)',
      ];
    }

    _getShaForPatch(patch) {
      return patch.sha.substring(0, 10);
    }

    _computeBaseDropdownContent(availablePatches, patchNum, _sortedRevisions,
        changeComments, revisionInfo) {
      // Polymer 2: check for undefined
      if ([
        availablePatches,
        patchNum,
        _sortedRevisions,
        changeComments,
        revisionInfo,
      ].some(arg => arg === undefined)) {
        return undefined;
      }

      const parentCounts = revisionInfo.getParentCountMap();
      const currentParentCount = parentCounts.hasOwnProperty(patchNum) ?
        parentCounts[patchNum] : 1;
      const maxParents = revisionInfo.getMaxParents();
      const isMerge = currentParentCount > 1;

      const dropdownContent = [];
      for (const basePatch of availablePatches) {
        const basePatchNum = basePatch.num;
        const entry = this._createDropdownEntry(basePatchNum, 'Patchset ',
            _sortedRevisions, changeComments, this._getShaForPatch(basePatch));
        dropdownContent.push(Object.assign({}, entry, {
          disabled: this._computeLeftDisabled(
              basePatch.num, patchNum, _sortedRevisions),
        }));
      }

      dropdownContent.push({
        text: isMerge ? 'Auto Merge' : 'Base',
        value: 'PARENT',
      });

      for (let idx = 0; isMerge && idx < maxParents; idx++) {
        dropdownContent.push({
          disabled: idx >= currentParentCount,
          triggerText: `Parent ${idx + 1}`,
          text: `Parent ${idx + 1}`,
          mobileText: `Parent ${idx + 1}`,
          value: -(idx + 1),
        });
      }

      return dropdownContent;
    }

    _computeMobileText(patchNum, changeComments, revisions) {
      return `${patchNum}` +
          `${this._computePatchSetCommentsString(changeComments, patchNum)}` +
          `${this._computePatchSetDescription(revisions, patchNum, true)}`;
    }

    _computePatchDropdownContent(availablePatches, basePatchNum,
        _sortedRevisions, changeComments) {
      // Polymer 2: check for undefined
      if ([
        availablePatches,
        basePatchNum,
        _sortedRevisions,
        changeComments,
      ].some(arg => arg === undefined)) {
        return undefined;
      }

      const dropdownContent = [];
      for (const patch of availablePatches) {
        const patchNum = patch.num;
        const entry = this._createDropdownEntry(
            patchNum, patchNum === 'edit' ? '' : 'Patchset ', _sortedRevisions,
            changeComments, this._getShaForPatch(patch));
        dropdownContent.push(Object.assign({}, entry, {
          disabled: this._computeRightDisabled(basePatchNum, patchNum,
              _sortedRevisions),
        }));
      }
      return dropdownContent;
    }

    _computeText(patchNum, prefix, changeComments, sha) {
      return `${prefix}${patchNum}` +
        `${this._computePatchSetCommentsString(changeComments, patchNum)}` +
          (` | ${sha}`);
    }

    _createDropdownEntry(patchNum, prefix, sortedRevisions, changeComments,
        sha) {
      const entry = {
        triggerText: `${prefix}${patchNum}`,
        text: this._computeText(patchNum, prefix, changeComments, sha),
        mobileText: this._computeMobileText(patchNum, changeComments,
            sortedRevisions),
        bottomText: `${this._computePatchSetDescription(
            sortedRevisions, patchNum)}`,
        value: patchNum,
      };
      const date = this._computePatchSetDate(sortedRevisions, patchNum);
      if (date) {
        entry['date'] = date;
      }
      return entry;
    }

    _updateSortedRevisions(revisionsRecord) {
      const revisions = revisionsRecord.base;
      this._sortedRevisions = this.sortRevisions(Object.values(revisions));
    }

    /**
     * The basePatchNum should always be <= patchNum -- because sortedRevisions
     * is sorted in reverse order (higher patchset nums first), invalid base
     * patch nums have an index greater than the index of patchNum.
     *
     * @param {number|string} basePatchNum The possible base patch num.
     * @param {number|string} patchNum The current selected patch num.
     * @param {!Array} sortedRevisions
     */
    _computeLeftDisabled(basePatchNum, patchNum, sortedRevisions) {
      return this.findSortedIndex(basePatchNum, sortedRevisions) <=
          this.findSortedIndex(patchNum, sortedRevisions);
    }

    /**
     * The basePatchNum should always be <= patchNum -- because sortedRevisions
     * is sorted in reverse order (higher patchset nums first), invalid patch
     * nums have an index greater than the index of basePatchNum.
     *
     * In addition, if the current basePatchNum is 'PARENT', all patchNums are
     * valid.
     *
     * If the curent basePatchNum is a parent index, then only patches that have
     * at least that many parents are valid.
     *
     * @param {number|string} basePatchNum The current selected base patch num.
     * @param {number|string} patchNum The possible patch num.
     * @param {!Array} sortedRevisions
     * @return {boolean}
     */
    _computeRightDisabled(basePatchNum, patchNum, sortedRevisions) {
      if (this.patchNumEquals(basePatchNum, 'PARENT')) { return false; }

      if (this.isMergeParent(basePatchNum)) {
        // Note: parent indices use 1-offset.
        return this.revisionInfo.getParentCount(patchNum) <
            this.getParentIndex(basePatchNum);
      }

      return this.findSortedIndex(basePatchNum, sortedRevisions) <=
          this.findSortedIndex(patchNum, sortedRevisions);
    }

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
    }

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
    }

    /**
     * @param {!Array} revisions
     * @param {number|string} patchNum
     */
    _computePatchSetDate(revisions, patchNum) {
      const rev = this.getRevisionByPatchNum(revisions, patchNum);
      return rev ? rev.created : undefined;
    }

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
    }
  }

  customElements.define(GrPatchRangeSelect.is, GrPatchRangeSelect);
})();
