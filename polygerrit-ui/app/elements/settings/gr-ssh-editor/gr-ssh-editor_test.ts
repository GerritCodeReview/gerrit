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
import './gr-ssh-editor';
import {
  mockPromise,
  query,
  queryAll,
  stubRestApi,
} from '../../../test/test-utils';
import {GrSshEditor} from './gr-ssh-editor';
import {SshKeyInfo} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';

const basicFixture = fixtureFromElement('gr-ssh-editor');

suite('gr-ssh-editor tests', () => {
  let element: GrSshEditor;
  let keys: SshKeyInfo[];

  setup(async () => {
    keys = [
      {
        seq: 1,
        ssh_public_key: 'ssh-rsa <key 1> comment-one@machine-one',
        encoded_key: '<key 1>',
        algorithm: 'ssh-rsa',
        comment: 'comment-one@machine-one',
        valid: true,
      },
      {
        seq: 2,
        ssh_public_key: 'ssh-rsa <key 2> comment-two@machine-two',
        encoded_key: '<key 2>',
        algorithm: 'ssh-rsa',
        comment: 'comment-two@machine-two',
        valid: true,
      },
    ];

    stubRestApi('getAccountSSHKeys').returns(Promise.resolve(keys));

    element = basicFixture.instantiate();

    await element.loadData();
    await flush();
  });

  test('renders', () => {
    const rows = queryAll<HTMLTableElement>(element, 'tbody tr');

    assert.equal(rows.length, 2);

    let cells = queryAll<HTMLTableElement>(rows[0], 'td');
    assert.equal(cells[0].textContent, keys[0].comment);

    cells = queryAll<HTMLTableElement>(rows[1], 'td');
    assert.equal(cells[0].textContent, keys[1].comment);
  });

  test('remove key', async () => {
    const lastKey = keys[1];

    const saveStub = stubRestApi('deleteAccountSSHKey').callsFake(() =>
      Promise.resolve()
    );

    assert.equal(element._keysToRemove.length, 0);
    assert.isFalse(element.hasUnsavedChanges);

    // Get the delete button for the last row.
    const button = query<GrButton>(
      element,
      'tbody tr:last-of-type td:nth-child(5) gr-button'
    );

    MockInteractions.tap(button!);

    assert.equal(element._keys.length, 1);
    assert.equal(element._keysToRemove.length, 1);
    assert.equal(element._keysToRemove[0], lastKey);
    assert.isTrue(element.hasUnsavedChanges);
    assert.isFalse(saveStub.called);

    await element.save();
    assert.isTrue(saveStub.called);
    assert.equal(saveStub.lastCall.args[0], `${lastKey.seq}`);
    assert.equal(element._keysToRemove.length, 0);
    assert.isFalse(element.hasUnsavedChanges);
  });

  test('show key', () => {
    const openSpy = sinon.spy(element.$.viewKeyOverlay, 'open');

    // Get the show button for the last row.
    const button = query<GrButton>(
      element,
      'tbody tr:last-of-type td:nth-child(3) gr-button'
    );

    MockInteractions.tap(button!);

    assert.equal(element._keyToView, keys[1]);
    assert.isTrue(openSpy.called);
  });

  test('add key', async () => {
    const newKeyString = 'ssh-rsa <key 3> comment-three@machine-three';
    const newKeyObject = {
      seq: 3,
      ssh_public_key: newKeyString,
      encoded_key: '<key 3>',
      algorithm: 'ssh-rsa',
      comment: 'comment-three@machine-three',
      valid: true,
    };

    const addStub = stubRestApi('addAccountSSHKey').resolves(newKeyObject);

    element._newKey = newKeyString;

    assert.isFalse(element.$.addButton.disabled);
    assert.isFalse(element.$.newKey.disabled);

    const promise = mockPromise();
    element._handleAddKey().then(() => {
      assert.isTrue(element.$.addButton.disabled);
      assert.isFalse(element.$.newKey.disabled);
      assert.equal(element._keys.length, 3);
      promise.resolve();
    });

    assert.isTrue(element.$.addButton.disabled);
    assert.isTrue(element.$.newKey.disabled);

    assert.isTrue(addStub.called);
    assert.equal(addStub.lastCall.args[0], newKeyString);
    await promise;
  });

  test('add invalid key', async () => {
    const newKeyString = 'not even close to valid';

    const addStub = stubRestApi('addAccountSSHKey').rejects(new Error('error'));

    element._newKey = newKeyString;

    assert.isFalse(element.$.addButton.disabled);
    assert.isFalse(element.$.newKey.disabled);

    const promise = mockPromise();
    element._handleAddKey().then(() => {
      assert.isFalse(element.$.addButton.disabled);
      assert.isFalse(element.$.newKey.disabled);
      assert.equal(element._keys.length, 2);
      promise.resolve();
    });

    assert.isTrue(element.$.addButton.disabled);
    assert.isTrue(element.$.newKey.disabled);

    assert.isTrue(addStub.called);
    assert.equal(addStub.lastCall.args[0], newKeyString);
    await promise;
  });
});
