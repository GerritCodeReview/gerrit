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
import {getAccountTemplate, isServiceUser, removeServiceUsers, replaceTemplates} from './account-util';
import {AccountTag} from '../constants/constants';
import {AccountInfo, ServerInfo} from '../api/rest-api';

const EMPTY = {};
const ERNIE = {name: 'Ernie'};
const SERVY = {name: 'Servy', tags: [AccountTag.SERVICE_USER]};
const BOTTY = {name: 'Botty', tags: [AccountTag.SERVICE_USER]};

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

  test('replaceTemplates', () => {
    const config = {
      user: {
        anonymous_coward_name: 'Anonymous Coward',
      },
      accounts: {
        default_display_name: 'USERNAME',
      },
    } as ServerInfo;

    const accounts = [{
      _account_id: 1,
      name: 'Test User #1',
      username: 'test-username-1',
    } as AccountInfo, {_account_id: 1,
      name: 'Test User #2',
      display_name: 'Display Name'} as AccountInfo] ;
    assert.equal(replaceTemplates("Text with action by <GERRIT_ACCOUNT_0000001>", accounts, config),
        "Text with action by test-username-1");
    assert.equal(replaceTemplates("Text with action by <GERRIT_ACCOUNT_0000001>", accounts),
        "Text with action by Test Username #1");
    // found acount according to config
    assert.equal(replaceTemplates("Text with action by <GERRIT_ACCOUNT_0000001>", accounts, config),
        "Text with action by Test User #2");
    assert.equal(replaceTemplates("Text with action by <GERRIT_ACCOUNT_0000001>", accounts),
        "Text with action by Test User #2");

    assert.equal(replaceTemplates("Text with action by <GERRIT_ACCOUNT_0000003>", accounts, config),
        "Text with action by Gerrit Account");
    // Not found account displays as row number
    assert.equal(replaceTemplates("Text with action by <GERRIT_ACCOUNT_0000003>", accounts),
        "Text with action by Gerrit Account");

  });


  test('getTemplate', () => {
    const config = {
      user: {
        anonymous_coward_name: 'Anonymous Coward',
      },
      accounts: {
        default_display_name: 'USERNAME',
      },
    } as ServerInfo;

    const account = {
      _account_id: 1,
      name: 'Test User #1',
      username: 'test-username-1',
    } as AccountInfo;
    assert.equal(getAccountTemplate(account, config),
        "<GERRIT_ACCOUNT_0000001>");
    assert.equal(getAccountTemplate({}, config),
        "Anonymous Coward");
  });
});
