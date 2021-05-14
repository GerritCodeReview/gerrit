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
import {GrRegistrationDialog} from './gr-registration-dialog';
import {queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {AccountDetailInfo, Timestamp} from '../../../types/common';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {AuthType, EditableAccountField} from '../../../constants/constants';
import {createServerInfo} from '../../../test/test-data-generators';

const basicFixture = fixtureFromElement('gr-registration-dialog');

suite('gr-registration-dialog tests', () => {
  let element: GrRegistrationDialog;
  let account: AccountDetailInfo;

  let _listeners: {[key: string]: EventListenerOrEventListenerObject};

  setup(() => {
    _listeners = {};

    account = {
      name: 'name',
      registered_on: '2018-02-08 18:49:18.000000000' as Timestamp,
    };

    stubRestApi('getAccount').returns(Promise.resolve(account));
    stubRestApi('setAccountName').callsFake(name => {
      account.name = name;
      return Promise.resolve();
    });
    stubRestApi('setAccountUsername').callsFake(username => {
      account.username = username;
      return Promise.resolve();
    });
    stubRestApi('getConfig').returns(
      Promise.resolve({
        ...createServerInfo(),
        auth: {
          auth_type: AuthType.HTTP,
          editable_account_fields: [EditableAccountField.USER_NAME],
        },
      })
    );

    element = basicFixture.instantiate();

    return element.loadData();
  });

  teardown(() => {
    for (const [eventType, listeners] of Object.entries(_listeners)) {
      element.removeEventListener(eventType, listeners);
    }
  });

  function listen(eventType: string): Promise<void> {
    return new Promise(resolve => {
      _listeners[eventType] = function () {
        resolve();
      };
      element.addEventListener(eventType, _listeners[eventType]);
    });
  }

  function save() {
    const promise = listen('account-detail-update');
    MockInteractions.tap(queryAndAssert(element, '#saveButton'));
    return promise;
  }

  function close(opt_action?: Function) {
    const promise = listen('close');
    if (opt_action) {
      opt_action();
    } else {
      MockInteractions.tap(queryAndAssert(element, '#closeButton'));
    }
    return promise;
  }

  test('fires the close event on close', done => {
    close().then(done);
  });

  test('fires the close event on save', done => {
    close(() =>
      MockInteractions.tap(queryAndAssert(element, '#saveButton'))
    ).then(done);
  });

  test('saves account details', done => {
    flush(() => {
      element.$.name.value = 'new name';

      element.set('_account.username', '');
      element._hasUsernameChange = false;
      assert.isTrue(element._usernameMutable);

      element.set('_username', 'new username');

      // Nothing should be committed yet.
      assert.equal(account.name, 'name');
      assert.isNotOk(account.username);

      // Save and verify new values are committed.
      save()
        .then(() => {
          assert.equal(account.name, 'new name');
          assert.equal(account.username, 'new username');
        })
        .then(done);
    });
  });

  test('save btn disabled', () => {
    const compute = element._computeSaveDisabled;
    assert.isTrue(compute('', '', false));
    assert.isFalse(compute('', 'test', false));
    assert.isFalse(compute('test', '', false));
    assert.isTrue(compute('test', 'test', true));
    assert.isFalse(compute('test', 'test', false));
  });

  test('_computeUsernameMutable', () => {
    assert.isTrue(element._computeUsernameMutable(undefined));
    assert.isFalse(element._computeUsernameMutable('abc'));
  });

  test('_computeUsernameEditable', () => {
    assert.isTrue(
      element._computeUsernameEditable({
        ...createServerInfo(),
        auth: {
          auth_type: AuthType.HTTP,
          editable_account_fields: [EditableAccountField.USER_NAME],
        },
      })
    );
    assert.isFalse(
      element._computeUsernameEditable({
        ...createServerInfo(),
        auth: {
          auth_type: AuthType.HTTP,
          editable_account_fields: [],
        },
      })
    );
  });
});
