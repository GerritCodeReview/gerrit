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
import {AccountTag} from '../constants/constants';

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
  return !!account && !isServiceUser(account);
}

export function hasAttention(
  config: ServerInfo,
  account: AccountInfo,
  change: ChangeInfo
): boolean {
  return (
    isAttentionSetEnabled(config) &&
    canHaveAttention(account) &&
    !!change?.attention_set?.hasOwnProperty(account._account_id)
  );
}
