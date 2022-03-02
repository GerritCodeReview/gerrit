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
import './gr-editable-label';
import {html} from '@polymer/polymer/lib/utils/html-tag';
import {GrEditableLabel} from './gr-editable-label';
import {queryAndAssert} from '../../../utils/common-util';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {PaperInputElement} from '@polymer/paper-input/paper-input';
import {GrButton} from '../gr-button/gr-button';

const basicFixture = fixtureFromTemplate(html`
  <gr-editable-label
    value="value text"
    placeholder="label text"
  ></gr-editable-label>
`);

const noPlaceholderFixture = fixtureFromTemplate(html`
  <gr-editable-label value=""></gr-editable-label>
`);

const readOnlyFixture = fixtureFromTemplate(html`
  <gr-editable-label
    read-only
    value="value text"
    placeholder="label text"
  ></gr-editable-label>
`);

suite('gr-editable-label tests', () => {
  let element: GrEditableLabel;
  let elementNoPlaceholder: GrEditableLabel;
  let input: HTMLInputElement;
  let label: HTMLLabelElement;

  setup(async () => {
    element = basicFixture.instantiate() as GrEditableLabel;
    elementNoPlaceholder =
      noPlaceholderFixture.instantiate() as GrEditableLabel;
    await flush();
    label = queryAndAssert<HTMLLabelElement>(element, 'label');

    await flush();
    // In Polymer 2 inputElement isn't nativeInput anymore
    const paperInput = queryAndAssert<PaperInputElement>(element, '#input');
    input = (paperInput.$.nativeInput ||
      paperInput.inputElement) as HTMLInputElement;
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(`<label
      aria-label="value text"
      class="editable"
      part="label"
      title="value text"
    >
      value text
    </label>
    <dom-if style="display: none;"><template is="dom-if"></template></dom-if>
    <dom-if style="display: none;"><template is="dom-if"></template></dom-if>
    <iron-dropdown
      allow-outside-scroll="true"
      aria-disabled="false"
      aria-hidden="true"
      horizontal-align="auto"
      id="dropdown"
      style="outline: none; display: none;"
      vertical-align="auto"
    >
      <div class="dropdown-content" slot="dropdown-content">
        <div class="inputContainer" part="input-container">
          <paper-input
            aria-disabled="false"
            id="input"
            tabindex="0"
          ></paper-input>
          <dom-if style="display: none;"><template is="dom-if"></template>
          </dom-if>
          <dom-if style="display: none;"><template is="dom-if"></template>
          </dom-if>
          <div class="buttons">
            <gr-button
              aria-disabled="false"
              id="cancelBtn"
              link=""
              role="button"
              tabindex="0"
            >
              cancel
            </gr-button>
            <gr-button
              aria-disabled="false"
              id="saveBtn"
              link=""
              role="button"
              tabindex="0"
            >
              save
            </gr-button>
          </div>
        </div>
      </div>
    </iron-dropdown>`);
  });

  test('element render', () => {
    // The dropdown is closed and the label is visible:
    assert.isFalse(element.$.dropdown.opened);
    assert.isTrue(label.classList.contains('editable'));
    assert.equal(label.textContent, 'value text');
    const focusSpy = sinon.spy(input, 'focus');
    const showSpy = sinon.spy(element, '_showDropdown');

    MockInteractions.tap(label);

    return showSpy.lastCall.returnValue!.then(() => {
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
    MockInteractions.pressAndReleaseKeyOn(
      queryAndAssert<GrButton>(element, '#saveBtn'),
      13,
      null,
      'Enter'
    );
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
    MockInteractions.tap(queryAndAssert<GrButton>(element, '#cancelBtn'));
    flush();

    assert.isFalse(editedSpy.called);
    // Text changes should be discarded.
    assert.equal(input.value, 'value text');
    assert.isFalse(element.editing);
  });

  suite('gr-editable-label read-only tests', () => {
    let element: GrEditableLabel;
    let label: HTMLLabelElement;

    setup(async () => {
      element = readOnlyFixture.instantiate() as GrEditableLabel;
      await flush();
      label = queryAndAssert(element, 'label');
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
