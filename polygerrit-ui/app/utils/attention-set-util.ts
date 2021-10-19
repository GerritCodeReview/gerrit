/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

import {AccountInfo, ChangeInfo, ServerInfo} from '../types/common';
import {ParsedChangeInfo} from '../types/types';
import {
  getAccountTemplate,
  isSelf,
  isServiceUser,
  replaceTemplates,
} from './account-util';
import {hasOwnProperty} from './common-util';

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

  const attentionSetInfo = change.attention_set[account._account_id!];

  if (attentionSetInfo?.reason === undefined) return '';

  return replaceTemplates(
    attentionSetInfo.reason,
    attentionSetInfo?.reason_account ? [attentionSetInfo.reason_account] : [],
    config
  );
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
  const s1 = isServiceUser(r1) ? -2 : 0;
  const s2 = isServiceUser(r2) ? -2 : 0;
  return a2 - a1 + s2 - s1;
}
