
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













import '../../../test/common-test-setup.js';
import './gr-group-audit-log.js';

const basicFixture = fixtureFromElement('gr-group-audit-log');


suite('gr-group-audit-log tests', () => {
  let element;
  let sandbox;

  setup(() => {
    sandbox = sinon.sandbox.create();
    element = basicFixture.instantiate();
  });

  teardown(() => {
    sandbox.restore();
  });

  suite('members', () => {
    test('test _getNameForGroup', () => {
      let group = {
        member: {
          name: 'test-name',
        },
      };
      assert.equal(element._getNameForGroup(group.member), 'test-name');

      group = {
        member: {
          id: 'test-id',
        },
      };
      assert.equal(element._getNameForGroup(group.member), 'test-id');
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
      const account = {
        user: {
          username: 'test-user',
          _account_id: 12,
        },
      };
      assert.equal(element._getIdForUser(account.user), ' (12)');
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
    test('fires page-error', done => {
      element.groupId = 1;

      const response = {status: 404};
      sandbox.stub(
          element.$.restAPI, 'getGroupAuditLog', (group, errFn) => {
            errFn(response);
          });

      element.addEventListener('page-error', e => {
        assert.deepEqual(e.detail.response, response);
        done();
      });

      element._getAuditLogs();
    });
  });
});

