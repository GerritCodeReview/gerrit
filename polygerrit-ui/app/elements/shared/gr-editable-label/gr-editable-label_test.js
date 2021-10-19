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
import './gr-editable-label.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

const basicFixture = fixtureFromTemplate(html`
<gr-editable-label
        value="value text"
        placeholder="label text"></gr-editable-label>
`);

const noPlaceholderFixture = fixtureFromTemplate(html`
<gr-editable-label value=""></gr-editable-label>
`);

const readOnlyFixture = fixtureFromTemplate(html`
<gr-editable-label
        read-only
        value="value text"
        placeholder="label text"></gr-editable-label>
`);

suite('gr-editable-label tests', () => {
  let element;
  let elementNoPlaceholder;
  let input;
  let label;

  setup(async () => {
    element = basicFixture.instantiate();
    elementNoPlaceholder = noPlaceholderFixture.instantiate();
    flush();
    label = element.shadowRoot.querySelector('label');

    await flush();
    // In Polymer 2 inputElement isn't nativeInput anymore
    const paperInput = element.shadowRoot.querySelector('#input');
    input = paperInput.$.nativeInput || paperInput.inputElement;
  });

  test('element render', () => {
    // The dropdown is closed and the label is visible:
    assert.isFalse(element.$.dropdown.opened);
    assert.isTrue(label.classList.contains('editable'));
    assert.equal(label.textContent, 'value text');
    const focusSpy = sinon.spy(input, 'focus');
    const showSpy = sinon.spy(element, '_showDropdown');

    MockInteractions.tap(label);

    return showSpy.lastCall.returnValue.then(() => {
      // The dropdown is open (which covers up the label):
      assert.isTrue(element.$.dropdown.opened);
      assert.isTrue(focusSpy.called);
      assert.equal(input.value, 'value text');
    });
  });

  test('title with placeholder', () => {
    assert.equal(element.title, 'value text');
    element.value = '';

    flush();
    assert.equal(element.title, 'label text');
  });

  test('title without placeholder', () => {
    assert.equal(elementNoPlaceholder.title, '');
    element.value = 'value text';

    flush();
    assert.equal(element.title, 'value text');
  });

  test('edit value', async () => {
    const editedSpy = sinon.spy();
    element.addEventListener('changed', editedSpy);
    assert.isFalse(element.editing);

    MockInteractions.tap(label);
    flush();

    assert.isTrue(element.editing);
    assert.isFalse(editedSpy.called);

    element._inputText = 'new text';
    // Press enter:
    MockInteractions.keyDownOn(input, 13, null, 'Enter');
    flush();

    assert.isTrue(editedSpy.called);
    assert.equal(input.value, 'new text');
    assert.isFalse(element.editing);
  });

  test('save button', () => {
    const editedSpy = sinon.spy();
    element.addEventListener('changed', editedSpy);
    assert.isFalse(element.editing);

    MockInteractions.tap(label);
    flush();

    assert.isTrue(element.editing);
    assert.isFalse(editedSpy.called);

    element._inputText = 'new text';
    // Press enter:
    MockInteractions.tap(element.$.saveBtn, 13, null, 'Enter');
    flush();

    assert.isTrue(editedSpy.called);
    assert.equal(input.value, 'new text');
    assert.isFalse(element.editing);
  });

  test('edit and then escape key', () => {
    const editedSpy = sinon.spy();
    element.addEventListener('changed', editedSpy);
    assert.isFalse(element.editing);

    MockInteractions.tap(label);
    flush();

    assert.isTrue(element.editing);
    assert.isFalse(editedSpy.called);

    element._inputText = 'new text';
    // Press escape:
    MockInteractions.keyDownOn(input, 27, null, 'Escape');
    flush();

    assert.isFalse(editedSpy.called);
    // Text changes should be discarded.
    assert.equal(input.value, 'value text');
    assert.isFalse(element.editing);
  });

  test('cancel button', () => {
    const editedSpy = sinon.spy();
    element.addEventListener('changed', editedSpy);
    assert.isFalse(element.editing);

    MockInteractions.tap(label);
    flush();

    assert.isTrue(element.editing);
    assert.isFalse(editedSpy.called);

    element._inputText = 'new text';
    // Press escape:
    MockInteractions.tap(element.$.cancelBtn);
    flush();

    assert.isFalse(editedSpy.called);
    // Text changes should be discarded.
    assert.equal(input.value, 'value text');
    assert.isFalse(element.editing);
  });

  suite('gr-editable-label read-only tests', () => {
    let element;
    let label;

    setup(() => {
      element = readOnlyFixture.instantiate();
      flush();
      label = element.shadowRoot
          .querySelector('label');
    });

    test('disallows edit when read-only', () => {
      // The dropdown is closed.
      assert.isFalse(element.$.dropdown.opened);
      MockInteractions.tap(label);

      flush();

      // The dropdown is still closed.
      assert.isFalse(element.$.dropdown.opened);
    });

    test('label is not marked as editable', () => {
      assert.isFalse(label.classList.contains('editable'));
    });
  });
});

