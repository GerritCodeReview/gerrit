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
  ServerInfo,
} from '../types/common';
import {AccountTag} from '../constants/constants';

export function accountKey(account: AccountInfo): AccountId | EmailAddress {
  if (account._account_id) return account._account_id;
  if (account.email) return account.email;
  throw new Error('Account has neither _account_id nor email.');
}

export function isServiceUser(account?: AccountInfo): boolean {
  return !!account?.tags?.includes(AccountTag.SERVICE_USER);
}

export function removeServiceUsers(accounts?: AccountInfo[]): AccountInfo[] {
  return accounts?.filter(a => !isServiceUser(a)) || [];
}

export function isAttentionSetEnabled(config: ServerInfo): boolean {
  return !!config?.change?.enable_attention_set;
}

export function canHaveAttention(account: AccountInfo): boolean {
  return !!account && !!account._account_id && !isServiceUser(account);
}

export function canRemoveReviewer(
  mutable: boolean,
  change?: ChangeInfo,
  reviewer?: AccountInfo
): boolean {
  if (
    !mutable ||
    change === undefined ||
    reviewer === undefined ||
    change.removable_reviewers === undefined
  ) {
    return false;
  }

  let current;
  for (let i = 0; i < change.removable_reviewers.length; i++) {
    current = change.removable_reviewers[i];
    if (
      current._account_id === reviewer._account_id ||
      (!reviewer._account_id && current.email === reviewer.email)
    ) {
      return true;
    }
  }
  return false;
}

export function hasAttention(
  config: ServerInfo,
  account: AccountInfo,
  change: ChangeInfo
): boolean {
  return (
    isAttentionSetEnabled(config) &&
    canHaveAttention(account) &&
    !!account._account_id &&
    !!change?.attention_set?.hasOwnProperty(account._account_id)
  );
}
