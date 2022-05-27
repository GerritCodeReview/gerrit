/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {getBaseUrl} from './url-util';
import {ChangeStatus} from '../constants/constants';
import {
  NumericChangeId,
  PatchSetNum,
  ChangeInfo,
  AccountInfo,
  RelatedChangeAndCommitInfo,
} from '../types/common';
import {ParsedChangeInfo} from '../types/types';
import {ChangeStates} from '../elements/shared/gr-change-status/gr-change-status';

// This can be wrong! See WARNING above
interface ChangeStatusesOptions {
  mergeable: boolean; // This can be wrong! See WARNING above
  submitEnabled: boolean; // This can be wrong! See WARNING above
}

export const ChangeDiffType = {
  ADDED: 'ADDED',
  COPIED: 'COPIED',
  DELETED: 'DELETED',
  MODIFIED: 'MODIFIED',
  RENAMED: 'RENAMED',
  REWRITE: 'REWRITE',
};

// Must be kept in sync with the ListChangesOption enum and protobuf.
export const ListChangesOption = {
  LABELS: 0,
  DETAILED_LABELS: 8,

  // Return information on the current patch set of the change.
  CURRENT_REVISION: 1,
  ALL_REVISIONS: 2,

  // If revisions are included, parse the commit object.
  CURRENT_COMMIT: 3,
  ALL_COMMITS: 4,

  // If a patch set is included, include the files of the patch set.
  CURRENT_FILES: 5,
  ALL_FILES: 6,

  // If accounts are included, include detailed account info.
  DETAILED_ACCOUNTS: 7,

  // Include messages associated with the change.
  MESSAGES: 9,

  // Include allowed actions client could perform.
  CURRENT_ACTIONS: 10,

  // Set the reviewed boolean for the caller.
  REVIEWED: 11,

  // Include download commands for the caller.
  DOWNLOAD_COMMANDS: 13,

  // Include patch set weblinks.
  WEB_LINKS: 14,

  // Include consistency check results.
  CHECK: 15,

  // Include allowed change actions client could perform.
  CHANGE_ACTIONS: 16,

  // Include a copy of commit messages including review footers.
  COMMIT_FOOTERS: 17,

  // Include push certificate information along with any patch sets.
  PUSH_CERTIFICATES: 18,

  // Include change's reviewer updates.
  REVIEWER_UPDATES: 19,

  // Set the submittable boolean.
  SUBMITTABLE: 20,

  // If tracking ids are included, include detailed tracking ids info.
  TRACKING_IDS: 21,

  // Skip mergeability data.
  SKIP_MERGEABLE: 22,

  /**
   * Skip diffstat computation that compute the insertions field (number of lines inserted) and
   * deletions field (number of lines deleted)
   */
  SKIP_DIFFSTAT: 23,

  /** Include the evaluated submit requirements for the caller. */
  SUBMIT_REQUIREMENTS: 24,
};

export function listChangesOptionsToHex(...args: number[]) {
  let v = 0;
  for (let i = 0; i < args.length; i++) {
    v |= 1 << args[i];
  }
  return v.toString(16);
}

export function changeBaseURL(
  project: string,
  changeNum: NumericChangeId,
  patchNum: PatchSetNum
): string {
  let v = `${getBaseUrl()}/changes/${encodeURIComponent(project)}~${changeNum}`;
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

export function changeStatuses(
  change: ChangeInfo,
  opt_options?: ChangeStatusesOptions
): ChangeStates[] {
  const states = [];
  if (change.status === ChangeStatus.MERGED) {
    states.push(ChangeStates.MERGED);
  } else if (change.status === ChangeStatus.ABANDONED) {
    states.push(ChangeStates.ABANDONED);
  } else if (
    change.mergeable === false ||
    (opt_options && opt_options.mergeable === false)
  ) {
    // 'mergeable' prop may not always exist (@see Issue 6819)
    states.push(ChangeStates.MERGE_CONFLICT);
  }
  if (change.work_in_progress) {
    states.push(ChangeStates.WIP);
  }
  if (change.is_private) {
    states.push(ChangeStates.PRIVATE);
  }

  // If there are any pre-defined statuses, only return those. Otherwise,
  // will determine the derived status.
  if (states.length || !opt_options) {
    return states;
  }

  // If no missing requirements, either active or ready to submit.
  if (change.submittable && opt_options.submitEnabled) {
    states.push(ChangeStates.READY_TO_SUBMIT);
  } else {
    // Otherwise it is active.
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

export function isRemovableReviewer(
  change?: ChangeInfo,
  reviewer?: AccountInfo
): boolean {
  if (!change?.removable_reviewers || !reviewer) return false;
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
