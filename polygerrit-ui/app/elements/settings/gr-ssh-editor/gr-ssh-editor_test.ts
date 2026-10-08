/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-ssh-editor';
import {
  mockPromise,
  query,
  queryAll,
  queryAndAssert,
  stubRestApi,
  waitEventLoop,
} from '../../../test/test-utils';
import {GrSshEditor} from './gr-ssh-editor';
import {SshKeyInfo, Timestamp} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {assert, fixture, html} from '@open-wc/testing';

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

    element = await fixture(html`<gr-ssh-editor></gr-ssh-editor>`);

    await element.loadData();
    await waitEventLoop();
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="gr-form-styles">
          <fieldset id="existing">
            <table>
              <thead>
                <tr>
                  <th class="commentColumn">Comment</th>
                  <th class="statusHeader">Status</th>
                  <th class="expirationHeader">Expiry</th>
                  <th class="keyHeader">Public key</th>
                  <th></th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td class="commentColumn">comment-one@machine-one</td>
                  <td class="statusColumn">Valid</td>
                  <td class="expirationColumn">Never</td>
                  <td>
                    <gr-button
                      aria-disabled="false"
                      data-index="0"
                      link=""
                      role="button"
                      tabindex="0"
                    >
                      Click To View
                    </gr-button>
                  </td>
                  <td>
                    <gr-copy-clipboard hastooltip="" hideinput="">
                    </gr-copy-clipboard>
                  </td>
                  <td>
                    <gr-button
                      aria-disabled="false"
                      data-index="0"
                      link=""
                      role="button"
                      tabindex="0"
                    >
                      Delete
                    </gr-button>
                  </td>
                </tr>
                <tr>
                  <td class="commentColumn">comment-two@machine-two</td>
                  <td class="statusColumn">Valid</td>
                  <td class="expirationColumn">Never</td>
                  <td>
                    <gr-button
                      aria-disabled="false"
                      data-index="1"
                      link=""
                      role="button"
                      tabindex="0"
                    >
                      Click To View
                    </gr-button>
                  </td>
                  <td>
                    <gr-copy-clipboard hastooltip="" hideinput="">
                    </gr-copy-clipboard>
                  </td>
                  <td>
                    <gr-button
                      aria-disabled="false"
                      data-index="1"
                      link=""
                      role="button"
                      tabindex="0"
                    >
                      Delete
                    </gr-button>
                  </td>
                </tr>
              </tbody>
            </table>
            <dialog id="viewKeyModal" tabindex="-1">
              <fieldset>
                <section>
                  <span class="title"> Algorithm </span>
                  <span class="value"> </span>
                </section>
                <section>
                  <span class="title"> Public key </span>
                  <span class="publicKey value"> </span>
                </section>
                <section>
                  <span class="title"> Comment </span>
                  <span class="value"> </span>
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
            </dialog>
            <gr-button
              aria-disabled="true"
              disabled=""
              role="button"
              tabindex="-1"
            >
              Save Changes
            </gr-button>
          </fieldset>
          <fieldset>
            <section>
              <span class="title"> New SSH key </span>
              <span class="value">
                <gr-autogrow-textarea
                  autocomplete="on"
                  id="newKey"
                  placeholder="New SSH Key"
                >
                </gr-autogrow-textarea>
              </span>
            </section>
            <section>
              <span class="title"> Lifetime </span>
              <span class="value">
                <input
                  class="lifetimeInput"
                  id="lifetime"
                  placeholder="Lifetime (e.g. 30d)"
                />
                <div class="lifetimeHint">
                  Max. allowed lifetime: unlimited. Leave empty for no limit.
                </div>
              </span>
            </section>
            <gr-button
              aria-disabled="true"
              disabled=""
              id="addButton"
              role="button"
              tabindex="-1"
            >
              Add New SSH Key
            </gr-button>
          </fieldset>
        </div>
      `
    );
  });

  test('remove key', async () => {
    const lastKey = keys[1];

    const saveStub = stubRestApi('deleteAccountSSHKey').callsFake(() =>
      Promise.resolve()
    );

    assert.equal(element.keysToRemove.length, 0);
    assert.isFalse(element.hasUnsavedChanges);

    // Get the delete button for the last row.
    const button = query<GrButton>(
      element,
      'tbody tr:last-of-type td:nth-child(6) gr-button'
    );

    button!.click();

    assert.equal(element.keys.length, 1);
    assert.equal(element.keysToRemove.length, 1);
    assert.equal(element.keysToRemove[0], lastKey);
    assert.isTrue(element.hasUnsavedChanges);
    assert.isFalse(saveStub.called);

    await element.save();
    assert.isTrue(saveStub.called);
    assert.equal(saveStub.lastCall.args[0], `${lastKey.seq}`);
    assert.equal(element.keysToRemove.length, 0);
    assert.isFalse(element.hasUnsavedChanges);
  });

  test('show key', () => {
    const openSpy = sinon.spy(element.viewKeyModal, 'showModal');

    // Get the show button for the last row.
    const button = query<GrButton>(
      element,
      'tbody tr:last-of-type td:nth-child(4) gr-button'
    );

    button!.click();

    assert.equal(element.keyToView, keys[1]);
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

    element.newKey = newKeyString;

    await element.updateComplete;

    assert.isFalse(element.addButton.disabled);
    assert.isFalse(element.newKeyEditor.disabled);

    const promise = mockPromise();
    element.handleAddKey().then(() => {
      assert.isTrue(element.addButton.disabled);
      assert.isFalse(element.newKeyEditor.disabled);
      assert.equal(element.keys.length, 3);
      promise.resolve();
    });

    assert.isTrue(element.addButton.disabled);
    assert.isTrue(element.newKeyEditor.disabled);

    assert.isTrue(addStub.called);
    assert.equal(addStub.lastCall.args[0], newKeyString);
    await promise;
  });

  test('add invalid key', async () => {
    const newKeyString = 'not even close to valid';

    const addStub = stubRestApi('addAccountSSHKey').rejects(new Error('error'));

    element.newKey = newKeyString;

    await element.updateComplete;

    assert.isFalse(element.addButton.disabled);
    assert.isFalse(element.newKeyEditor.disabled);

    const promise = mockPromise();
    element.handleAddKey().then(() => {
      assert.isFalse(element.addButton.disabled);
      assert.isFalse(element.newKeyEditor.disabled);
      assert.equal(element.keys.length, 2);
      promise.resolve();
    });

    assert.isTrue(element.addButton.disabled);
    assert.isTrue(element.newKeyEditor.disabled);

    assert.isTrue(addStub.called);
    assert.equal(addStub.lastCall.args[0], newKeyString);
    await promise;
  });

  test('add key failure shows the server message', async () => {
    stubRestApi('addAccountSSHKey').rejects(
      new Error('Error 400 (Bad Request): SSH key has been used previously')
    );
    element.newKey = 'ssh-rsa <key 3> comment-three@machine-three';
    await element.updateComplete;
    const alerts: string[] = [];
    element.addEventListener('show-alert', e =>
      alerts.push((e as CustomEvent).detail.message)
    );

    await element.handleAddKey();

    assert.equal(alerts.length, 1);
    assert.include(alerts[0], 'SSH key has been used previously');
  });

  test('expired keys are hidden until expanded', async () => {
    element.keys = [
      {...keys[0], expiration: '2000-01-01 00:00:00.000000000' as Timestamp},
      {...keys[1], expiration: '2999-01-01 00:00:00.000000000' as Timestamp},
    ];
    await element.updateComplete;

    let rows = queryAll<HTMLTableRowElement>(element, 'tbody tr');
    assert.equal(rows.length, 1);
    assert.isFalse(rows[0].classList.contains('expired'));
    assert.equal(
      rows[0].querySelectorAll('td')[1].textContent?.trim(),
      'Valid'
    );
    const toggle = queryAndAssert<GrButton>(element, '#toggleExpiredKeys');
    assert.include(toggle.textContent, 'Show expired keys');
    assert.include(toggle.textContent, '(1)');

    toggle.click();
    await element.updateComplete;

    rows = queryAll<HTMLTableRowElement>(element, 'tbody tr');
    assert.equal(rows.length, 2);
    assert.isTrue(rows[0].classList.contains('expired'));
    assert.equal(
      rows[0].querySelectorAll('td')[1].textContent?.trim(),
      'Expired'
    );
    assert.include(
      queryAndAssert<GrButton>(element, '#toggleExpiredKeys').textContent,
      'Hide expired keys'
    );
  });

  test('delete button of an expanded expired key removes that key', async () => {
    element.keys = [
      {...keys[0], expiration: '2000-01-01 00:00:00.000000000' as Timestamp},
      keys[1],
    ];
    element.showExpiredKeys = true;
    await element.updateComplete;

    const button = query<GrButton>(
      element,
      'tbody tr:first-of-type td:nth-child(6) gr-button'
    );
    button!.click();

    assert.equal(element.keysToRemove.length, 1);
    assert.equal(element.keysToRemove[0].seq, 1);
  });

  test('no toggle without expired keys', async () => {
    assert.isUndefined(query(element, '#toggleExpiredKeys'));
  });

  test('add key with lifetime', async () => {
    const addStub = stubRestApi('addAccountSSHKey').resolves(keys[0]);
    element.newKey = 'ssh-rsa <key 3> comment-three@machine-three';
    element.newLifetime = ' 30d ';
    await element.updateComplete;

    await element.handleAddKey();

    assert.isTrue(addStub.calledOnce);
    assert.equal(addStub.lastCall.args[1], '30d');
    assert.equal(element.newLifetime, '');
  });

  test('add key without lifetime', async () => {
    const addStub = stubRestApi('addAccountSSHKey').resolves(keys[0]);
    element.newKey = 'ssh-rsa <key 3> comment-three@machine-three';
    await element.updateComplete;

    await element.handleAddKey();

    assert.isUndefined(addStub.lastCall.args[1]);
  });

  test('add key with non-positive lifetime is rejected', async () => {
    const addStub = stubRestApi('addAccountSSHKey').resolves(keys[0]);
    element.newKey = 'ssh-rsa <key 3> comment-three@machine-three';
    for (const lifetime of ['0', '-5d', 'abc']) {
      element.newLifetime = lifetime;
      await element.updateComplete;
      await element.handleAddKey();
    }
    assert.isFalse(addStub.called);
    assert.isFalse(element.addButton.disabled);
  });

  test('lifetime hint shows the configured maximum', async () => {
    const hint = () =>
      queryAndAssert(element, '.lifetimeHint').textContent?.trim();
    assert.equal(
      hint(),
      'Max. allowed lifetime: unlimited. Leave empty for no limit.'
    );

    element.maxLifetime = '259200';
    await element.updateComplete;

    assert.equal(
      hint(),
      'Max. allowed lifetime: 180d. Leave empty to use the maximum allowed lifetime.'
    );
  });
});
