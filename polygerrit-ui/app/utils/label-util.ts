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
  AccountInfo,
  ApprovalInfo,
  DetailedLabelInfo,
  isDetailedLabelInfo,
  LabelInfo,
  VotingRangeInfo,
} from '../types/common';

// Name of the standard Code-Review label.
export const CODE_REVIEW = 'Code-Review';

export function getVotingRange(label?: LabelInfo): VotingRangeInfo | undefined {
  if (!label || !isDetailedLabelInfo(label) || !label.values) return undefined;
  const values = Object.keys(label.values).map(v => Number(v));
  values.sort((a, b) => a - b);
  if (!values.length) return undefined;
  return {min: values[0], max: values[values.length - 1]};
}

export function getVotingRangeOrDefault(label?: LabelInfo): VotingRangeInfo {
  const range = getVotingRange(label);
  return range ? range : {min: 0, max: 0};
}

export function getMaxAccounts(label?: LabelInfo): ApprovalInfo[] {
  if (!label || !isDetailedLabelInfo(label) || !label.all) return [];
  const votingRange = getVotingRangeOrDefault(label);
  return label.all.filter(account => account.value === votingRange.max);
}

export function getApprovalInfo(
  label: DetailedLabelInfo,
  account: AccountInfo
): ApprovalInfo | undefined {
  return label.all?.filter(x => x._account_id === account._account_id)[0];
}
