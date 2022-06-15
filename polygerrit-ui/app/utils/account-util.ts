/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  AccountId,
  AccountInfo,
  ChangeInfo,
  EmailAddress,
  GroupId,
  GroupInfo,
  isAccount,
  isDetailedLabelInfo,
  isGroup,
  ReviewerInput,
  ServerInfo,
} from '../types/common';
import {AccountTag, ReviewerState} from '../constants/constants';
import {assertNever, hasOwnProperty} from './common-util';
import {AccountAddition} from '../elements/shared/gr-account-list/gr-account-list';
import {getDisplayName} from './display-name-util';
import {getApprovalInfo} from './label-util';

export const ACCOUNT_TEMPLATE_REGEX = '<GERRIT_ACCOUNT_(\\d+)>';

export function accountKey(account: AccountInfo): AccountId | EmailAddress {
  if (account._account_id !== undefined) return account._account_id;
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
 * Get account in pseudonymized form, that can be send to the backend.
 *
 * If account is not present, returns anonymous user name according to config.
 */
export function getAccountTemplate(account?: AccountInfo, config?: ServerInfo) {
  return account?._account_id
    ? `<GERRIT_ACCOUNT_${account._account_id}>`
    : getDisplayName(config);
}

/**
 * Replace account templates with user display names in text, received from the backend.
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
 * Returns max permitted score for reviewer.
 */
const getReviewerPermittedScore = (
  change: ChangeInfo,
  reviewer: AccountInfo,
  label: string
) => {
  // Note (issue 7874): sometimes the "all" list is not included in change
  // detail responses, even when DETAILED_LABELS is included in options.
  if (!change?.labels) {
    return NaN;
  }
  const detailedLabel = change.labels[label];
  if (!isDetailedLabelInfo(detailedLabel) || !detailedLabel.all) {
    return NaN;
  }
  const approvalInfo = getApprovalInfo(detailedLabel, reviewer);
  if (!approvalInfo) {
    return NaN;
  }
  if (hasOwnProperty(approvalInfo, 'permitted_voting_range')) {
    if (!approvalInfo.permitted_voting_range) return NaN;
    return approvalInfo.permitted_voting_range.max;
  } else if (hasOwnProperty(approvalInfo, 'value')) {
    // If present, user can vote on the label.
    return 0;
  }
  return NaN;
};

/**
 * Explains which labels the user can vote on and which score they can
 * give.
 */
export function computeVoteableText(change: ChangeInfo, reviewer: AccountInfo) {
  if (!change || !change.labels) {
    return '';
  }
  const maxScores = [];
  for (const label of Object.keys(change.labels)) {
    const maxScore = getReviewerPermittedScore(change, reviewer, label);
    if (isNaN(maxScore) || maxScore < 0) {
      continue;
    }
    const scoreLabel = maxScore > 0 ? `+${maxScore}` : `${maxScore}`;
    maxScores.push(`${label}: ${scoreLabel}`);
  }
  return maxScores.join(', ');
}
