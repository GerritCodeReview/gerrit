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
import {createChange, createServerInfo} from '../test/test-data-generators';
import {
  AccountId,
  AccountInfo,
  ChangeInfo,
  EmailAddress,
  ServerInfo,
} from '../types/common';
import {getReason, hasAttention} from './attention-set-util';
import {DefaultDisplayNameConfig} from '../api/rest-api';
import {AccountsVisibility} from '../constants/constants';

const KERMIT: AccountInfo = {
  email: 'kermit@gmail.com' as EmailAddress,
  username: 'kermit',
  name: 'Kermit The Frog',
  _account_id: 31415926535 as AccountId,
};

const OTHER_ACCOUNT: AccountInfo = {
  email: 'other@gmail.com' as EmailAddress,
  username: 'other',
  name: 'Other User',
  _account_id: 31415926536 as AccountId,
};

const change: ChangeInfo = {
  ...createChange(),
  attention_set: {
    '31415926535': {
      account: KERMIT,
      reason: 'a good reason',
    },
    '31415926536': {
      account: OTHER_ACCOUNT,
      reason: 'Added by <GERRIT_ACCOUNT_31415926535>',
      reason_account: KERMIT,
    },
  },
};

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

suite('attention-set-util', () => {
  test('hasAttention', () => {
    assert.isTrue(hasAttention(KERMIT, change));
  });

  test('getReason', () => {
    assert.equal(getReason(config, KERMIT, change), 'a good reason');
    assert.equal(getReason(config, OTHER_ACCOUNT, change), 'Added by kermit');
  });
});
