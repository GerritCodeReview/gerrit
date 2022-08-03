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
  NumericChangeId,
  ReviewerInput,
  ServerInfo,
} from '../types/common';
import {AccountTag} from '../constants/constants';
import {assertNever, hasOwnProperty} from './common-util';
import {AccountAddition} from '../elements/shared/gr-account-list/gr-account-list';
import {getDisplayName} from './display-name-util';
import {getApprovalInfo} from './label-util';
import {RestApiService} from '../services/gr-rest-api/gr-rest-api';

export const ACCOUNT_TEMPLATE_REGEX = '<GERRIT_ACCOUNT_(\\d+)>';
const SUGGESTIONS_LIMIT = 15;
// https://html.spec.whatwg.org/multipage/input.html#valid-e-mail-address
export const MENTIONS_REGEX =
  /(?:^|\s)@([a-zA-Z0-9.!#$%&'*+=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*)(?:\s+|$)/g;

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

export function getAccountSuggestions(
  input: string,
  restApiService: RestApiService,
  canSee?: NumericChangeId,
  filterActive = false
) {
  return restApiService
    .getSuggestedAccounts(input, SUGGESTIONS_LIMIT, canSee, filterActive)
    .then(accounts => {
      if (!accounts) return [];
      const accountSuggestions = [];
      for (const account of accounts) {
        let nameAndEmail;
        if (account.email !== undefined) {
          nameAndEmail = `${account.name} <${account.email}>`;
        } else {
          nameAndEmail = account.name;
        }
        accountSuggestions.push({
          name: nameAndEmail,
          value: account._account_id?.toString(),
        });
      }
      return accountSuggestions;
    });
}

/**
 * Extracts the emails of mentioned users from a given text.
 * A user can be mentioned by triggering the mentions dropdown in a comment
 * by typing @ at the start of the comment or after a space.
 * The Mentions Regex first looks start of sentence or whitespace (?:^|\s) then
 * @ token which would have triggered the mentions dropdown and then looks
 * for the email token ending with a whitespace or end of string.
 */
export function extractMentionedEmails(text?: string): EmailAddress[] {
  if (!text) return [];
  let match;
  const emails = [];
  while ((match = MENTIONS_REGEX.exec(text))) {
    emails.push(match[1] as EmailAddress);
  }
  return emails;
}
