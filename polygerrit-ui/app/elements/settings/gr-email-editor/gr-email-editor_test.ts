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
import './gr-email-editor.js';
import {GrEmailEditor} from './gr-email-editor';

const basicFixture = fixtureFromElement('gr-email-editor');

suite('gr-email-editor tests', () => {
  let element: GrEmailEditor;

  setup(async () => {
    const emails = [
      {email: 'email@one.com'},
      {email: 'email@two.com', preferred: true},
      {email: 'email@three.com'},
    ];

    stub('gr-rest-api-interface', {
      getAccountEmails() {
        return Promise.resolve(emails);
      },
    });

    element = basicFixture.instantiate();

    await element.loadData();
    await flush();
  });

  test('renders', () => {
    const rows = element
      .shadowRoot!.querySelector('table')!
      .querySelectorAll('tbody tr') as NodeListOf<HTMLTableRowElement>;

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
      .querySelectorAll('input[type=radio]') as NodeListOf<HTMLInputElement>;

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

  test('save changes', done => {
    const deleteEmailStub = sinon.stub(element.$.restAPI, 'deleteAccountEmail');
    const setPreferredStub = sinon.stub(
      element.$.restAPI,
      'setPreferredAccountEmail'
    );

    const rows = element
      .shadowRoot!.querySelector('table')!
      .querySelectorAll('tbody tr');

    assert.isFalse(element.hasUnsavedChanges);
    assert.isNotOk(element._newPreferred);
    assert.equal(element._emailsToRemove.length, 0);
    assert.equal(element._emails.length, 3);

    // Delete the first email and set the last as preferred.
    rows[0].querySelector('gr-button')!.click();
    (rows[2].querySelector('input[type=radio]')! as HTMLInputElement).click();

    assert.isTrue(element.hasUnsavedChanges);
    assert.equal(element._newPreferred, 'email@three.com');
    assert.equal(element._emailsToRemove.length, 1);
    assert.equal(element._emailsToRemove[0].email, 'email@one.com');
    assert.equal(element._emails.length, 2);

    // Save the changes.
    element.save().then(() => {
      assert.equal(deleteEmailStub.callCount, 1);
      assert.equal(deleteEmailStub.getCall(0).args[0], 'email@one.com');

      assert.isTrue(setPreferredStub.called);
      assert.equal(setPreferredStub.getCall(0).args[0], 'email@three.com');

      done();
    });
  });
});
