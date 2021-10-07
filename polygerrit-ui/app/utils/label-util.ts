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
  isQuickLabelInfo,
  SubmitRequirementResultInfo,
  SubmitRequirementStatus,
} from '../api/rest-api';
import {
  AccountInfo,
  ApprovalInfo,
  DetailedLabelInfo,
  isDetailedLabelInfo,
  LabelInfo,
  LabelNameToInfoMap,
  VotingRangeInfo,
} from '../types/common';
import {assertNever, unique} from './common-util';

// Name of the standard Code-Review label.
export const CODE_REVIEW = 'Code-Review';

export enum LabelStatus {
  APPROVED = 'APPROVED',
  REJECTED = 'REJECTED',
  RECOMMENDED = 'RECOMMENDED',
  DISLIKED = 'DISLIKED',
  NEUTRAL = 'NEUTRAL',
}

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

/**
 * If we don't know the label config, then we still need some way to decide
 * which vote value is the most important one, so we apply the standard rule
 * of a Code-Review label, where -2 blocks. So the most negative vote is
 * regarded as representative, if its absolute value is greater than or equal
 * to the most positive vote.
 */
export function getRepresentativeValue(label?: DetailedLabelInfo): number {
  if (!label?.all) return 0;
  const allValues = label.all.map(approvalInfo => approvalInfo.value ?? 0);
  if (allValues.length === 0) return 0;
  const max = Math.max(...allValues);
  const min = Math.min(...allValues);
  return max > -min ? max : min;
}

export function getLabelStatus(
  label?: DetailedLabelInfo,
  vote?: number
): LabelStatus {
  const value = vote ?? getRepresentativeValue(label);
  const range = getVotingRangeOrDefault(label);
  if (value < 0) {
    return value === range.min ? LabelStatus.REJECTED : LabelStatus.DISLIKED;
  }
  if (value > 0) {
    return value === range.max ? LabelStatus.APPROVED : LabelStatus.RECOMMENDED;
  }
  return LabelStatus.NEUTRAL;
}

export function classForLabelStatus(status: LabelStatus) {
  switch (status) {
    case LabelStatus.APPROVED:
      return 'max';
    case LabelStatus.RECOMMENDED:
      return 'positive';
    case LabelStatus.DISLIKED:
      return 'negative';
    case LabelStatus.REJECTED:
      return 'min';
    case LabelStatus.NEUTRAL:
      return 'neutral';
    default:
      assertNever(status, `Unsupported status: ${status}`);
  }
}

export function valueString(value?: number) {
  if (!value) return ' 0';
  let s = `${value}`;
  if (value > 0) s = `+${s}`;
  return s;
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

export function getAllUniqueApprovals(labelInfo?: LabelInfo) {
  if (!labelInfo || !isDetailedLabelInfo(labelInfo)) return [];
  const uniqueApprovals = (labelInfo.all ?? [])
    .filter(
      (approvalInfo, index, array) =>
        index === array.findIndex(other => other.value === approvalInfo.value)
    )
    .sort((a, b) => -(a.value ?? 0) + (b.value ?? 0));
  return uniqueApprovals;
}

export function hasVotes(labelInfo: LabelInfo): boolean {
  if (isDetailedLabelInfo(labelInfo)) {
    return (labelInfo.all ?? []).some(
      approval =>
        getLabelStatus(labelInfo, approval.value) !== LabelStatus.NEUTRAL
    );
  }
  if (isQuickLabelInfo(labelInfo)) {
    return !!labelInfo.rejected || !!labelInfo.approved;
  }
  return false;
}

export function labelCompare(labelName1: string, labelName2: string) {
  if (labelName1 === CODE_REVIEW && labelName2 === CODE_REVIEW) return 0;
  if (labelName1 === CODE_REVIEW) return -1;
  if (labelName2 === CODE_REVIEW) return 1;

  return labelName1.localeCompare(labelName2);
}

export function getCodeReviewLabel(
  labels: LabelNameToInfoMap
): LabelInfo | undefined {
  for (const label of Object.keys(labels)) {
    if (label === CODE_REVIEW) {
      return labels[label];
    }
  }
  return;
}

export function extractAssociatedLabels(
  requirement: SubmitRequirementResultInfo
): string[] {
  const pattern = new RegExp('label[0-9]*:([\\w-]+)', 'g');
  const labels = [];
  let match;
  while (
    (match = pattern.exec(
      requirement.submittability_expression_result.expression
    )) !== null
  ) {
    labels.push(match[1]);
  }
  return labels.filter(unique);
}

export function iconForStatus(status: SubmitRequirementStatus) {
  switch (status) {
    case SubmitRequirementStatus.SATISFIED:
      return 'check';
    case SubmitRequirementStatus.UNSATISFIED:
      return 'close';
    case SubmitRequirementStatus.OVERRIDDEN:
      return 'warning';
    case SubmitRequirementStatus.NOT_APPLICABLE:
      return 'info';
    default:
      assertNever(status, `Unsupported status: ${status}`);
  }
}
