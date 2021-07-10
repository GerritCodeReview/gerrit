/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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

import '../../../test/common-test-setup-karma';
import './gr-account-label';
import {
  queryAndAssert,
  spyRestApi,
  stubRestApi,
} from '../../../test/test-utils';
import {GrAccountLabel} from './gr-account-label';
import {AccountDetailInfo, ServerInfo} from '../../../types/common';
import {
  createAccountDetailWithId,
  createChange,
  createServerInfo,
} from '../../../test/test-data-generators';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';

const basicFixture = fixtureFromElement('gr-account-label');

suite('gr-account-label tests', () => {
  let element: GrAccountLabel;
  const kermit: AccountDetailInfo = {
    ...createAccountDetailWithId(31),
    name: 'kermit',
  };

  setup(() => {
    stubRestApi('getAccount').callsFake(() => Promise.resolve(kermit));
    stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    element = basicFixture.instantiate();
    element._config = {
      ...createServerInfo(),
      user: {
        anonymous_coward_name: 'Anonymous Coward',
      },
    };
  });

  suite('_computeName', () => {
    test('not showing anonymous', () => {
      const account = {name: 'Wyatt'};
      assert.deepEqual(element._computeName(account, false), 'Wyatt');
    });

    test('showing anonymous but no config', () => {
      const account = {};
      assert.deepEqual(element._computeName(account, false), 'Anonymous');
    });

    test('test for Anonymous Coward user and replace with Anonymous', () => {
      const config: ServerInfo = {
        ...createServerInfo(),
        user: {
          anonymous_coward_name: 'Anonymous Coward',
        },
      };
      const account = {};
      assert.deepEqual(
        element._computeName(account, false, config),
        'Anonymous'
      );
    });

    test('test for anonymous_coward_name', () => {
      const config = {
        ...createServerInfo(),
        user: {
          anonymous_coward_name: 'TestAnon',
        },
      };
      const account = {};
      assert.deepEqual(
        element._computeName(account, false, config),
        'TestAnon'
      );
    });
  });

  suite('attention set', () => {
    setup(async () => {
      element.highlightAttention = true;
      element._config = {
        ...createServerInfo(),
        user: {anonymous_coward_name: 'Anonymous Coward'},
      };
      element._selfAccount = kermit;
      element.account = {
        ...createAccountDetailWithId(42),
        name: 'ernie',
      };
      element.change = {
        ...createChange(),
        attention_set: {
          42: {
            account: createAccountDetailWithId(42),
          },
        },
        owner: kermit,
        reviewers: {},
      };
      await flush();
    });

    test('show attention button', () => {
      const button = queryAndAssert(element, '#attentionButton');
      assert.ok(button);
      assert.isNull(button.getAttribute('disabled'));
    });

    test('tap attention button', async () => {
      const apiSpy = spyRestApi('removeFromAttentionSet');
      const button = queryAndAssert(element, '#attentionButton');
      assert.ok(button);
      assert.isNull(button.getAttribute('disabled'));
      MockInteractions.tap(button);
      assert.isTrue(apiSpy.calledOnce);
      assert.equal(apiSpy.lastCall.args[1], 42);
    });
  });
});
