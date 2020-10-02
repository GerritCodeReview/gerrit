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

import {AccountInfo, ChangeInfo} from '../types/common';
import {isServiceUser} from './account-util';

// You would typically use a ServerInfo here, but this utility does not care
// about all the other parameters in that object.
interface SimpleServerInfo {
  change?: {
    enable_attention_set?: boolean;
  };
}

const CONFIG_ENABLED: SimpleServerInfo = {
  change: {enable_attention_set: true},
};

export function isAttentionSetEnabled(config?: SimpleServerInfo): boolean {
  return !!config?.change?.enable_attention_set;
}

export function canHaveAttention(account?: AccountInfo): boolean {
  return !!account?._account_id && !isServiceUser(account);
}

export function hasAttention(
  config?: SimpleServerInfo,
  account?: AccountInfo,
  change?: ChangeInfo
): boolean {
  return (
    isAttentionSetEnabled(config) &&
    canHaveAttention(account) &&
    !!change?.attention_set?.hasOwnProperty(account!._account_id!)
  );
}

export function getReason(account?: AccountInfo, change?: ChangeInfo) {
  if (!hasAttention(CONFIG_ENABLED, account, change)) return '';
  const entry = change!.attention_set![account!._account_id!];
  return entry?.reason ? entry.reason : '';
}

export function getLastUpdate(account?: AccountInfo, change?: ChangeInfo) {
  if (!hasAttention(CONFIG_ENABLED, account, change)) return '';
  const entry = change!.attention_set![account!._account_id!];
  return entry?.last_update ? entry.last_update : '';
}
