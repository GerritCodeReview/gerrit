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
                  <th class="expirationColumn">Expiration Date</th>
                  <th class="keyHeader">Public key</th>
                  <th></th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td class="commentColumn">comment-one@machine-one</td>
                  <td>Valid</td>
                  <td class="expirationColumn">
                    <gr-date-formatter
                      dateformat="STD"
                      showdateandtime=""
                      withtooltip=""
                    >
                    </gr-date-formatter>
                  </td>
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
                  <td>Valid</td>
                  <td class="expirationColumn">
                    <gr-date-formatter
                      dateformat="STD"
                      showdateandtime=""
                      withtooltip=""
                    >
                    </gr-date-formatter>
                  </td>
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
                <md-outlined-text-field
                  autocomplete=""
                  class="lifeTimeInput showBlueFocusBorder"
                  id="lifetime"
                  inputmode=""
                  placeholder="Lifetime (e.g. 30d)"
                  supporting-text="Max. allowed lifetime: unlimited. Leave empty to use maximum allowed lifetime."
                  type="text"
                >
                </md-outlined-text-field>
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

  test('expired keys are hidden by default', async () => {
    const past = '2000-01-01 00:00:00.000000000' as Timestamp;
    element.keys = [{...keys[0], expiration: past}, keys[1]];
    await element.updateComplete;

    assert.equal(element.shadowRoot!.querySelectorAll('tbody tr').length, 1);

    const button = query<GrButton>(element, '#showExpired')!;
    assert.equal(button.textContent!.trim(), 'Show expired keys');
    button.click();
    await element.updateComplete;

    const rows = element.shadowRoot!.querySelectorAll('tbody tr');
    assert.equal(rows.length, 2);
    assert.equal(rows[0].children[1].textContent, 'Expired');
    assert.equal(rows[1].children[1].textContent, 'Valid');
    assert.equal(button.textContent!.trim(), 'Hide expired keys');
  });

  test('add key with lifetime', async () => {
    const newKeyString = 'ssh-rsa <key 3> comment-three@machine-three';
    const addStub = stubRestApi('addAccountSSHKey').resolves({
      seq: 3,
      ssh_public_key: newKeyString,
      encoded_key: '<key 3>',
      algorithm: 'ssh-rsa',
      comment: 'comment-three@machine-three',
      valid: true,
    });

    element.newKey = newKeyString;
    element.newLifetime = '30d';
    await element.updateComplete;

    await element.handleAddKey();

    assert.deepEqual(addStub.lastCall.args, [newKeyString, '30d']);
    assert.equal(element.newLifetime, '');
  });

  test('add invalid key', async () => {
    const newKeyString = 'not even close to valid';

    const addStub = stubRestApi('addAccountSSHKey').rejects(new Error('error'));
    const alertStub = sinon.stub();
    element.addEventListener('show-alert', alertStub);

    element.newKey = newKeyString;

    await element.updateComplete;

    assert.isFalse(element.addButton.disabled);
    assert.isFalse(element.newKeyEditor.disabled);

    const promise = mockPromise();
    element.handleAddKey().then(() => {
      assert.isFalse(element.addButton.disabled);
      assert.isFalse(element.newKeyEditor.disabled);
      assert.equal(element.keys.length, 2);
      assert.isTrue(alertStub.calledOnce);
      assert.equal(
        alertStub.lastCall.args[0].detail.message,
        'Failed to add SSH key: error'
      );
      promise.resolve();
    });

    assert.isTrue(element.addButton.disabled);
    assert.isTrue(element.newKeyEditor.disabled);

    assert.isTrue(addStub.called);
    assert.equal(addStub.lastCall.args[0], newKeyString);
    await promise;
  });
});
