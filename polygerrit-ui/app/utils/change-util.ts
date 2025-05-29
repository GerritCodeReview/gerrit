/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {getBaseUrl} from './url-util';
import {ChangeStatus} from '../constants/constants';
import {
  AccountInfo,
  ChangeInfo,
  ChangeStates,
  NumericChangeId,
  PatchSetNum,
  RelatedChangeAndCommitInfo,
} from '../types/common';
import {ParsedChangeInfo} from '../types/types';
import {getUserId, isServiceUser} from './account-util';

interface ChangeStatusesOptions {
  mergeable: boolean;
  /** Is there a reverting change and if so, what status has it? */
  revertingChangeStatus?: ChangeStatus;
}

export const ChangeDiffType = {
  ADDED: 'ADDED',
  COPIED: 'COPIED',
  DELETED: 'DELETED',
  MODIFIED: 'MODIFIED',
  RENAMED: 'RENAMED',
  REWRITE: 'REWRITE',
};

export function listChangesOptionsToHex(...args: number[]) {
  let v = 0;
  for (let i = 0; i < args.length; i++) {
    v |= 1 << args[i];
  }
  return v.toString(16);
}

export function changeBaseURL(
  repo: string,
  changeNum: NumericChangeId,
  patchNum: PatchSetNum
): string {
  let v = `${getBaseUrl()}/changes/${encodeURIComponent(repo)}~${changeNum}`;
  if (patchNum) {
    v += `/revisions/${patchNum}`;
  }
  return v;
}

export function changePath(changeNum: NumericChangeId) {
  return `${getBaseUrl()}/c/${changeNum}`;
}

export function changeIsOpen(change?: ChangeInfo | ParsedChangeInfo | null) {
  return change?.status === ChangeStatus.NEW;
}

export function changeIsMerged(change?: ChangeInfo | ParsedChangeInfo | null) {
  return change?.status === ChangeStatus.MERGED;
}

export function changeIsAbandoned(
  change?: ChangeInfo | ParsedChangeInfo | null
) {
  return change?.status === ChangeStatus.ABANDONED;
}
/**
 * Get the change number from either a ChangeInfo (such as those included in
 * SubmittedTogetherInfo responses) or get the change number from a
 * RelatedChangeAndCommitInfo (such as those included in a
 * RelatedChangesInfo response).
 */
export function getChangeNumber(
  change: ChangeInfo | ParsedChangeInfo | RelatedChangeAndCommitInfo
): NumericChangeId {
  if (isChangeInfo(change)) {
    return change._number;
  }
  return change._change_number!;
}

export function changeStatuses(
  change: ChangeInfo,
  options?: ChangeStatusesOptions
): ChangeStates[] {
  const states: ChangeStates[] = [];

  if (change.status === ChangeStatus.MERGED) {
    if (options?.revertingChangeStatus === ChangeStatus.MERGED) {
      return [ChangeStates.MERGED, ChangeStates.REVERT_SUBMITTED];
    }
    if (options?.revertingChangeStatus !== undefined) {
      return [ChangeStates.MERGED, ChangeStates.REVERT_CREATED];
    }
    return [ChangeStates.MERGED];
  }
  if (change.status === ChangeStatus.ABANDONED) {
    return [ChangeStates.ABANDONED];
  }

  if (change.revert_of) {
    states.push(ChangeStates.REVERT);
  }
  if (change.mergeable === false || (options && options.mergeable === false)) {
    // 'mergeable' prop may not always exist (@see Issue 6819)
    states.push(ChangeStates.MERGE_CONFLICT);
  } else if (change.contains_git_conflicts) {
    states.push(ChangeStates.GIT_CONFLICT);
  }
  if (change.work_in_progress) {
    states.push(ChangeStates.WIP);
  }
  if (change.is_private) {
    states.push(ChangeStates.PRIVATE);
  }

  // The gr-change-list table does not want READY TO SUBMIT or ACTIVE and it
  // does not pass options.
  if (!options) {
    return states;
  }

  // The change is not submittable if there are conflicts or is WIP/private even
  // if the submit requirements are ok.
  if (
    [
      ChangeStates.MERGE_CONFLICT,
      ChangeStates.GIT_CONFLICT,
      ChangeStates.WIP,
      ChangeStates.PRIVATE,
    ].some(unsubmittableState => states.includes(unsubmittableState))
  ) {
    return states;
  }

  if (change.submittable) {
    states.push(ChangeStates.READY_TO_SUBMIT);
  }
  if (states.length === 0) {
    states.push(ChangeStates.ACTIVE);
  }
  return states;
}

