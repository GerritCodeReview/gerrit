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
  ApprovalInfo,
  isDetailedLabelInfo,
  LabelInfo,
  VotingRangeInfo,
} from '../types/common';

// Name of the standard Code-Review label.
export const CODE_REVIEW = 'Code-Review';

export function getVotingRange(label?: LabelInfo): VotingRangeInfo | undefined {
  if (!label || !isDetailedLabelInfo(label)) return undefined;
  const values = Object.keys(label.values).map(v => parseInt(v, 10));
  values.sort((a, b) => a - b);
  if (!values.length) return undefined;
  return {min: values[0], max: values[values.length - 1]};
}

export function getVotingRangeOrDefault(label?: LabelInfo): VotingRangeInfo {
  const range = getVotingRange(label);
  if (!range) {
    return {min: 0, max: 0};
  }
  return range;
}

export function getMaxAccounts(label?: LabelInfo): ApprovalInfo[] {
  if (!label || !isDetailedLabelInfo(label) || !label.all) return [];
  const votingRange = getVotingRangeOrDefault(label);
  return label.all.filter(account => account.value === votingRange.max);
}
