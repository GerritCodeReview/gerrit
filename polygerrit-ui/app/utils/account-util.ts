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

import {
  AccountId,
  AccountInfo,
  ChangeInfo,
  EmailAddress,
  GroupId,
  GroupInfo,
  isAccount,
  isGroup,
  ReviewerInput,
  ServerInfo,
} from '../types/common';
import {AccountTag, ReviewerState} from '../constants/constants';
import {assertNever} from './common-util';
import {AccountAddition} from '../elements/shared/gr-account-list/gr-account-list';
import {getDisplayName} from './display-name-util';
import {hasAttention} from './attention-set-util';
import {ParsedChangeInfo} from '../types/types';

export const ACCOUNT_TEMPLATE_REGEX = '<GERRIT_ACCOUNT_(\\d+)>';

export function accountKey(account: AccountInfo): AccountId | EmailAddress {
  if (account._account_id) return account._account_id;
  if (account.email) return account.email;
  throw new Error('Account has neither _account_id nor email.');
}

export function mapReviewer(addition: AccountAddition): ReviewerInput {
  if (addition.account) {
    return {reviewer: accountKey(addition.account)};
  }
  if (addition.group) {
    const reviewer = decodeURIComponent(addition.group.id) as GroupId;
    const confirmed = addition.group.confirmed;
    return {reviewer, confirmed};
  }
  throw new Error('Reviewer must be either an account or a group.');
}

export function isReviewerOrCC(
  change: ChangeInfo,
  reviewerAddition: AccountAddition
): boolean {
  const reviewers = [
    ...(change.reviewers[ReviewerState.CC] ?? []),
    ...(change.reviewers[ReviewerState.REVIEWER] ?? []),
  ];
  const reviewer = mapReviewer(reviewerAddition);
  return reviewers.some(r => accountOrGroupKey(r) === reviewer.reviewer);
}

export function isServiceUser(account?: AccountInfo): boolean {
  return !!account?.tags?.includes(AccountTag.SERVICE_USER);
}

export function isSelf(account?: AccountInfo, self?: AccountInfo): boolean {
  return account?._account_id === self?._account_id;
}

export function removeServiceUsers(accounts?: AccountInfo[]): AccountInfo[] {
  return accounts?.filter(a => !isServiceUser(a)) || [];
}

export function hasSameAvatar(account?: AccountInfo, other?: AccountInfo) {
  return account?.avatars?.[0]?.url === other?.avatars?.[0]?.url;
}

export function accountOrGroupKey(entry: AccountInfo | GroupInfo) {
  if (isAccount(entry)) return accountKey(entry);
  if (isGroup(entry)) return entry.id;
  assertNever(entry, 'entry must be account or group');
}

export function uniqueDefinedAvatar(
  account: AccountInfo,
  index: number,
  accountArray: AccountInfo[]
) {
  return (
    index === accountArray.findIndex(other => hasSameAvatar(account, other))
  );
}

/**
 * @desc Get account in pseudonymized form, that can be send to the backend.
 *
 * If account is not present, returns anonymous user name according to config.
 */
export function getAccountTemplate(account?: AccountInfo, config?: ServerInfo) {
  return account?._account_id
    ? `<GERRIT_ACCOUNT_${account._account_id}>`
    : getDisplayName(config);
}

/**
 * @desc Replace account templates with user display names in text, received from the backend.
 */
export function replaceTemplates(
  text: string,
  accountsInText?: AccountInfo[],
  config?: ServerInfo
) {
  return text.replace(
    new RegExp(ACCOUNT_TEMPLATE_REGEX, 'g'),
    (_accountIdTemplate, accountId) => {
      const parsedAccountId = Number(accountId) as AccountId;
      const accountInText = (accountsInText || []).find(
        account => account._account_id === parsedAccountId
      );
      if (!accountInText) {
        return `Gerrit Account ${parsedAccountId}`;
      }
      return getDisplayName(config, accountInText);
    }
  );
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
