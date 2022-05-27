/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  ChangeInfo,
  isQuickLabelInfo,
  SubmitRequirementResultInfo,
  SubmitRequirementStatus,
  LabelNameToValuesMap,
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
import {ParsedChangeInfo} from '../types/types';
import {assertNever, unique, hasOwnProperty} from './common-util';

export interface Label {
  name: string;
  value: string | null;
}

// Name of the standard Code-Review label.
export enum StandardLabels {
  CODE_REVIEW = 'Code-Review',
  CODE_OWNERS = 'Code Owners',
  PRESUBMIT_VERIFIED = 'Presubmit-Verified',
}

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

export function getLabelStatus(label?: LabelInfo, vote?: number): LabelStatus {
  if (!label) return LabelStatus.NEUTRAL;
  if (isDetailedLabelInfo(label)) {
    const value = vote ?? getRepresentativeValue(label);
    const range = getVotingRangeOrDefault(label);
    if (value < 0) {
      return value === range.min ? LabelStatus.REJECTED : LabelStatus.DISLIKED;
    }
    if (value > 0) {
      return value === range.max
        ? LabelStatus.APPROVED
        : LabelStatus.RECOMMENDED;
    }
  } else if (isQuickLabelInfo(label)) {
    if (label.approved) return LabelStatus.APPROVED;
    if (label.rejected) return LabelStatus.REJECTED;
    if (label.disliked) return LabelStatus.DISLIKED;
    if (label.recommended) return LabelStatus.RECOMMENDED;
  }
  return LabelStatus.NEUTRAL;
}

export function hasNeutralStatus(
  label: DetailedLabelInfo,
  approvalInfo?: ApprovalInfo
) {
  if (approvalInfo?.value === undefined) return true;
  return getLabelStatus(label, approvalInfo.value) === LabelStatus.NEUTRAL;
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

export function hasVoted(label: LabelInfo, account: AccountInfo) {
  if (isDetailedLabelInfo(label)) {
    return !hasNeutralStatus(label, getApprovalInfo(label, account));
  } else if (isQuickLabelInfo(label)) {
    return (
      label.approved?._account_id === account._account_id ||
      label.rejected?._account_id === account._account_id
    );
  }
  return false;
}

export function canVote(label: DetailedLabelInfo, account: AccountInfo) {
  const approvalInfo = getApprovalInfo(label, account);
  if (!approvalInfo) return false;
  if (approvalInfo.permitted_voting_range) {
    return approvalInfo.permitted_voting_range.max > 0;
  }
  // If value present, user can vote on the label.
  return approvalInfo.value !== undefined;
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
      approval => !hasNeutralStatus(labelInfo, approval)
    );
  }
  if (isQuickLabelInfo(labelInfo)) {
    return (
      !!labelInfo.rejected ||
      !!labelInfo.approved ||
      !!labelInfo.recommended ||
      !!labelInfo.disliked
    );
  }
  return false;
}

export function labelCompare(labelName1: string, labelName2: string) {
  if (
    labelName1 === StandardLabels.CODE_REVIEW &&
    labelName2 === StandardLabels.CODE_REVIEW
  )
    return 0;
  if (labelName1 === StandardLabels.CODE_REVIEW) return -1;
  if (labelName2 === StandardLabels.CODE_REVIEW) return 1;

  return labelName1.localeCompare(labelName2);
}

export function getCodeReviewLabel(
  labels: LabelNameToInfoMap
): LabelInfo | undefined {
  for (const label of Object.keys(labels)) {
    if (label === StandardLabels.CODE_REVIEW) {
      return labels[label];
    }
  }
  return;
}

function extractLabelsFrom(expression: string) {
  const pattern = new RegExp('label[0-9]*:([\\w-]+)', 'g');
  const labels = [];
  let match;
  while ((match = pattern.exec(expression)) !== null) {
    labels.push(match[1]);
  }
  return labels;
}

export function extractAssociatedLabels(
  requirement: SubmitRequirementResultInfo,
  type: 'all' | 'onlyOverride' | 'onlySubmittability' = 'all'
): string[] {
  let labels: string[] = [];
  if (requirement.submittability_expression_result && type !== 'onlyOverride') {
    labels = labels.concat(
      extractLabelsFrom(requirement.submittability_expression_result.expression)
    );
  }
  if (requirement.override_expression_result && type !== 'onlySubmittability') {
    labels = labels.concat(
      extractLabelsFrom(requirement.override_expression_result.expression)
    );
  }
  return labels.filter(unique);
}

export function iconForStatus(status: SubmitRequirementStatus) {
  switch (status) {
    case SubmitRequirementStatus.SATISFIED:
      return 'check-circle-filled';
    case SubmitRequirementStatus.UNSATISFIED:
      return 'block';
    case SubmitRequirementStatus.OVERRIDDEN:
      return 'overridden';
    case SubmitRequirementStatus.NOT_APPLICABLE:
      return 'info';
    case SubmitRequirementStatus.ERROR:
      return 'error';
    case SubmitRequirementStatus.FORCED:
      return 'check-circle-filled';
    default:
      assertNever(status, `Unsupported status: ${status}`);
  }
}

