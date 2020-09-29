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
import {getBaseUrl} from './url-util';
import {ChangeStatus} from '../constants/constants';
import {NumericChangeId, PatchSetNum, ChangeInfo} from '../types/common';
import {ParsedChangeInfo} from '../elements/shared/gr-rest-api-interface/gr-reviewer-updates-parser';
import {AccountInfo} from '../types/common';

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

export function changeIsOpen(change?: ChangeInfo | ParsedChangeInfo) {
  return change?.status === ChangeStatus.NEW;
}

export function changeStatuses(
  change: ChangeInfo,
  opt_options?: ChangeStatusesOptions
) {
  const states = [];
  if (change.status === ChangeStatus.MERGED) {
    states.push('Merged');
  } else if (change.status === ChangeStatus.ABANDONED) {
    states.push('Abandoned');
  } else if (
    change.mergeable === false ||
    (opt_options && opt_options.mergeable === false)
  ) {
    // 'mergeable' prop may not always exist (@see Issue 6819)
    states.push('Merge Conflict');
  }
  if (change.work_in_progress) {
    states.push('WIP');
  }
  if (change.is_private) {
    states.push('Private');
  }

  // If there are any pre-defined statuses, only return those. Otherwise,
  // will determine the derived status.
  if (states.length || !opt_options) {
    return states;
  }

  // If no missing requirements, either active or ready to submit.
  if (change.submittable && opt_options.submitEnabled) {
    states.push('Ready to submit');
  } else {
    // Otherwise it is active.
    states.push('Active');
  }
  return states;
}

export function isOwner(change?: ChangeInfo, account?: AccountInfo) {
  if (!change || !account) return false;
  return change.owner?._account_id === account._account_id;
}

export function changeStatusString(change: ChangeInfo) {
  return changeStatuses(change).join(', ');
}
