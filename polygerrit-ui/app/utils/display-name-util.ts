/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {AccountInfo, GroupInfo, ServerInfo} from '../types/common';
import {DefaultDisplayNameConfig} from '../constants/constants';

const ANONYMOUS_NAME = 'Anonymous';

export function getUserName(
  config?: ServerInfo,
  account?: AccountInfo
): string {
  if (account?.name) {
    return account.name;
  } else if (account?.username) {
    return account.username;
  } else if (account?.email) {
    return account.email;
  } else if (
    config &&
    config.user &&
    config.user.anonymous_coward_name !== 'Anonymous Coward'
  ) {
    return config.user.anonymous_coward_name;
  }

  return ANONYMOUS_NAME;
}

export function getDisplayName(
  config?: ServerInfo,
  account?: AccountInfo,
  firstNameOnly = false
): string {
  if (account?.display_name) {
    return account.display_name;
  }
  if (!account || !account.name) {
    return getUserName(config, account);
  }
  const configDefault = config?.accounts?.default_display_name;
  if (firstNameOnly || configDefault === DefaultDisplayNameConfig.FIRST_NAME) {
    return account.name.trim().split(' ')[0];
  }
  if (configDefault === DefaultDisplayNameConfig.USERNAME && account.username) {
    return account.username;
  }
  // Treat every other value as FULL_NAME.
  return account.name;
}

export function getAccountDisplayName(
  config: ServerInfo | undefined,
  account: AccountInfo
) {
  const reviewerName = getUserName(config, account);
  const reviewerEmail = _accountEmail(account.email);
  const reviewerStatus = account.status ? '(' + account.status + ')' : '';
  return [reviewerName, reviewerEmail, reviewerStatus]
    .filter(p => p.length > 0)
    .join(' ');
}

function _accountEmail(email?: string) {
  if (typeof email !== 'undefined') {
    return '<' + email + '>';
  }
  return '';
}

export const _testOnly_accountEmail = _accountEmail;

export function getGroupDisplayName(group: GroupInfo) {
  return `${group.name || ''} (group)`;
}
