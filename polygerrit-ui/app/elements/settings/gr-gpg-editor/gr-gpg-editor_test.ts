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
import './gr-gpg-editor';
import {
  mockPromise,
  query,
  queryAll,
  stubRestApi,
} from '../../../test/test-utils';
import {GrGpgEditor} from './gr-gpg-editor';
import {
  GpgKeyFingerprint,
  GpgKeyInfo,
  GpgKeyInfoStatus,
  OpenPgpUserIds,
} from '../../../api/rest-api';
import {GrButton} from '../../shared/gr-button/gr-button';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';

const basicFixture = fixtureFromElement('gr-gpg-editor');

suite('gr-gpg-editor tests', () => {
  let element: GrGpgEditor;
  let keys: Record<string, GpgKeyInfo>;

  setup(async () => {
    const fingerprint1 =
      '0192 723D 42D1 0C5B 32A6  E1E0 9350 9E4B AFC8 A49B' as GpgKeyFingerprint;
    const fingerprint2 =
      '0196 723D 42D1 0C5B 32A6  E1E0 9350 9E4B AFC8 A49B' as GpgKeyFingerprint;
    keys = {
      AFC8A49B: {
        fingerprint: fingerprint1,
        user_ids: ['John Doe john.doe@example.com'] as OpenPgpUserIds[],
        key:
          '-----BEGIN PGP PUBLIC KEY BLOCK-----' +
          '\nVersion: BCPG v1.52\n\t<key 1>',
        status: 'TRUSTED' as GpgKeyInfoStatus,
        problems: [],
      },
      AED9B59C: {
        fingerprint: fingerprint2,
        user_ids: ['Gerrit gerrit@example.com'] as OpenPgpUserIds[],
        key:
          '-----BEGIN PGP PUBLIC KEY BLOCK-----' +
          '\nVersion: BCPG v1.52\n\t<key 2>',
        status: 'TRUSTED' as GpgKeyInfoStatus,
        problems: [],
      },
    };

    stubRestApi('getAccountGPGKeys').returns(Promise.resolve(keys));

    element = basicFixture.instantiate();

    await element.loadData();
    await flush();
  });

  test('renders', () => {
    const rows = queryAll(element, 'tbody tr');

    assert.equal(rows.length, 2);

    let cells = rows[0].querySelectorAll('td');
    assert.equal(cells[0].textContent, 'AFC8A49B');

    cells = rows[1].querySelectorAll('td');
    assert.equal(cells[0].textContent, 'AED9B59C');
  });

  test('remove key', async () => {
    const lastKey = keys[Object.keys(keys)[1]];

    const saveStub = stubRestApi('deleteAccountGPGKey').callsFake(() =>
      Promise.resolve(new Response())
    );

    assert.equal(element._keysToRemove.length, 0);
    assert.isFalse(element.hasUnsavedChanges);

    // Get the delete button for the last row.
    const button = query<GrButton>(
      element,
      'tbody tr:last-of-type td:nth-child(6) gr-button'
    );

    MockInteractions.tap(button!);

    assert.equal(element._keys.length, 1);
    assert.equal(element._keysToRemove.length, 1);
    assert.equal(element._keysToRemove[0], lastKey);
    assert.isTrue(element.hasUnsavedChanges);
    assert.isFalse(saveStub.called);

    await element.save();
    assert.isTrue(saveStub.called);
    assert.equal(saveStub.lastCall.args[0], Object.keys(keys)[1]);
    assert.equal(element._keysToRemove.length, 0);
    assert.isFalse(element.hasUnsavedChanges);
  });

  test('show key', () => {
    const openSpy = sinon.spy(element.$.viewKeyOverlay, 'open');

    // Get the show button for the last row.
    const button = query<GrButton>(
      element,
      'tbody tr:last-of-type td:nth-child(4) gr-button'
    );

    MockInteractions.tap(button!);

    assert.equal(element._keyToView, keys[Object.keys(keys)[1]]);
    assert.isTrue(openSpy.called);
  });

  test('add key', async () => {
    const newKeyString =
      '-----BEGIN PGP PUBLIC KEY BLOCK-----' +
      '\nVersion: BCPG v1.52\n\t<key 3>';
    const newKeyObject = {
      ADE8A59B: {
        fingerprint:
          '0194 723D 42D1 0C5B 32A6  E1E0 9350 9E4B AFC8 A49B' as GpgKeyFingerprint,
        user_ids: ['John john@example.com'] as OpenPgpUserIds[],
        key: newKeyString,
        status: 'TRUSTED' as GpgKeyInfoStatus,
        problems: [],
      },
    };

    const addStub = stubRestApi('addAccountGPGKey').callsFake(() =>
      Promise.resolve(newKeyObject)
    );

    element._newKey = newKeyString;

    assert.isFalse(element.$.addButton.disabled);
    assert.isFalse(element.$.newKey.disabled);

    const promise = mockPromise();
    element._handleAddKey().then(() => {
      assert.isTrue(element.$.addButton.disabled);
      assert.isFalse(element.$.newKey.disabled);
      assert.equal(element._keys.length, 2);
      promise.resolve();
    });

    assert.isTrue(element.$.addButton.disabled);
    assert.isTrue(element.$.newKey.disabled);

    assert.isTrue(addStub.called);
    assert.deepEqual(addStub.lastCall.args[0], {add: [newKeyString]});
    await promise;
  });

  test('add invalid key', async () => {
    const newKeyString = 'not even close to valid';

    const addStub = stubRestApi('addAccountGPGKey').callsFake(() =>
      Promise.reject(new Error('error'))
    );

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
    assert.deepEqual(addStub.lastCall.args[0], {add: [newKeyString]});
    await promise;
  });
});
