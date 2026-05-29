/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  AccountInfo,
  CommentThread,
  DetailedLabelInfo,
  ServerInfo,
} from '../types/common';
import {IdToAttentionSetMap, LabelNameToInfoMap} from '../api/rest-api';
import {
  getAccountTemplate,
  isSelf,
  isServiceUser,
  replaceTemplates,
} from './account-util';
import {isMentionedThread, isUnresolved} from './comment-util';
import {hasOwnProperty} from './common-util';
import {getApprovalInfo, getCodeReviewLabel} from './label-util';

export function canHaveAttention(account?: AccountInfo): boolean {
  return !!account?._account_id && !isServiceUser(account);
}

export function hasAttention(
  account?: AccountInfo,
  attention_set?: IdToAttentionSetMap
): boolean {
  return (
    canHaveAttention(account) &&
    !!attention_set &&
    hasOwnProperty(attention_set, account!._account_id!)
  );
}

export function getReason(
  config?: ServerInfo,
  account?: AccountInfo,
  attention_set?: IdToAttentionSetMap
) {
  if (!hasAttention(account, attention_set)) return '';
  if (attention_set === undefined) return '';
  if (account?._account_id === undefined) return '';

  const attentionSetInfo = attention_set[account._account_id];

  if (attentionSetInfo?.reason === undefined) return '';

  return replaceTemplates(
    attentionSetInfo.reason,
    attentionSetInfo?.reason_account ? [attentionSetInfo.reason_account] : [],
    config
  );
}

export function getMentionedReason(
  threads: CommentThread[],
  account?: AccountInfo,
  mentionedAccount?: AccountInfo,
  config?: ServerInfo
) {
  const mentionedThreads = threads
    .filter(isUnresolved)
    .filter(t => isMentionedThread(t, mentionedAccount));
  if (mentionedThreads.length > 0) {
    return `${getAccountTemplate(account, config)} mentioned you in a comment`;
  }
  return getReplyByReason(account, config);
}

export function getAddedByReason(account?: AccountInfo, config?: ServerInfo) {
  return `Added by ${getAccountTemplate(
    account,
    config
  )} using the hovercard menu`;
}

export function getRemovedByReason(account?: AccountInfo, config?: ServerInfo) {
  return `Removed by ${getAccountTemplate(
    account,
    config
  )} using the hovercard menu`;
}

export function getReplyByReason(account?: AccountInfo, config?: ServerInfo) {
  return `${getAccountTemplate(account, config)} replied on the change`;
}

export function getRemovedByIconClickReason(
  account?: AccountInfo,
  config?: ServerInfo
) {
  return `Removed by ${getAccountTemplate(
    account,
    config
  )} by clicking the attention icon`;
}

export function getLastUpdate(
  account?: AccountInfo,
  attention_set?: IdToAttentionSetMap
) {
  if (!hasAttention(account, attention_set)) return '';
  const entry = attention_set![account!._account_id!];
  return entry?.last_update ? entry.last_update : '';
}

/**
 *  Sort order:
 * 1. The user themselves
 * 2. Users in the attention set first.
 * 3. Human users first.
 * 4. Users that have voted first in this order of vote values:
 *    -2, -1, +2, +1, 0 or no vote.
 */
export function sortReviewers(
  r1: AccountInfo,
  r2: AccountInfo,
  attention_set?: IdToAttentionSetMap,
  labels?: LabelNameToInfoMap,
  selfAccount?: AccountInfo
) {
  if (selfAccount) {
    if (isSelf(r1, selfAccount)) return -1;
    if (isSelf(r2, selfAccount)) return 1;
  }
  const a1 = hasAttention(r1, attention_set) ? 1 : 0;
  const a2 = hasAttention(r2, attention_set) ? 1 : 0;
  if (a2 - a1 !== 0) return a2 - a1;

  const s1 = isServiceUser(r1) ? -1 : 0;
  const s2 = isServiceUser(r2) ? -1 : 0;
  if (s2 - s1 !== 0) return s2 - s1;

  let v1 = getCodeReviewVote(r1, labels);
  let v2 = getCodeReviewVote(r2, labels);
  // We want negative votes getting a higher score than positive votes, so
  // we choose 10 as a random number that is higher than all positive votes that
  // are in use, and then add the absolute value of the vote to that.
  // So -2 becomes 12.
  if (v1 < 0) v1 = 10 - v1;
  if (v2 < 0) v2 = 10 - v2;
  return v2 - v1;
}

/**
 * Returns the vote value for the given account on the Code-Review label.
 */
export function getCodeReviewVote(
  account?: AccountInfo,
  labels?: LabelNameToInfoMap
) {
  if (!account || !labels) return 0;
  const crLabel = getCodeReviewLabel(labels) as DetailedLabelInfo | undefined;
  if (!crLabel?.all) return 0;
  return getApprovalInfo(crLabel, account)?.value ?? 0;
}
