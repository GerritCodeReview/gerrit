/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import './gr-group-audit-log';
import {
  addListenerForTest,
  mockPromise,
  stubRestApi,
} from '../../../test/test-utils';
import {GrGroupAuditLog} from './gr-group-audit-log';
import {
  EncodedGroupId,
  GroupAuditEventType,
  GroupInfo,
  GroupName,
} from '../../../types/common';
import {
  createAccountWithId,
  createGroupAuditEventInfo,
  createGroupInfo,
} from '../../../test/test-data-generators';
import {PageErrorEvent} from '../../../types/events';

const basicFixture = fixtureFromElement('gr-group-audit-log');

suite('gr-group-audit-log tests', () => {
  let element: GrGroupAuditLog;

  setup(async () => {
    element = basicFixture.instantiate();
    await element.updateComplete;
  });

  suite('members', () => {
    test('test getNameForGroup', () => {
      let member: GroupInfo = {
        ...createGroupInfo(),
        name: 'test-name' as GroupName,
      };
      assert.equal(element.getNameForGroup(member), 'test-name');

      member = createGroupInfo('test-id');
      assert.equal(element.getNameForGroup(member), 'test-id');
    });

    test('test isGroupEvent', () => {
      assert.isTrue(
        element.isGroupEvent(
          createGroupAuditEventInfo(GroupAuditEventType.ADD_GROUP)
        )
      );
      assert.isTrue(
        element.isGroupEvent(
          createGroupAuditEventInfo(GroupAuditEventType.REMOVE_GROUP)
        )
      );

      assert.isFalse(
        element.isGroupEvent(
          createGroupAuditEventInfo(GroupAuditEventType.ADD_USER)
        )
      );
      assert.isFalse(
        element.isGroupEvent(
          createGroupAuditEventInfo(GroupAuditEventType.REMOVE_USER)
        )
      );
    });
  });

  suite('users', () => {
    test('test getIdForUser', () => {
      const user = {
        ...createAccountWithId(12),
        username: 'test-user',
      };
      assert.equal(element.getIdForUser(user), ' (12)');
    });

    test('test _account_id not present', () => {
      const account = {
        user: {
          username: 'test-user',
        },
      };
      assert.equal(element.getIdForUser(account.user), '');
    });
  });

  suite('404', () => {
    test('fires page-error', async () => {
      element.groupId = '1' as EncodedGroupId;
      await element.updateComplete;

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

      element.getAuditLogs();
      await pageErrorCalled;
    });
  });
});
