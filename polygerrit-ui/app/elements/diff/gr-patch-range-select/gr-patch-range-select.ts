/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import '../../../styles/shared-styles.js';
import '../../shared/gr-dropdown-list/gr-dropdown-list.js';
import '../../shared/gr-select/gr-select.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-patch-range-select_html.js';
import {GrCountStringFormatter} from '../../shared/gr-count-string-formatter/gr-count-string-formatter.js';
import {appContext} from '../../../services/app-context.js';
import {
  computeLatestPatchNum, findSortedIndex, getParentIndex,
  getRevisionByPatchNum,
  isMergeParent,
  patchNumEquals, sortRevisions,
  SPECIAL_PATCH_SET_NUM,
} from '../../../utils/patch-set-util.js';

// Maximum length for patch set descriptions.
const PATCH_DESC_MAX_LENGTH = 500;

/**
 * Fired when the patch range changes
 *
 * @event patch-range-change
 *
 * @property {string} patchNum
 * @property {string} basePatchNum
 * @extends PolymerElement
 */
class GrPatchRangeSelect extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  static get template() { return htmlTemplate; }

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

  constructor() {
    super();
    this.reporting = appContext.reportingService;
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
    ].includes(undefined)) {
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
      dropdownContent.push({...entry, disabled: this._computeLeftDisabled(
          basePatch.num, patchNum, _sortedRevisions)});
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
    ].includes(undefined)) {
      return undefined;
    }

    const dropdownContent = [];
    for (const patch of availablePatches) {
      const patchNum = patch.num;
      const entry = this._createDropdownEntry(
          patchNum, patchNum === 'edit' ? '' : 'Patchset ', _sortedRevisions,
          changeComments, this._getShaForPatch(patch));
      dropdownContent.push({
        ...entry,
        disabled: this._computeRightDisabled(
            basePatchNum, patchNum, _sortedRevisions),
      });
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
    if (!revisions) return;
    this._sortedRevisions = sortRevisions(Object.values(revisions));
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
    return findSortedIndex(basePatchNum, sortedRevisions) <=
        findSortedIndex(patchNum, sortedRevisions);
  }

  /**
   * The basePatchNum should always be <= patchNum -- because sortedRevisions
   * is sorted in reverse order (higher patchset nums first), invalid patch
   * nums have an index greater than the index of basePatchNum.
   *
   * In addition, if the current basePatchNum is 'PARENT', all patchNums are
   * valid.
   *
   * If the current basePatchNum is a parent index, then only patches that have
   * at least that many parents are valid.
   *
   * @param {number|string} basePatchNum The current selected base patch num.
   * @param {number|string} patchNum The possible patch num.
   * @param {!Array} sortedRevisions
   * @return {boolean}
   */
  _computeRightDisabled(basePatchNum, patchNum, sortedRevisions) {
    if (patchNumEquals(basePatchNum, SPECIAL_PATCH_SET_NUM.PARENT)) {
      return false;
    }

    if (isMergeParent(basePatchNum)) {
      // Note: parent indices use 1-offset.
      return this.revisionInfo.getParentCount(patchNum) <
          getParentIndex(basePatchNum);
    }

    return findSortedIndex(basePatchNum, sortedRevisions) <=
        findSortedIndex(patchNum, sortedRevisions);
  }

  _computePatchSetCommentsString(changeComments, patchNum) {
    if (!changeComments) { return; }

    const commentCount = changeComments.computeCommentCount({patchNum});
    const commentString = GrCountStringFormatter.computePluralString(
        commentCount, 'comment');

    const unresolvedCount = changeComments.computeUnresolvedNum({patchNum});
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
    const rev = getRevisionByPatchNum(revisions, patchNum);
    return (rev && rev.description) ?
      (opt_addFrontSpace ? ' ' : '') +
        rev.description.substring(0, PATCH_DESC_MAX_LENGTH) : '';
  }

  /**
   * @param {!Array} revisions
   * @param {number|string} patchNum
   */
  _computePatchSetDate(revisions, patchNum) {
    const rev = getRevisionByPatchNum(revisions, patchNum);
    return rev ? rev.created : undefined;
  }

  /**
   * Catches value-change events from the patchset dropdowns and determines
   * whether or not a patch change event should be fired.
   */
  _handlePatchChange(e) {
    const detail = {patchNum: this.patchNum, basePatchNum: this.basePatchNum};
    const target = dom(e).localTarget;
    const latestPatchNum = computeLatestPatchNum(this.availablePatches);
    if (target === this.$.patchNumDropdown) {
      if (detail.patchNum === e.detail.value) return;
      this.reporting.reportInteraction('right-patchset-changed',
          {
            previous: detail.patchNum,
            current: e.detail.value,
            latest: latestPatchNum,
            commentCount: this.changeComments.computeCommentCount(
                {patchNum: e.detail.value}),
          });
      detail.patchNum = e.detail.value;
    } else {
      if (patchNumEquals(detail.basePatchNum, e.detail.value)) return;
      this.reporting.reportInteraction('left-patchset-changed',
          {
            previous: detail.basePatchNum,
            current: e.detail.value,
            commentCount: this.changeComments.computeCommentCount(
                {patchNum: e.detail.value}),
          });
      detail.basePatchNum = e.detail.value;
    }

    this.dispatchEvent(
        new CustomEvent('patch-range-change', {detail, bubbles: false}));
  }
}

customElements.define(GrPatchRangeSelect.is, GrPatchRangeSelect);
