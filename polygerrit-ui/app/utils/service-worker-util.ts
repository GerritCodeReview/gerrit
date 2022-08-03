/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {AccountDetailInfo} from '../api/rest-api';
import {ParsedChangeInfo} from '../types/types';
import {parseDate} from './date-util';

/**
 * Filter changes that had change in attention set after last round
 * of notifications. Filter out changes we already notified about.
 */
export function filterAttentionChangesAfter(
  changes: ParsedChangeInfo[],
  account: AccountDetailInfo,
  latestUpdateTimestampMs?: number
) {
  if (!latestUpdateTimestampMs) return changes;
  return changes.filter(change => {
    const attention_set = change.attention_set![account._account_id!];
    if (!attention_set.last_update) return false;
    const lastUpdateTimestampMs = parseDate(
      attention_set.last_update
    ).valueOf();
    return latestUpdateTimestampMs < lastUpdateTimestampMs;
  });
}
