/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-editable-label';
import {GrEditableLabel} from './gr-editable-label';
import {queryAndAssert} from '../../../utils/common-util';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {PaperInputElement} from '@polymer/paper-input/paper-input';
import {GrButton} from '../gr-button/gr-button';
import {fixture, html} from '@open-wc/testing-helpers';
import {IronDropdownElement} from '@polymer/iron-dropdown';

suite('gr-editable-label tests', () => {
  let element: GrEditableLabel;
  let elementNoPlaceholder: GrEditableLabel;
  let input: HTMLInputElement;
  let label: HTMLLabelElement;

  setup(async () => {
    element = await fixture<GrEditableLabel>(html`
      <gr-editable-label
        value="value text"
        placeholder="label text"
      ></gr-editable-label>
    `);
    elementNoPlaceholder = await fixture<GrEditableLabel>(html`
      <gr-editable-label value=""></gr-editable-label>
    `);
    label = queryAndAssert<HTMLLabelElement>(element, 'label');

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
    <iron-dropdown
      allowoutsidescroll=""
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

  test('element render', async () => {
    // The dropdown is closed and the label is visible:
    const dropdown = queryAndAssert<IronDropdownElement>(element, '#dropdown');
    assert.isFalse(dropdown.opened);
    assert.isTrue(label.classList.contains('editable'));
    assert.equal(label.textContent, 'value text');

    label.click();
    await element.updateComplete;
    // The dropdown is open (which covers up the label):
    assert.isTrue(dropdown.opened);
    assert.equal(input.value, 'value text');
  });

  test('title with placeholder', async () => {
    assert.equal(element.title, 'value text');
    element.value = '';

    await element.updateComplete;
    assert.equal(element.title, 'label text');
  });

  test('title without placeholder', async () => {
    assert.equal(elementNoPlaceholder.title, '');
    element.value = 'value text';

    await flush();
    assert.equal(element.title, 'value text');
  });

  test('edit value', async () => {
    const editedSpy = sinon.spy();
    element.addEventListener('changed', editedSpy);
    assert.isFalse(element.editing);

    MockInteractions.tap(label);
    await flush();

    assert.isTrue(element.editing);
    assert.isFalse(editedSpy.called);

    element.inputText = 'new text';
    // Press enter:
    MockInteractions.keyDownOn(input, 13, null, 'Enter');
    await flush();

    assert.isTrue(editedSpy.called);
    assert.equal(input.value, 'new text');
    assert.isFalse(element.editing);
  });

  test('save button', async () => {
    const editedSpy = sinon.spy();
    element.addEventListener('changed', editedSpy);
    assert.isFalse(element.editing);

    MockInteractions.tap(label);
    await flush();

    assert.isTrue(element.editing);
    assert.isFalse(editedSpy.called);

    element.inputText = 'new text';
    // Press enter:
    MockInteractions.pressAndReleaseKeyOn(
      queryAndAssert<GrButton>(element, '#saveBtn'),
      13,
      null,
      'Enter'
    );
    await flush();

    assert.isTrue(editedSpy.called);
    assert.equal(input.value, 'new text');
    assert.isFalse(element.editing);
  });

  test('edit and then escape key', async () => {
    const editedSpy = sinon.spy();
    element.addEventListener('changed', editedSpy);
    assert.isFalse(element.editing);

    MockInteractions.tap(label);
    await flush();

    assert.isTrue(element.editing);
    assert.isFalse(editedSpy.called);

    element.inputText = 'new text';
    // Press escape:
    MockInteractions.keyDownOn(input, 27, null, 'Escape');
    await flush();

    assert.isFalse(editedSpy.called);
    // Text changes should be discarded.
    assert.equal(input.value, 'value text');
    assert.isFalse(element.editing);
  });

  test('cancel button', async () => {
    const editedSpy = sinon.spy();
    element.addEventListener('changed', editedSpy);
    assert.isFalse(element.editing);

    MockInteractions.tap(label);
    await flush();

    assert.isTrue(element.editing);
    assert.isFalse(editedSpy.called);

    element.inputText = 'new text';
    // Press escape:
    MockInteractions.tap(queryAndAssert<GrButton>(element, '#cancelBtn'));
    await flush();

    assert.isFalse(editedSpy.called);
    // Text changes should be discarded.
    assert.equal(input.value, 'value text');
    assert.isFalse(element.editing);
  });

  suite('gr-editable-label read-only tests', () => {
    let element: GrEditableLabel;
    let label: HTMLLabelElement;

    setup(async () => {
      element = await fixture<GrEditableLabel>(html`
        <gr-editable-label
          readOnly
          value="value text"
          placeholder="label text"
        ></gr-editable-label>
      `);
      label = queryAndAssert(element, 'label');
    });

    test('disallows edit when read-only', async () => {
      // The dropdown is closed.
      const dropdown = queryAndAssert<IronDropdownElement>(
        element,
        '#dropdown'
      );
      assert.isFalse(dropdown.opened);
      label.click();

      await element.updateComplete;

      // The dropdown is still closed.
      assert.isFalse(dropdown.opened);
    });

    test('label is not marked as editable', () => {
      assert.isFalse(label.classList.contains('editable'));
    });
  });
});
