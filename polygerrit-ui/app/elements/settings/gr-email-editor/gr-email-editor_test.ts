/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-email-editor';
import {GrEmailEditor} from './gr-email-editor';
import {spyRestApi, stubRestApi} from '../../../test/test-utils';
import {assert, fixture, html} from '@open-wc/testing';
import {EmailAddress} from '../../../api/rest-api';

suite('gr-email-editor tests', () => {
  let element: GrEmailEditor;
  let accountEmailStub: sinon.SinonStub;

  setup(async () => {
    const emails = [
      {email: 'email@one.com' as EmailAddress},
      {email: 'email@two.com' as EmailAddress, preferred: true},
      {email: 'email@three.com' as EmailAddress},
    ];

    accountEmailStub = stubRestApi('getAccountEmails').returns(
      Promise.resolve(emails)
    );

    element = await fixture<GrEmailEditor>(
      html`<gr-email-editor></gr-email-editor>`
    );

    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `<div class="gr-form-styles">
        <table id="emailTable">
          <thead>
            <tr>
              <th class="emailColumn">Email</th>
              <th class="preferredHeader">Preferred</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td class="emailColumn">email@one.com</td>
              <td class="preferredControl">
                <input
                  class="preferredRadio"
                  name="preferred"
                  type="radio"
                  value="email@one.com"
                />
              </td>
              <td>
                <gr-button
                  aria-disabled="false"
                  class="remove-button"
                  role="button"
                  tabindex="0"
                >
                  Delete
                </gr-button>
              </td>
            </tr>
            <tr>
              <td class="emailColumn">email@two.com</td>
              <td class="preferredControl">
                <input
                  class="preferredRadio"
                  name="preferred"
                  type="radio"
                  value="email@two.com"
                />
              </td>
              <td>
                <gr-button
                  aria-disabled="true"
                  class="remove-button"
                  disabled=""
                  role="button"
                  tabindex="-1"
                >
                  Delete
                </gr-button>
              </td>
            </tr>
            <tr>
              <td class="emailColumn">email@three.com</td>
              <td class="preferredControl">
                <input
                  class="preferredRadio"
                  name="preferred"
                  type="radio"
                  value="email@three.com"
                />
              </td>
              <td>
                <gr-button
                  aria-disabled="false"
                  class="remove-button"
                  role="button"
                  tabindex="0"
                >
                  Delete
                </gr-button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>`
    );
  });

  test('renders', () => {
    const hasUnsavedChangesSpy = sinon.spy();
    element.addEventListener(
      'has-unsaved-changes-changed',
      hasUnsavedChangesSpy
    );

    const rows = element
      .shadowRoot!.querySelector('table')!
      .querySelectorAll('tbody tr');

    assert.equal(rows.length, 3);

    assert.isFalse(
      (rows[0].querySelector('input[type=radio]') as HTMLInputElement).checked
    );
    assert.isNotOk(rows[0].querySelector('gr-button')!.disabled);

    assert.isTrue(
      (rows[1].querySelector('input[type=radio]') as HTMLInputElement).checked
    );
    assert.isOk(rows[1].querySelector('gr-button')!.disabled);

    assert.isFalse(
      (rows[2].querySelector('input[type=radio]') as HTMLInputElement).checked
    );
    assert.isNotOk(rows[2].querySelector('gr-button')!.disabled);

    assert.isFalse(hasUnsavedChangesSpy.called);
  });

  test('edit preferred', () => {
    const hasUnsavedChangesSpy = sinon.spy();
    element.addEventListener(
      'has-unsaved-changes-changed',
      hasUnsavedChangesSpy
    );

    const radios = element
      .shadowRoot!.querySelector('table')!
      .querySelectorAll<HTMLInputElement>('input[type=radio]');

    assert.isFalse(hasUnsavedChangesSpy.called);
    assert.isNotOk(element.newPreferred);
    assert.equal(element.emailsToRemove.length, 0);
    assert.equal(element.emails.length, 3);
    assert.isNotOk(radios[0].checked);
    assert.isOk(radios[1].checked);
    assert.isUndefined(element.emails[0].preferred);

    radios[0].click();

    assert.isTrue(hasUnsavedChangesSpy.called);
    assert.isOk(element.newPreferred);
    assert.equal(element.emailsToRemove.length, 0);
    assert.equal(element.emails.length, 3);
    assert.isOk(radios[0].checked);
    assert.isNotOk(radios[1].checked);
    assert.isTrue(element.emails[0].preferred);
  });

  test('delete email', () => {
    const hasUnsavedChangesSpy = sinon.spy();
    element.addEventListener(
      'has-unsaved-changes-changed',
      hasUnsavedChangesSpy
    );

    const buttons = element
      .shadowRoot!.querySelector('table')!
      .querySelectorAll('gr-button');

    assert.isFalse(hasUnsavedChangesSpy.called);
    assert.isNotOk(element.newPreferred);
    assert.equal(element.emailsToRemove.length, 0);
    assert.equal(element.emails.length, 3);

    buttons[2].click();

    assert.isTrue(hasUnsavedChangesSpy.called);
    assert.isNotOk(element.newPreferred);
    assert.equal(element.emailsToRemove.length, 1);
    assert.equal(element.emails.length, 2);

    assert.equal(element.emailsToRemove[0].email, 'email@three.com');
  });

  test('save changes', async () => {
    const hasUnsavedChangesSpy = sinon.spy();
    element.addEventListener(
      'has-unsaved-changes-changed',
      hasUnsavedChangesSpy
    );

    const deleteEmailSpy = spyRestApi('deleteAccountEmail');
    const setPreferredSpy = spyRestApi('setPreferredAccountEmail');

    const rows = element
      .shadowRoot!.querySelector('table')!
      .querySelectorAll('tbody tr');

    assert.isFalse(hasUnsavedChangesSpy.called);
    assert.isNotOk(element.newPreferred);
    assert.equal(element.emailsToRemove.length, 0);
    assert.equal(element.emails.length, 3);

    // Delete the first email and set the last as preferred.
    rows[0].querySelector('gr-button')!.click();
    rows[2].querySelector<HTMLInputElement>('input[type=radio]')!.click();

    assert.isTrue(hasUnsavedChangesSpy.called);
    assert.isTrue(hasUnsavedChangesSpy.lastCall.args[0].detail.value);
    assert.equal(element.newPreferred, 'email@three.com');
    assert.equal(element.emailsToRemove.length, 1);
    assert.equal(element.emailsToRemove[0].email, 'email@one.com');
    assert.equal(element.emails.length, 2);

    accountEmailStub.restore();

    accountEmailStub = stubRestApi('getAccountEmails').returns(
      Promise.resolve(element.emails)
    );

    await element.save();
    assert.equal(deleteEmailSpy.callCount, 1);
    assert.equal(deleteEmailSpy.getCall(0).args[0], 'email@one.com');
    assert.isTrue(setPreferredSpy.called);
    assert.equal(setPreferredSpy.getCall(0).args[0], 'email@three.com');
    assert.isFalse(hasUnsavedChangesSpy.lastCall.args[0].detail.value);
  });
});
