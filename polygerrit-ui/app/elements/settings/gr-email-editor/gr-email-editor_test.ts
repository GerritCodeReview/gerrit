/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-email-editor';
import {GrEmailEditor} from './gr-email-editor';
import {spyRestApi, stubRestApi} from '../../../test/test-utils';
import {fixture, html} from '@open-wc/testing-helpers';

suite('gr-email-editor tests', () => {
  let element: GrEmailEditor;

  setup(async () => {
    const emails = [
      {email: 'email@one.com'},
      {email: 'email@two.com', preferred: true},
      {email: 'email@three.com'},
    ];

    stubRestApi('getAccountEmails').returns(Promise.resolve(emails));

    element = await fixture<GrEmailEditor>(
      html`<gr-email-editor></gr-email-editor>`
    );

    await element.loadData();
    await element.updateComplete;
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `<div class="gr-form-styles">
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
              <iron-input class="preferredRadio">
                <input
                  class="preferredRadio"
                  name="preferred"
                  type="radio"
                  value="email@one.com"
                />
              </iron-input>
            </td>
            <td>
              <gr-button
                aria-disabled="false"
                class="remove-button"
                data-index="0"
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
              <iron-input class="preferredRadio">
                <input
                  checked=""
                  class="preferredRadio"
                  name="preferred"
                  type="radio"
                  value="email@two.com"
                />
              </iron-input>
            </td>
            <td>
              <gr-button
                aria-disabled="true"
                class="remove-button"
                data-index="1"
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
              <iron-input class="preferredRadio">
                <input
                  class="preferredRadio"
                  name="preferred"
                  type="radio"
                  value="email@three.com"
                />
              </iron-input>
            </td>
            <td>
              <gr-button
                aria-disabled="false"
                class="remove-button"
                data-index="2"
                role="button"
                tabindex="0"
              >
                Delete
              </gr-button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>`);
  });

  test('renders', () => {
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

    assert.isFalse(element.hasUnsavedChanges);
  });

  test('edit preferred', () => {
    const radios = element
      .shadowRoot!.querySelector('table')!
      .querySelectorAll<HTMLInputElement>('input[type=radio]');

    assert.isFalse(element.hasUnsavedChanges);
    assert.isNotOk(element.newPreferred);
    assert.equal(element.emailsToRemove.length, 0);
    assert.equal(element.emails.length, 3);
    assert.isNotOk(radios[0].checked);
    assert.isOk(radios[1].checked);
    assert.isUndefined(element.emails[0].preferred);

    radios[0].click();

    assert.isTrue(element.hasUnsavedChanges);
    assert.isOk(element.newPreferred);
    assert.equal(element.emailsToRemove.length, 0);
    assert.equal(element.emails.length, 3);
    assert.isOk(radios[0].checked);
    assert.isNotOk(radios[1].checked);
    assert.isTrue(element.emails[0].preferred);
  });

  test('delete email', () => {
    const buttons = element
      .shadowRoot!.querySelector('table')!
      .querySelectorAll('gr-button');

    assert.isFalse(element.hasUnsavedChanges);
    assert.isNotOk(element.newPreferred);
    assert.equal(element.emailsToRemove.length, 0);
    assert.equal(element.emails.length, 3);

    buttons[2].click();

    assert.isTrue(element.hasUnsavedChanges);
    assert.isNotOk(element.newPreferred);
    assert.equal(element.emailsToRemove.length, 1);
    assert.equal(element.emails.length, 2);

    assert.equal(element.emailsToRemove[0].email, 'email@three.com');
  });

  test('save changes', async () => {
    const deleteEmailSpy = spyRestApi('deleteAccountEmail');
    const setPreferredSpy = spyRestApi('setPreferredAccountEmail');

    const rows = element
      .shadowRoot!.querySelector('table')!
      .querySelectorAll('tbody tr');

    assert.isFalse(element.hasUnsavedChanges);
    assert.isNotOk(element.newPreferred);
    assert.equal(element.emailsToRemove.length, 0);
    assert.equal(element.emails.length, 3);

    // Delete the first email and set the last as preferred.
    rows[0].querySelector('gr-button')!.click();
    rows[2].querySelector<HTMLInputElement>('input[type=radio]')!.click();

    assert.isTrue(element.hasUnsavedChanges);
    assert.equal(element.newPreferred, 'email@three.com');
    assert.equal(element.emailsToRemove.length, 1);
    assert.equal(element.emailsToRemove[0].email, 'email@one.com');
    assert.equal(element.emails.length, 2);

    await element.save();
    assert.equal(deleteEmailSpy.callCount, 1);
    assert.equal(deleteEmailSpy.getCall(0).args[0], 'email@one.com');
    assert.isTrue(setPreferredSpy.called);
    assert.equal(setPreferredSpy.getCall(0).args[0], 'email@three.com');
  });
});
