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
  isReviewerAccountSuggestion,
  isReviewerGroupSuggestion,
  ReviewerInput,
  ServerInfo,
  SuggestedReviewerAccountInfo,
  SuggestedReviewerGroupInfo,
  Suggestion,
  UserId,
} from '../types/common';
import {AccountTag, ReviewerState} from '../constants/constants';
import {assertNever, hasOwnProperty} from './common-util';
import {
  getAccountDisplayName,
  getDisplayName,
  getGroupDisplayName,
  getUserName,
} from './display-name-util';
import {getApprovalInfo} from './label-util';
import {ParsedChangeInfo} from '../types/types';
import {AutocompleteSuggestion} from '../elements/shared/gr-autocomplete/gr-autocomplete';

export const ACCOUNT_TEMPLATE_REGEX = '<GERRIT_ACCOUNT_(\\d+)>';
// https://html.spec.whatwg.org/multipage/input.html#valid-e-mail-address
export const MENTIONS_REGEX =
  /(?:^|\s)@([a-zA-Z0-9.!#$%&'*+=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*)(?=\s+|$)/g;

export interface AccountInputDetail {
  account: AccountInput;
}

/** Supported input to be added */
export type RawAccountInput =
  | string
  | SuggestedReviewerAccountInfo
  | SuggestedReviewerGroupInfo;

// type guards for SuggestedReviewerAccountInfo and SuggestedReviewerGroupInfo
export function isAccountObject(
  x: RawAccountInput
): x is SuggestedReviewerAccountInfo {
  return !!(x as SuggestedReviewerAccountInfo).account;
}

export function isSuggestedReviewerGroupInfo(
  x: RawAccountInput
): x is SuggestedReviewerGroupInfo {
  return !!(x as SuggestedReviewerGroupInfo).group;
}

// AccountInfo with confirmation to be added as reviewer/cc.
export interface AccountInfoInput extends AccountInfo {
  _account?: boolean;
  confirmed?: boolean;
}

// GroupInfo with confirmation to be added as reviewer/cc.
export interface GroupInfoInput extends GroupInfo {
  _account?: boolean;
  confirmed?: boolean;
}

export type AccountInput = AccountInfoInput | GroupInfoInput;

export function accountKey(account: AccountInfo): AccountId | EmailAddress {
  if (account._account_id !== undefined) return account._account_id;
  if (account.email) return account.email;
  throw new Error('Account has neither _account_id nor email.');
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

export function getUserId(entry: AccountInfo | GroupInfo): UserId {
  if (isAccount(entry)) return accountKey(entry);
  if (isGroup(entry)) return entry.id;
  assertNever(entry, 'entry must be account or group');
}

export function isAccountEmailOnly(entry: AccountInfo | GroupInfo) {
  if (isGroup(entry)) return false;
  return !entry._account_id;
}

export function isAccountNewlyAdded(
  account: AccountInfo | GroupInfo,
  state?: ReviewerState,
  change?: ChangeInfo | ParsedChangeInfo
) {
  if (!change || !state) return false;
  const accounts = [...(change.reviewers[state] ?? [])];
  return !accounts.some(a => getUserId(a) === getUserId(account));
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

export function uniqueAccountId(
  account: AccountInfo,
  index: number,
  accountArray: AccountInfo[]
) {
  return (
    index ===
    accountArray.findIndex(other => account._account_id === other._account_id)
  );
}

const SELF_EXPRESSION = 'self';
const ME_EXPRESSION = 'me';

/**
 * Maps account information to a list of suggestions for autocompletion.
 *
 * @param accounts An array of account information.
 * @param predicate The search predicate (e.g., "owner", "reviewer"). This is used to
 *   prefix the suggested account/group in the autocomplete dropdown,
 *   forming a query like "predicate:value".
 * @param serverConfig The server configuration, used to determine the display name.
 * @return An array of autocomplete suggestions.
 */
function convertToSuggestion(
  accounts: AccountInfo[],
  predicate: string,
  serverConfig?: ServerInfo
): AutocompleteSuggestion[] {
  return accounts.map(account => {
    const userName = getUserName(serverConfig, account);
    return {
      label: account.name || '',
      text: account.email
        ? `${predicate}:${account.email}`
        : `${predicate}:"${userName}"`,
    };
  });
}

/**
 * Fetches account suggestions based on the provided predicate and expression.
 * It also includes "self" and "me" as suggestions if they contain expression as prefix.
 *
 * @param accountFetcher A function that fetches a list of accounts that match expression.
 * @param predicate The search predicate (e.g., "owner", "reviewer"). This is used to
 *   prefix the suggested account/group in the autocomplete dropdown,
 *   forming a query like "predicate:value".
 * @param accountPrefix The prefix of the account identifier.
 * @param serverConfig The server configuration.
 * @return A promise that resolves to an array of autocomplete suggestions.
 */
export function fetchAccountSuggestions(
  accountFetcher: (accountPrefix: string) => Promise<AccountInfo[] | undefined>,
  predicate: string,
  accountPrefix: string,
  serverConfig?: ServerInfo
): Promise<AutocompleteSuggestion[]> {
  if (accountPrefix.length === 0) {
    return Promise.resolve([]);
  }
  return accountFetcher(accountPrefix)
    .then(accounts => {
      if (!accounts) {
        return [];
      }
      return convertToSuggestion(accounts, predicate, serverConfig);
    })
    .then(accounts => {
      // When the expression supplied is a beginning substring of 'self',
      // add it as an autocomplete option.
      if (SELF_EXPRESSION.startsWith(accountPrefix)) {
        return accounts.concat([{text: predicate + ':' + SELF_EXPRESSION}]);
      } else if (ME_EXPRESSION.startsWith(accountPrefix)) {
        return accounts.concat([{text: predicate + ':' + ME_EXPRESSION}]);
      } else {
        return accounts;
      }
    });
}

export function isDetailedAccount(account?: AccountInfo) {
  // In case ChangeInfo is requested without DetailedAccount option, the
  // reviewer entry is returned as just {_account_id: 123}.
  // At least a name or an email must be set for the account to be treated as
  // "detailed".
  return (!!account?.email || !!account?.name) && !!account?._account_id;
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
 * Returns max permitted score for reviewer (other than self, current user)
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
 * Returns max permitted score for self (current user)
 */
const getSelfPermittedScore = (change: ChangeInfo, label: string): number => {
  const permittedLabels = change.permitted_labels?.[label];
  if (permittedLabels) {
    return Number(permittedLabels[permittedLabels.length - 1]);
  }
  return 0;
};

/**
 * Explains which labels the user can vote on and which score they can
 * give.
 */
export function computeVoteableText(
  change: ChangeInfo,
  reviewer: AccountInfo,
  self?: AccountInfo
) {
  if (!change || !change.labels) {
    return '';
  }
  const maxScores = [];
  for (const label of Object.keys(change.labels)) {
    let maxScore = getReviewerPermittedScore(change, reviewer, label);
    // if the reviewer is the current user, check if they can vote on the label
    // we have different logic for self and other reviewers
    if (isNaN(maxScore) && self && isSelf(reviewer, self)) {
      maxScore = getSelfPermittedScore(change, label);
    }

    if (isNaN(maxScore) || maxScore < 0) {
      continue;
    }
    const scoreLabel = maxScore > 0 ? `+${maxScore}` : `${maxScore}`;
    maxScores.push(`${label}: ${scoreLabel}`);
  }
  return maxScores.join(', ');
}

/**
 * Extracts mentioned users from a given text.
 * A user can be mentioned by triggering the mentions dropdown in a comment
 * by typing @ at the start of the comment or after a space.
 * The Mentions Regex first looks start of sentence or whitespace (?:^|\s) then
 * @ token which would have triggered the mentions dropdown and then looks
 * for the email token ending with a whitespace or end of string.
 */
export function extractMentionedUsers(text?: string): AccountInfo[] {
  if (!text) return [];
  let match;
  const users = [];
  while ((match = MENTIONS_REGEX.exec(text))) {
    users.push({
      email: match[1] as EmailAddress,
    });
  }
  return users;
}

export function toReviewInput(
  account: AccountInput,
  state: ReviewerState
): ReviewerInput {
  if (isAccount(account)) {
    return {
      reviewer: accountKey(account),
      state,
      ...(account.confirmed && {confirmed: account.confirmed}),
    };
  } else if (isGroup(account)) {
    const reviewer = decodeURIComponent(account.id) as GroupId;
    return {
      reviewer,
      state,
      ...(account.confirmed && {confirmed: account.confirmed}),
    };
  }
  throw new Error('Must be either an account or a group.');
}

export function isAccountSuggestion(s: Suggestion): s is AccountInfo {
  return (s as AccountInfo)._account_id !== undefined;
}

export function getSuggestedReviewerName(
  suggestion: Suggestion,
  config?: ServerInfo
) {
  if (isAccountSuggestion(suggestion)) {
    // Reviewer is an account suggestion from getSuggestedAccounts.
    return getAccountDisplayName(config, suggestion);
  }

  if (isReviewerAccountSuggestion(suggestion)) {
    // Reviewer is an account suggestion from getChangeSuggestedReviewers.
    return getAccountDisplayName(config, suggestion.account);
  }

  if (isReviewerGroupSuggestion(suggestion)) {
    // Reviewer is a group suggestion from getChangeSuggestedReviewers.
    return getGroupDisplayName(suggestion.group);
  }

  assertNever(suggestion, 'Received an incorrect suggestion');
}

export function getSuggestedReviewerID(suggestion: Suggestion) {
  if (isAccountSuggestion(suggestion)) {
    // Reviewer is an account suggestion from getSuggestedAccounts.
    return suggestion.email;
  }

  if (isReviewerAccountSuggestion(suggestion)) {
    // Reviewer is an account suggestion from getChangeSuggestedReviewers.
    return suggestion.account.email;
  }

  if (isReviewerGroupSuggestion(suggestion)) {
    // Reviewer is a group suggestion from getChangeSuggestedReviewers.
    // Groups are special users in Gerrit that do not have an email associated
    // with them but instead have a groupID.
    // Adding a group adds all members of that group as reviewer.
    return suggestion.group.id;
  }
  return '';
}