/**
 * Show only applicable.
 */
export function getRequirements(change?: ParsedChangeInfo | ChangeInfo) {
  return (change?.submit_requirements ?? []).filter(
    req => req.status !== SubmitRequirementStatus.NOT_APPLICABLE
  );
}

// TODO(milutin): This may be temporary for demo purposes
export const PRIORITY_REQUIREMENTS_ORDER: string[] = [
  StandardLabels.CODE_REVIEW,
  StandardLabels.CODE_OWNERS,
  StandardLabels.PRESUBMIT_VERIFIED,
];
export function orderSubmitRequirements(
  requirements: SubmitRequirementResultInfo[]
) {
  let priorityRequirementList: SubmitRequirementResultInfo[] = [];
  for (const label of PRIORITY_REQUIREMENTS_ORDER) {
    const priorityRequirement = requirements.filter(r => r.name === label);
    priorityRequirementList =
      priorityRequirementList.concat(priorityRequirement);
  }
  const nonPriorityRequirements = requirements.filter(
    r => !PRIORITY_REQUIREMENTS_ORDER.includes(r.name)
  );
  return priorityRequirementList.concat(nonPriorityRequirements);
}

function getStringLabelValue(
  labels: LabelNameToInfoMap,
  labelName: string,
  numberValue?: number
): string {
  const detailedInfo = labels[labelName] as DetailedLabelInfo;
  if (detailedInfo.values) {
    for (const labelValue of Object.keys(detailedInfo.values)) {
      if (Number(labelValue) === numberValue) {
        return labelValue;
      }
    }
  }
  // TODO: This code is sometimes executed with numberValue taking the
  // values 0 and undefined.
  // For now it is unclear how this is happening, ideally this code should
  // never be executed.
  return `${numberValue}`;
}

export function getDefaultValue(
  labels?: LabelNameToInfoMap,
  labelName?: string
) {
  if (!labelName || !labels?.[labelName]) return undefined;
  const labelInfo = labels[labelName] as DetailedLabelInfo;
  return labelInfo.default_value;
}

export function getVoteForAccount(
  labelName: string,
  account?: AccountInfo,
  change?: ParsedChangeInfo | ChangeInfo
): string | null {
  const labels = change?.labels;
  if (!account || !labels) return null;
  const votes = labels[labelName] as DetailedLabelInfo;
  if (!votes.all?.length) return null;
  for (let i = 0; i < votes.all.length; i++) {
    if (votes.all[i]._account_id === account._account_id) {
      return getStringLabelValue(labels, labelName, votes.all[i].value);
    }
  }
  return null;
}

export function computeOrderedLabelValues(
  permittedLabels?: LabelNameToValuesMap
) {
  if (!permittedLabels) return [];
  const labels = Object.keys(permittedLabels);
  const values: Set<number> = new Set();
  for (const label of labels) {
    for (const value of permittedLabels[label]) {
      values.add(Number(value));
    }
  }

  return Array.from(values.values()).sort((a, b) => a - b);
}

export function mergeLabelInfoMaps(
  a?: LabelNameToInfoMap,
  b?: LabelNameToInfoMap
): LabelNameToInfoMap {
  if (!a || !b) return {};
  const mergedMap: LabelNameToInfoMap = {};
  for (const key of Object.keys(a)) {
    if (!hasOwnProperty(b, key)) continue;
    mergedMap[key] = a[key];
  }
  return mergedMap;
}

export function mergeLabelMaps(
  a?: LabelNameToValuesMap,
  b?: LabelNameToValuesMap
): LabelNameToValuesMap {
  if (!a || !b) return {};
  const mergedMap: LabelNameToValuesMap = {};
  for (const key of Object.keys(a)) {
    if (!hasOwnProperty(b, key)) continue;
    mergedMap[key] = mergeLabelValues(a[key], b[key]);
  }
  return mergedMap;
}

export function mergeLabelValues(a: string[], b: string[]) {
  return a.filter(value => b.includes(value));
}

export function computeLabels(
  account?: AccountInfo,
  change?: ParsedChangeInfo | ChangeInfo
): Label[] {
  if (!account) return [];
  const labelsObj = change?.labels;
  if (!labelsObj) return [];
  return Object.keys(labelsObj)
    .sort(labelCompare)
    .map(key => {
      return {
        name: key,
        value: getVoteForAccount(key, account, change),
      };
    });
}

export function getTriggerVotes(change?: ParsedChangeInfo | ChangeInfo) {
  const allLabels = Object.keys(change?.labels ?? {});
  // Normally there is utility method getRequirements, which filter out
  // not_applicable requirements. In this case we don't want to filter out them,
  // because trigger votes are labels not associated with any requirement.
  const submitReqs = change?.submit_requirements ?? [];
  const labelAssociatedWithSubmitReqs = submitReqs
    .flatMap(req => extractAssociatedLabels(req))
    .filter(unique);
  return allLabels.filter(
    label => !labelAssociatedWithSubmitReqs.includes(label)
  );
}
