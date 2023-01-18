/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  AccountInfo,
  ChangeInfo,
  DetailedLabelInfo,
  ServerInfo,
} from '../types/common';
import {ParsedChangeInfo} from '../types/types';
import {
  getAccountTemplate,
  isSelf,
  isServiceUser,
  replaceTemplates,
} from './account-util';
import {CommentThread, isMentionedThread, isUnresolved} from './comment-util';
import {hasOwnProperty} from './common-util';
import {getCodeReviewLabel} from './label-util';

export function canHaveAttention(account?: AccountInfo): boolean {
  return !!account?._account_id && !isServiceUser(account);
}

export function hasAttention(
  account?: AccountInfo,
  change?: ChangeInfo | ParsedChangeInfo
): boolean {
  return (
    canHaveAttention(account) &&
    !!change?.attention_set &&
    hasOwnProperty(change?.attention_set, account!._account_id!)
  );
}

export function getReason(
  config?: ServerInfo,
  account?: AccountInfo,
  change?: ChangeInfo | ParsedChangeInfo
) {
  if (!hasAttention(account, change)) return '';
  if (change?.attention_set === undefined) return '';
  if (account?._account_id === undefined) return '';

  const attentionSetInfo = change.attention_set[account._account_id];

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

export function getLastUpdate(account?: AccountInfo, change?: ChangeInfo) {
  if (!hasAttention(account, change)) return '';
  const entry = change!.attention_set![account!._account_id!];
  return entry?.last_update ? entry.last_update : '';
}

/**
 *  Sort order:
 * 1. The user themselves
 * 2. Human users in the attention set.
 * 3. Other human users.
 * 4. Service users.
 */
export function sortReviewers(
  r1: AccountInfo,
  r2: AccountInfo,
  change?: ChangeInfo | ParsedChangeInfo,
  selfAccount?: AccountInfo
) {
  if (selfAccount) {
    if (isSelf(r1, selfAccount)) return -1;
    if (isSelf(r2, selfAccount)) return 1;
  }
  const a1 = hasAttention(r1, change) ? 1 : 0;
  const a2 = hasAttention(r2, change) ? 1 : 0;
  const a = a2 - a1;
  if (a !== 0) return a;

  const s1 = isServiceUser(r1) ? -1 : 0;
  const s2 = isServiceUser(r2) ? -1 : 0;
  const s = s2 - s1;
  if (s !== 0) return s;

  const crLabel = getCodeReviewLabel(change?.labels ?? {}) as DetailedLabelInfo;
  let v1 =
    crLabel?.all?.find(vote => vote._account_id === r1._account_id)?.value ?? 0;
  let v2 =
    crLabel?.all?.find(vote => vote._account_id === r2._account_id)?.value ?? 0;
  if (v1 < 0) v1 = 10 - v1;
  if (v2 < 0) v2 = 10 - v2;
  return v2 - v1;
}
