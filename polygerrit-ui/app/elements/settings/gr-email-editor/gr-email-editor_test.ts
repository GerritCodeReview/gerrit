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
import './gr-email-editor';
import {GrEmailEditor} from './gr-email-editor';
import {spyRestApi, stubRestApi} from '../../../test/test-utils';

const basicFixture = fixtureFromElement('gr-email-editor');

suite('gr-email-editor tests', () => {
  let element: GrEmailEditor;

  setup(async () => {
    const emails = [
      {email: 'email@one.com'},
      {email: 'email@two.com', preferred: true},
      {email: 'email@three.com'},
    ];

    stubRestApi('getAccountEmails').returns(Promise.resolve(emails));

    element = basicFixture.instantiate();

    await element.loadData();
    await flush();
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `<div class="gr-form-styles">
      <dom-repeat style="display: none;">
        <template is="dom-repeat"> </template>
      </dom-repeat>
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
              <iron-input class="preferredRadio" name="preferred" type="radio">
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
              <iron-input
                checked=""
                class="preferredRadio"
                name="preferred"
                type="radio"
              >
                <input
                  checked="true"
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
              <iron-input class="preferredRadio" name="preferred" type="radio">
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
    const preferredChangedSpy = sinon.spy(element, '_handlePreferredChange');
    const radios = element
      .shadowRoot!.querySelector('table')!
      .querySelectorAll<HTMLInputElement>('input[type=radio]');

    assert.isFalse(element.hasUnsavedChanges);
    assert.isNotOk(element._newPreferred);
    assert.equal(element._emailsToRemove.length, 0);
    assert.equal(element._emails.length, 3);
    assert.isNotOk(radios[0].checked);
    assert.isOk(radios[1].checked);
    assert.isFalse(preferredChangedSpy.called);

    radios[0].click();

    assert.isTrue(element.hasUnsavedChanges);
    assert.isOk(element._newPreferred);
    assert.equal(element._emailsToRemove.length, 0);
    assert.equal(element._emails.length, 3);
    assert.isOk(radios[0].checked);
    assert.isNotOk(radios[1].checked);
    assert.isTrue(preferredChangedSpy.called);
  });

  test('delete email', () => {
    const buttons = element
      .shadowRoot!.querySelector('table')!
      .querySelectorAll('gr-button');

    assert.isFalse(element.hasUnsavedChanges);
    assert.isNotOk(element._newPreferred);
    assert.equal(element._emailsToRemove.length, 0);
    assert.equal(element._emails.length, 3);

    buttons[2].click();

    assert.isTrue(element.hasUnsavedChanges);
    assert.isNotOk(element._newPreferred);
    assert.equal(element._emailsToRemove.length, 1);
    assert.equal(element._emails.length, 2);

    assert.equal(element._emailsToRemove[0].email, 'email@three.com');
  });

  test('save changes', async () => {
    const deleteEmailSpy = spyRestApi('deleteAccountEmail');
    const setPreferredSpy = spyRestApi('setPreferredAccountEmail');

    const rows = element
      .shadowRoot!.querySelector('table')!
      .querySelectorAll('tbody tr');

    assert.isFalse(element.hasUnsavedChanges);
    assert.isNotOk(element._newPreferred);
    assert.equal(element._emailsToRemove.length, 0);
    assert.equal(element._emails.length, 3);

    // Delete the first email and set the last as preferred.
    rows[0].querySelector('gr-button')!.click();
    rows[2].querySelector<HTMLInputElement>('input[type=radio]')!.click();

    assert.isTrue(element.hasUnsavedChanges);
    assert.equal(element._newPreferred, 'email@three.com');
    assert.equal(element._emailsToRemove.length, 1);
    assert.equal(element._emailsToRemove[0].email, 'email@one.com');
    assert.equal(element._emails.length, 2);

    await element.save();
    assert.equal(deleteEmailSpy.callCount, 1);
    assert.equal(deleteEmailSpy.getCall(0).args[0], 'email@one.com');
    assert.isTrue(setPreferredSpy.called);
    assert.equal(setPreferredSpy.getCall(0).args[0], 'email@three.com');
  });
});
