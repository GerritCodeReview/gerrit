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
import {createChange} from '../test/test-data-generators';
import {
  AccountId,
  AccountInfo,
  ChangeInfo,
  EmailAddress,
  ServerInfo,
} from '../types/common';
import {hasAttention, getReason} from './attention-set-util';

const KERMIT: AccountInfo = {
  email: 'kermit@gmail.com' as EmailAddress,
  username: 'kermit',
  name: 'Kermit The Frog',
  _account_id: 31415926535 as AccountId,
};
const change: ChangeInfo = {
  ...createChange(),
  attention_set: {
    '31415926535': {
      account: KERMIT,
      reason: 'a good reason',
    },
  },
};

suite('attention-set-util', () => {
  test('hasAttention', () => {
    assert.isTrue(hasAttention(KERMIT, change));
  });

  test('getReason', () => {
    assert.equal(getReason({} as ServerInfo, KERMIT, change), 'a good reason');
  });
});