export function isOwner(
  change?: ChangeInfo | ParsedChangeInfo,
  account?: AccountInfo
): boolean {
  if (!change || !account) return false;
  return change.owner?._account_id === account._account_id;
}

export function isReviewer(
  change?: ChangeInfo | ParsedChangeInfo,
  account?: AccountInfo
): boolean {
  if (!change || !account) return false;
  if (isOwner(change, account)) return false;
  const reviewers = change.reviewers.REVIEWER ?? [];
  return reviewers.some(r => r._account_id === account._account_id);
}

export function isCc(
  change?: ChangeInfo | ParsedChangeInfo,
  account?: AccountInfo
): boolean {
  if (!change || !account) return false;
  const ccs = change.reviewers.CC ?? [];
  return ccs.some(r => r._account_id === account._account_id);
}

export function isUploader(
  change?: ChangeInfo | ParsedChangeInfo,
  account?: AccountInfo
): boolean {
  if (!change || !account) return false;
  const rev = getCurrentRevision(change);
  return rev?.uploader?._account_id === account._account_id;
}

export function isInvolved(
  change?: ChangeInfo | ParsedChangeInfo,
  account?: AccountInfo
): boolean {
  const owner = isOwner(change, account);
  const uploader = isUploader(change, account);
  const reviewer = isReviewer(change, account);
  const cc = isCc(change, account);
  return owner || uploader || reviewer || cc;
}

export function roleDetails(
  change?: ChangeInfo | ParsedChangeInfo,
  account?: AccountInfo
) {
  return {
    isOwner: isOwner(change, account),
    isUploader: isUploader(change, account),
    isReviewer: isReviewer(change, account),
    isCc: isCc(change, account),
  };
}

export function getCurrentRevision(change?: ChangeInfo | ParsedChangeInfo) {
  if (!change?.revisions || !change?.current_revision) return undefined;
  return change.revisions[change.current_revision];
}

export function getRevisionKey(
  change: ChangeInfo | ParsedChangeInfo,
  patchNum: PatchSetNum
) {
  return Object.keys(change.revisions ?? []).find(
    rev => change?.revisions?.[rev]._number === patchNum
  );
}

export function hasHumanReviewer(
  change?: ChangeInfo | ParsedChangeInfo
): boolean {
  if (!change) return false;
  const reviewers = change.reviewers.REVIEWER ?? [];
  return reviewers
    .filter(r => getUserId(r) !== getUserId(change.owner))
    .some(r => !isServiceUser(r));
}

export function isRemovableReviewer(
  change?: ChangeInfo,
  reviewer?: AccountInfo
): boolean {
  if (!reviewer || !change) return false;
  if (isCc(change, reviewer)) return true;
  if (!change.removable_reviewers) return false;
  return change.removable_reviewers.some(
    account =>
      account._account_id === reviewer._account_id ||
      (!reviewer._account_id && account.email === reviewer.email)
  );
}

export function isChangeInfo(
  x: ChangeInfo | RelatedChangeAndCommitInfo | ParsedChangeInfo
): x is ChangeInfo | ParsedChangeInfo {
  return (x as ChangeInfo)._number !== undefined;
}
