/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-gpg-editor';
import {
  mockPromise,
  queryAll,
  queryAndAssert,
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

const basicFixture = fixtureFromElement('gr-gpg-editor');

suite('gr-gpg-editor tests', () => {
  let element: GrGpgEditor;
  let keys: Record<string, GpgKeyInfo>;

  setup(async () => {
    const fingerprint1 =
      '0192 723D 42D1 0C5B 32A6 E1E0 9350 9E4B AFC8 A49B' as GpgKeyFingerprint;
    const fingerprint2 =
      '0196 723D 42D1 0C5B 32A6 E1E0 9350 9E4B AFC8 A49B' as GpgKeyFingerprint;
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
    await element.updateComplete;
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `<div class="gr-form-styles">
      <fieldset id="existing">
        <table>
          <thead>
            <tr>
              <th class="idColumn">ID</th>
              <th class="fingerPrintColumn">Fingerprint</th>
              <th class="userIdHeader">User IDs</th>
              <th class="keyHeader">Public Key</th>
              <th></th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td class="idColumn">AFC8A49B</td>
              <td class="fingerPrintColumn">
                0192 723D 42D1 0C5B 32A6 E1E0 9350 9E4B AFC8 A49B
              </td>
              <td class="userIdHeader">John Doe john.doe@example.com</td>
              <td class="keyHeader">
                <gr-button
                  aria-disabled="false"
                  link=""
                  role="button"
                  tabindex="0"
                >
                  Click to View
                </gr-button>
              </td>
              <td>
                <gr-copy-clipboard
                  buttontitle="Copy GPG public key to clipboard"
                  hastooltip=""
                  hideinput=""
                >
                </gr-copy-clipboard>
              </td>
              <td>
                <gr-button aria-disabled="false" role="button" tabindex="0">
                  Delete
                </gr-button>
              </td>
            </tr>
            <tr>
              <td class="idColumn">AED9B59C</td>
              <td class="fingerPrintColumn">
                0196 723D 42D1 0C5B 32A6 E1E0 9350 9E4B AFC8 A49B
              </td>
              <td class="userIdHeader">Gerrit gerrit@example.com</td>
              <td class="keyHeader">
                <gr-button
                  aria-disabled="false"
                  link=""
                  role="button"
                  tabindex="0"
                >
                  Click to View
                </gr-button>
              </td>
              <td>
                <gr-copy-clipboard
                  buttontitle="Copy GPG public key to clipboard"
                  hastooltip=""
                  hideinput=""
                >
                </gr-copy-clipboard>
              </td>
              <td>
                <gr-button aria-disabled="false" role="button" tabindex="0">
                  Delete
                </gr-button>
              </td>
            </tr>
          </tbody>
        </table>
        <gr-overlay
          aria-hidden="true"
          id="viewKeyOverlay"
          style="outline: none; display: none;"
          tabindex="-1"
          with-backdrop=""
        >
          <fieldset>
            <section>
              <span class="title"> Status </span> <span class="value"> </span>
            </section>
            <section>
              <span class="title"> Key </span> <span class="value"> </span>
            </section>
          </fieldset>
          <gr-button
            aria-disabled="false"
            class="closeButton"
            role="button"
            tabindex="0"
          >
            Close
          </gr-button>
        </gr-overlay>
        <gr-button aria-disabled="true" disabled="" role="button" tabindex="-1">
          Save changes
        </gr-button>
      </fieldset>
      <fieldset>
        <section>
          <span class="title"> New GPG key </span>
          <span class="value">
            <iron-autogrow-textarea
              aria-disabled="false"
              autocomplete="on"
              id="newKey"
              placeholder="New GPG Key"
            >
            </iron-autogrow-textarea>
          </span>
        </section>
        <gr-button
          aria-disabled="true"
          disabled=""
          id="addButton"
          role="button"
          tabindex="-1"
        >
          Add new GPG key
        </gr-button>
      </fieldset>
    </div> `);
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

    assert.equal(element.keysToRemove.length, 0);
    assert.isFalse(element.hasUnsavedChanges);

    // Get the delete button for the last row.
    const button = queryAndAssert<GrButton>(
      element,
      'tbody tr:last-of-type td:nth-child(6) gr-button'
    );

    button.click();

    assert.equal(element.keys.length, 1);
    assert.equal(element.keysToRemove.length, 1);
    assert.equal(element.keysToRemove[0], lastKey);
    assert.isTrue(element.hasUnsavedChanges);
    assert.isFalse(saveStub.called);

    await element.save();
    assert.isTrue(saveStub.called);
    assert.equal(saveStub.lastCall.args[0], Object.keys(keys)[1]);
    assert.equal(element.keysToRemove.length, 0);
    assert.isFalse(element.hasUnsavedChanges);
  });

  test('show key', () => {
    const openSpy = sinon.spy(element.viewKeyOverlay!, 'open');

    // Get the show button for the last row.
    const button = queryAndAssert<GrButton>(
      element,
      'tbody tr:last-of-type td:nth-child(4) gr-button'
    );

    button.click();
    assert.equal(element.keyToView, keys[Object.keys(keys)[1]]);
    assert.isTrue(openSpy.called);
  });

  test('add key', async () => {
    const newKeyString =
      '-----BEGIN PGP PUBLIC KEY BLOCK-----' + ' Version: BCPG v1.52 \t<key 3>';
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

    element.newKey = newKeyString;
    await element.updateComplete;

    assert.isFalse(element.addButton!.disabled);
    assert.isFalse(element.newKeyTextarea!.disabled);

    const promise = mockPromise();
    element.handleAddKey().then(() => {
      assert.isTrue(element.addButton!.disabled);
      assert.isFalse(element.newKeyTextarea!.disabled);
      assert.equal(element.keys.length, 2);
      promise.resolve();
    });

    assert.isTrue(element.addButton!.disabled);
    assert.isTrue(element.newKeyTextarea!.disabled);

    assert.isTrue(addStub.called);
    assert.deepEqual(addStub.lastCall.args[0], {add: [newKeyString]});
    await promise;
  });

  test('add invalid key', async () => {
    const newKeyString = 'not even close to valid';

    const addStub = stubRestApi('addAccountGPGKey').callsFake(() =>
      Promise.reject(new Error('error'))
    );

    element.newKey = newKeyString;
    await element.updateComplete;

    assert.isFalse(element.addButton!.disabled);
    assert.isFalse(element.newKeyTextarea!.disabled);

    const promise = mockPromise();
    element.handleAddKey().then(() => {
      assert.isFalse(element.addButton!.disabled);
      assert.isFalse(element.newKeyTextarea!.disabled);
      assert.equal(element.keys.length, 2);
      promise.resolve();
    });

    assert.isTrue(element.addButton!.disabled);
    assert.isTrue(element.newKeyTextarea!.disabled);

    assert.isTrue(addStub.called);
    assert.deepEqual(addStub.lastCall.args[0], {add: [newKeyString]});
    await promise;
  });
});
