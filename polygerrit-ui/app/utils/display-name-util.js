/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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
const ANONYMOUS_NAME = 'Anonymous';

export function getUserName(config, account) {
  if (account && account.name) {
    return account.name;
  } else if (account && account.username) {
    return account.username;
  } else if (account && account.email) {
    return account.email;
  } else if (config && config.user &&
      config.user.anonymous_coward_name !== 'Anonymous Coward') {
    return config.user.anonymous_coward_name;
  }

  return ANONYMOUS_NAME;
}

export function getDisplayName(config, account) {
  if (account && account.display_name) {
    return account.display_name;
  }
  if (!account || !account.name || !config || !config.accounts) {
    return getUserName(config, account);
  }
  if (config.accounts.default_display_name === 'USERNAME'
      && account.username) {
    return account.username;
  }
  if (config.accounts.default_display_name === 'FIRST_NAME') {
    return account.name.trim().split(' ')[0];
  }
  // Treat every other value as FULL_NAME.
  return account.name;
}

export function getAccountDisplayName(config, account) {
  const reviewerName = getUserName(config, account);
  const reviewerEmail = _accountEmail(account.email);
  const reviewerStatus = account.status ? '(' + account.status + ')' : '';
  return [reviewerName, reviewerEmail, reviewerStatus]
      .filter(p => p.length > 0).join(' ');
}

function _accountEmail(email) {
  if (typeof email !== 'undefined') {
    return '<' + email + '>';
  }
  return '';
}

export const _testOnly_accountEmail = _accountEmail;

export function getGroupDisplayName(group) {
  return group.name + ' (group)';
}
