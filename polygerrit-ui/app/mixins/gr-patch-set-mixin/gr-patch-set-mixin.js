/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

import {dedupingMixin} from '@polymer/polymer/lib/utils/mixin.js';
import {
  computeAllPatchSets,
  computeLatestPatchNum,
  hasEditBasedOnCurrentPatchSet, hasEditPatchsetLoaded,
} from '../../utils/patch-set-util.js';

/**
 * @polymer
 * @mixinFunction
 */
export const PatchSetMixin = dedupingMixin(superClass => {
  /**
   * @polymer
   * @mixinClass
   */
  class Mixin extends superClass {
    // Mixin wraps some patch-set-util functions, so they can be used
    // in templates and computed properties

    /** @return {number|undefined} */
    computeLatestPatchNum(allPatchSets) {
      return computeLatestPatchNum(allPatchSets);
    }

    /** @return {boolean} */
    hasEditBasedOnCurrentPatchSet(allPatchSets) {
      return hasEditBasedOnCurrentPatchSet(allPatchSets);
    }

    /** @return {boolean} */
    hasEditPatchsetLoaded(patchRangeRecord) {
      return hasEditPatchsetLoaded(patchRangeRecord);
    }

    /**
     * Construct a chronological list of patch sets derived from change details.
     * Each element of this list is an object with the following properties:
     *
     *   * num {number} The number identifying the patch set
     *   * desc {!string} Optional patch set description
     *   * wip {boolean} If true, this patch set was never subject to review.
     *   * sha {string} hash of the commit
     *
     * The wip property is determined by the change's current work_in_progress
     * property and its log of change messages.
     *
     * @param {!Object} change The change details
     * @return {!Array<!Object>} Sorted list of patch set objects, as described
     *     above
     */
    computeAllPatchSets(change) {
      return computeAllPatchSets(change);
    }
  }

  return Mixin;
});
