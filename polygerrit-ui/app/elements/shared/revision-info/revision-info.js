<!--
@license
Copyright (C) 2017 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
<link rel="import" href="../../../behaviors/gr-patch-set-behavior/gr-patch-set-behavior.html">
<script>
  (function() {
    'use strict';

    /**
     * @constructor
     * @param {Object} change A change object resulting from a change detail
     *     call that includes revision information.
     */
    function RevisionInfo(change) {
      this._change = change;
    }

    /**
     * Get the largest number of parents of the commit in any revision. For
     * example, with normal changes this will always return 1. For merge changes
     * wherein the revisions are merge commits this will return 2 or potentially
     * more.
     *
     * @return {number}
     */
    RevisionInfo.prototype.getMaxParents = function() {
      if (!this._change || !this._change.revisions) {
        return 0;
      }
      return Object.values(this._change.revisions)
          .reduce((acc, rev) => Math.max(rev.commit.parents.length, acc), 0);
    };

    /**
     * Get an object that maps revision numbers to the number of parents of the
     * commit of that revision.
     *
     * @return {!Object}
     */
    RevisionInfo.prototype.getParentCountMap = function() {
      const result = {};
      if (!this._change || !this._change.revisions) {
        return {};
      }
      Object.values(this._change.revisions)
          .forEach(rev => { result[rev._number] = rev.commit.parents.length; });
      return result;
    };

    /**
     * @param {number|string} patchNum
     * @return {number}
     */
    RevisionInfo.prototype.getParentCount = function(patchNum) {
      return this.getParentCountMap()[patchNum];
    };

    /**
     * Get the commit ID of the (0-offset) indexed parent in the given revision
     * number.
     *
     * @param {number|string} patchNum
     * @param {number} parentIndex (0-offset)
     * @return {string}
     */
    RevisionInfo.prototype.getParentId = function(patchNum, parentIndex) {
      const rev = Object.values(this._change.revisions).find(rev =>
        Gerrit.PatchSetBehavior.patchNumEquals(rev._number, patchNum));
      return rev.commit.parents[parentIndex].commit;
    };

    window.Gerrit = window.Gerrit || {};
    window.Gerrit.RevisionInfo = RevisionInfo;
  })();
</script>
