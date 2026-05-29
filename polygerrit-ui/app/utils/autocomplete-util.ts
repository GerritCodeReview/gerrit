/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {AccountInfo, ServerInfo} from '../api/rest-api';
import {getUserName} from './display-name-util';

export interface AutocompleteSuggestion<T = string> {
  name?: string;
  label?: string;
  value?: T;
  text?: string;
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
