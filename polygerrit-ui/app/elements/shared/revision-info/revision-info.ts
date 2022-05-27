/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {ChangeInfo, PatchSetNum} from '../../../types/common';
import {ParsedChangeInfo} from '../../../types/types';

type RevNumberToParentCountMap = {[revNumber: number]: number};

export class RevisionInfo {
  /**
   * @param change A change object resulting from a change detail
   *     call that includes revision information.
   */
  constructor(private change: ChangeInfo | ParsedChangeInfo) {}

  /**
   * Get the largest number of parents of the commit in any revision. For
   * example, with normal changes this will always return 1. For merge changes
   * wherein the revisions are merge commits this will return 2 or potentially
   * more.
   */
  getMaxParents() {
    if (!this.change || !this.change.revisions) {
      return 0;
    }
    return Object.values(this.change.revisions).reduce(
      (acc, rev) => Math.max(!rev.commit ? 0 : rev.commit.parents.length, acc),
      0
    );
  }

  /**
   * Get an object that maps revision numbers to the number of parents of the
   * commit of that revision.
   */
  getParentCountMap() {
    const result: RevNumberToParentCountMap = {};
    if (!this.change || !this.change.revisions) {
      return {};
    }
    Object.values(this.change.revisions).forEach(rev => {
      if (rev.commit) result[rev._number as number] = rev.commit.parents.length;
    });
    return result;
  }

  getParentCount(patchNum: PatchSetNum) {
    return this.getParentCountMap()[patchNum as number];
  }

  isMergeCommit(patchNum: PatchSetNum) {
    return this.getParentCount(patchNum) > 1;
  }

  /**
   * Get the commit ID of the (0-offset) indexed parent in the given revision
   * number.
   */

  getParentId(patchNum: PatchSetNum, parentIndex: number) {
    if (!this.change.revisions) return;
    const rev = Object.values(this.change.revisions).find(
      rev => rev._number === patchNum
    );
    if (!rev || !rev.commit) return;
    return rev.commit.parents[parentIndex].commit;
  }
}
