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

import '../test/common-test-setup-karma';
import {
  getAccountTemplate,
  isServiceUser,
  removeServiceUsers,
  replaceTemplates,
} from './account-util';
import {
  AccountsVisibility,
  AccountTag,
  DefaultDisplayNameConfig,
} from '../constants/constants';
import {AccountId, AccountInfo, ServerInfo} from '../api/rest-api';
import {createServerInfo} from '../test/test-data-generators';

const EMPTY = {};
const ERNIE = {name: 'Ernie'};
const SERVY = {name: 'Servy', tags: [AccountTag.SERVICE_USER]};
const BOTTY = {name: 'Botty', tags: [AccountTag.SERVICE_USER]};

const config: ServerInfo = {
  ...createServerInfo(),
  user: {
    anonymous_coward_name: 'Unidentified User',
  },
  accounts: {
    visibility: AccountsVisibility.ALL,
    default_display_name: DefaultDisplayNameConfig.USERNAME,
  },
};
const accounts: AccountInfo[] = [
  {
    _account_id: 1 as AccountId,
    name: 'Test User #1',
    username: 'test-username-1',
  },
  {
    _account_id: 2 as AccountId,
    name: 'Test User #2',
  },
];

suite('account-util tests 3', () => {
  test('isServiceUser', () => {
    assert.isFalse(isServiceUser());
    assert.isFalse(isServiceUser(EMPTY));
    assert.isFalse(isServiceUser(ERNIE));
    assert.isTrue(isServiceUser(SERVY));
    assert.isTrue(isServiceUser(BOTTY));
  });

  test('removeServiceUsers', () => {
    assert.sameMembers(removeServiceUsers([]), []);
    assert.sameMembers(removeServiceUsers([EMPTY, ERNIE]), [EMPTY, ERNIE]);
    assert.sameMembers(removeServiceUsers([SERVY, BOTTY]), []);
    assert.sameMembers(removeServiceUsers([EMPTY, SERVY, ERNIE, BOTTY]), [
      EMPTY,
      ERNIE,
    ]);
  });

  test('replaceTemplates with display config', () => {
    assert.equal(
      replaceTemplates(
        'Text with action by <GERRIT_ACCOUNT_0000001>',
        accounts,
        config
      ),
      'Text with action by test-username-1'
    );
    assert.equal(
      replaceTemplates(
        'Text with action by <GERRIT_ACCOUNT_0000002>',
        accounts,
        config
      ),
      'Text with action by Test User #2'
    );
    assert.equal(
      replaceTemplates(
        'Text with action by <GERRIT_ACCOUNT_3>',
        accounts,
        config
      ),
      'Text with action by Gerrit Account 3'
    );
    assert.equal(
      replaceTemplates(
        'Text with multiple accounts: <GERRIT_ACCOUNT_0000003>, <GERRIT_ACCOUNT_0000002>, <GERRIT_ACCOUNT_0000001>',
        accounts,
        config
      ),
      'Text with multiple accounts: Gerrit Account 3, Test User #2, test-username-1'
    );
  });

  test('replaceTemplates no display config', () => {
    assert.equal(
      replaceTemplates(
        'Text with action by <GERRIT_ACCOUNT_0000001>',
        accounts
      ),
      'Text with action by Test User #1'
    );
    assert.equal(
      replaceTemplates(
        'Text with action by <GERRIT_ACCOUNT_0000002>',
        accounts
      ),
      'Text with action by Test User #2'
    );

    assert.equal(
      replaceTemplates('Text with action by <GERRIT_ACCOUNT_3>', accounts),
      'Text with action by Gerrit Account 3'
    );

    assert.equal(
      replaceTemplates(
        'Text with multiple accounts: <GERRIT_ACCOUNT_0000003>, <GERRIT_ACCOUNT_0000002>, <GERRIT_ACCOUNT_0000001>',
        accounts
      ),
      'Text with multiple accounts: Gerrit Account 3, Test User #2, Test User #1'
    );
  });

  test('getTemplate', () => {
    assert.equal(getAccountTemplate(accounts[0], config), '<GERRIT_ACCOUNT_1>');
    assert.equal(getAccountTemplate({}, config), 'Unidentified User');
    assert.equal(getAccountTemplate(), 'Anonymous');
  });
});
