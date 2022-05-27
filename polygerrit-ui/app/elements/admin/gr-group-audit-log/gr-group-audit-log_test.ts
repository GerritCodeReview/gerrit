/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
