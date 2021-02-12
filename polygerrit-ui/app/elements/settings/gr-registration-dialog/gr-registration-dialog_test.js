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
import '../../../test/common-test-setup-karma.js';
import './gr-registration-dialog.js';
import {stubRestApi} from '../../../test/test-utils.js';

const basicFixture = fixtureFromElement('gr-registration-dialog');

suite('gr-registration-dialog tests', () => {
  let element;
  let account;

  let _listeners;

  setup(() => {
    _listeners = {};

    account = {
      name: 'name',
      username: null,
      email: 'email',
      secondary_emails: [
        'email2',
        'email3',
      ],
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
    stubRestApi('setPreferredAccountEmail').callsFake(email => {
      account.email = email;
      return Promise.resolve();
    });
    stubRestApi('getConfig').returns(
        Promise.resolve({auth: {editable_account_fields: ['USER_NAME']}}));

    element = basicFixture.instantiate();

    return element.loadData();
  });

  teardown(() => {
    for (const [eventType, listeners] of Object.entries(_listeners)) {
      element.removeEventListener(eventType, listeners);
    }
  });

  function listen(eventType) {
    return new Promise(resolve => {
      _listeners[eventType] = function() { resolve(); };
      element.addEventListener(eventType, _listeners[eventType]);
    });
  }

  function save(opt_action) {
    const promise = listen('account-detail-update');
    if (opt_action) {
      opt_action();
    } else {
      MockInteractions.tap(element.$.saveButton);
    }
    return promise;
  }

  function close(opt_action) {
    const promise = listen('close');
    if (opt_action) {
      opt_action();
    } else {
      MockInteractions.tap(element.$.closeButton);
    }
    return promise;
  }

  test('fires the close event on close', done => {
    close().then(done);
  });

  test('fires the close event on save', done => {
    close(() => {
      MockInteractions.tap(element.$.saveButton);
    }).then(done);
  });

  test('saves account details', done => {
    flush(() => {
      element.$.name.value = 'new name';
      element.$.username.value = 'new username';
      element.$.email.value = 'email3';

      // Nothing should be committed yet.
      assert.equal(account.name, 'name');
      assert.isNotOk(account.username);
      assert.equal(account.email, 'email');

      // Save and verify new values are committed.
      save()
          .then(() => {
            assert.equal(account.name, 'new name');
            assert.equal(account.username, 'new username');
            assert.equal(account.email, 'email3');
          })
          .then(done);
    });
  });

  test('email select properly populated', done => {
    element._account = {email: 'foo', secondary_emails: ['bar', 'baz']};
    flush(() => {
      assert.equal(element.$.email.value, 'foo');
      done();
    });
  });

  test('save btn disabled', () => {
    const compute = element._computeSaveDisabled;
    assert.isTrue(compute('', '', false));
    assert.isTrue(compute('', 'test', false));
    assert.isTrue(compute('test', '', false));
    assert.isTrue(compute('test', 'test', true));
    assert.isFalse(compute('test', 'test', false));
  });

  test('_computeUsernameMutable', () => {
    assert.isTrue(element._computeUsernameMutable(
        {auth: {editable_account_fields: ['USER_NAME']}}, null));
    assert.isFalse(element._computeUsernameMutable(
        {auth: {editable_account_fields: ['USER_NAME']}}, 'abc'));
    assert.isFalse(element._computeUsernameMutable(
        {auth: {editable_account_fields: []}}, null));
    assert.isFalse(element._computeUsernameMutable(
        {auth: {editable_account_fields: []}}, 'abc'));
  });
});

