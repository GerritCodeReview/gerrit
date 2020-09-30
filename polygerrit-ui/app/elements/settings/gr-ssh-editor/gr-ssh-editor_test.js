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
import './gr-ssh-editor.js';

const basicFixture = fixtureFromElement('gr-ssh-editor');

suite('gr-ssh-editor tests', () => {
  let element;
  let keys;

  setup(done => {
    keys = [{
      seq: 1,
      ssh_public_key: 'ssh-rsa <key 1> comment-one@machine-one',
      encoded_key: '<key 1>',
      algorithm: 'ssh-rsa',
      comment: 'comment-one@machine-one',
      valid: true,
    }, {
      seq: 2,
      ssh_public_key: 'ssh-rsa <key 2> comment-two@machine-two',
      encoded_key: '<key 2>',
      algorithm: 'ssh-rsa',
      comment: 'comment-two@machine-two',
      valid: true,
    }];

    stub('gr-rest-api-interface', {
      getAccountSSHKeys() { return Promise.resolve(keys); },
    });

    element = basicFixture.instantiate();

    element.loadData().then(() => { flush(done); });
  });

  test('renders', () => {
    const rows = element.root.querySelectorAll('tbody tr');

    assert.equal(rows.length, 2);

    let cells = rows[0].querySelectorAll('td');
    assert.equal(cells[0].textContent, keys[0].comment);

    cells = rows[1].querySelectorAll('td');
    assert.equal(cells[0].textContent, keys[1].comment);
  });

  test('remove key', done => {
    const lastKey = keys[1];

    const saveStub = sinon.stub(element.$.restAPI, 'deleteAccountSSHKey')
        .callsFake(() => { return Promise.resolve(); });

    assert.equal(element._keysToRemove.length, 0);
    assert.isFalse(element.hasUnsavedChanges);

    // Get the delete button for the last row.
    const button = element.root.querySelector(
        'tbody tr:last-of-type td:nth-child(5) gr-button');

    MockInteractions.tap(button);

    assert.equal(element._keys.length, 1);
    assert.equal(element._keysToRemove.length, 1);
    assert.equal(element._keysToRemove[0], lastKey);
    assert.isTrue(element.hasUnsavedChanges);
    assert.isFalse(saveStub.called);

    element.save().then(() => {
      assert.isTrue(saveStub.called);
      assert.equal(saveStub.lastCall.args[0], lastKey.seq);
      assert.equal(element._keysToRemove.length, 0);
      assert.isFalse(element.hasUnsavedChanges);
      done();
    });
  });

  test('show key', () => {
    const openSpy = sinon.spy(element.$.viewKeyOverlay, 'open');

    // Get the show button for the last row.
    const button = element.root.querySelector(
        'tbody tr:last-of-type td:nth-child(3) gr-button');

    MockInteractions.tap(button);

    assert.equal(element._keyToView, keys[1]);
    assert.isTrue(openSpy.called);
  });

  test('add key', done => {
    const newKeyString = 'ssh-rsa <key 3> comment-three@machine-three';
    const newKeyObject = {
      seq: 3,
      ssh_public_key: newKeyString,
      encoded_key: '<key 3>',
      algorithm: 'ssh-rsa',
      comment: 'comment-three@machine-three',
      valid: true,
    };

    const addStub = sinon.stub(element.$.restAPI, 'addAccountSSHKey').callsFake(
        () => { return Promise.resolve(newKeyObject); });

    element._newKey = newKeyString;

    assert.isFalse(element.$.addButton.disabled);
    assert.isFalse(element.$.newKey.disabled);

    element._handleAddKey().then(() => {
      assert.isTrue(element.$.addButton.disabled);
      assert.isFalse(element.$.newKey.disabled);
      assert.equal(element._keys.length, 3);
      done();
    });

    assert.isTrue(element.$.addButton.disabled);
    assert.isTrue(element.$.newKey.disabled);

    assert.isTrue(addStub.called);
    assert.equal(addStub.lastCall.args[0], newKeyString);
  });

  test('add invalid key', done => {
    const newKeyString = 'not even close to valid';

    const addStub = sinon.stub(element.$.restAPI, 'addAccountSSHKey').callsFake(
        () => { return Promise.reject(new Error('error')); });

    element._newKey = newKeyString;

    assert.isFalse(element.$.addButton.disabled);
    assert.isFalse(element.$.newKey.disabled);

    element._handleAddKey().then(() => {
      assert.isFalse(element.$.addButton.disabled);
      assert.isFalse(element.$.newKey.disabled);
      assert.equal(element._keys.length, 2);
      done();
    });

    assert.isTrue(element.$.addButton.disabled);
    assert.isTrue(element.$.newKey.disabled);

    assert.isTrue(addStub.called);
    assert.equal(addStub.lastCall.args[0], newKeyString);
  });
});

