/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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

import '../../../test/common-test-setup-karma.js';
import './gr-group-audit-log.js';
import {
  stubRestApi,
  addListenerForTest,
  mockPromise,
} from '../../../test/test-utils.js';
import {GrGroupAuditLog} from './gr-group-audit-log.js';
import {EncodedGroupId, GroupInfo, GroupName} from '../../../types/common.js';
import {
  createAccountWithId,
  createGroupInfo,
} from '../../../test/test-data-generators.js';
import {PageErrorEvent} from '../../../types/events.js';

const basicFixture = fixtureFromElement('gr-group-audit-log');

suite('gr-group-audit-log tests', () => {
  let element: GrGroupAuditLog;

  setup(() => {
    element = basicFixture.instantiate();
  });

  suite('members', () => {
    test('test _getNameForGroup', () => {
      let member: GroupInfo = {
        ...createGroupInfo(),
        name: 'test-name' as GroupName,
      };
      assert.equal(element._getNameForGroup(member), 'test-name');

      member = createGroupInfo('test-id');
      assert.equal(element._getNameForGroup(member), 'test-id');
    });

    test('test _isGroupEvent', () => {
      assert.isTrue(element._isGroupEvent('ADD_GROUP'));
      assert.isTrue(element._isGroupEvent('REMOVE_GROUP'));

      assert.isFalse(element._isGroupEvent('ADD_USER'));
      assert.isFalse(element._isGroupEvent('REMOVE_USER'));
    });
  });

  suite('users', () => {
    test('test _getIdForUser', () => {
      const user = {
        ...createAccountWithId(12),
        username: 'test-user',
      };
      assert.equal(element._getIdForUser(user), ' (12)');
    });

    test('test _account_id not present', () => {
      const account = {
        user: {
          username: 'test-user',
        },
      };
      assert.equal(element._getIdForUser(account.user), '');
    });
  });

  suite('404', () => {
    test('fires page-error', async () => {
      element.groupId = '1' as EncodedGroupId;
      await flush();

      const response = {...new Response(), status: 404};
      stubRestApi('getGroupAuditLog').callsFake((_group, errFn) => {
        if (errFn) errFn(response);
        return Promise.resolve(undefined);
      });

      const pageErrorCalled = mockPromise();
      addListenerForTest(document, 'page-error', e => {
        assert.deepEqual((e as PageErrorEvent).detail.response, response);
        pageErrorCalled.resolve();
      });

      element._getAuditLogs();
      await pageErrorCalled;
    });
  });
});
