/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-editable-label';
import {GrEditableLabel} from './gr-editable-label';
import {queryAndAssert} from '../../../utils/common-util';
import {PaperInputElement} from '@polymer/paper-input/paper-input';
import {GrButton} from '../gr-button/gr-button';
import {assert, fixture, html} from '@open-wc/testing';
import {IronDropdownElement} from '@polymer/iron-dropdown';
import {
  AutocompleteSuggestion,
  GrAutocomplete,
} from '../gr-autocomplete/gr-autocomplete';
import {Key} from '../../../utils/dom-util';
import {pressKey, waitEventLoop, waitUntil} from '../../../test/test-utils';
import {IronInputElement} from '@polymer/iron-input';

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
    label = queryAndAssert<HTMLLabelElement>(element, 'label');
    elementNoPlaceholder = await fixture<GrEditableLabel>(html`
      <gr-editable-label value=""></gr-editable-label>
    `);

    const paperInput = queryAndAssert<PaperInputElement>(element, '#input');
    input = (paperInput.inputElement as IronInputElement)
      .inputElement as HTMLInputElement;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      `<label
      aria-label="value text"
      class="editable"
      part="label"
      title="value text"
    >
      value text
    </label>
    <iron-dropdown
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
              id="saveBtn"
              primary
              role="button"
              tabindex="0"
            >
              Save
            </gr-button>
            <gr-button
              aria-disabled="false"
              id="cancelBtn"
              role="button"
              tabindex="0"
            >
              Cancel
            </gr-button>
          </div>
        </div>
      </div>
    </iron-dropdown>`
    );
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

    await waitEventLoop();
    assert.equal(element.title, 'value text');
  });

  test('edit value', async () => {
    const editedSpy = sinon.spy();
    element.addEventListener('changed', editedSpy);
    assert.isFalse(element.editing);

    label.click();
    await waitEventLoop();

    assert.isTrue(element.editing);
    assert.isFalse(editedSpy.called);

    element.inputText = 'new text';
    pressKey(input, Key.ENTER);
    await waitEventLoop();

    assert.isTrue(editedSpy.called);
    assert.equal(input.value, 'new text');
    assert.isFalse(element.editing);
  });

  test('save button', async () => {
    const editedSpy = sinon.spy();
    element.addEventListener('changed', editedSpy);
    assert.isFalse(element.editing);

    label.click();
    await waitEventLoop();

    assert.isTrue(element.editing);
    assert.isFalse(editedSpy.called);

    element.inputText = 'new text';
    pressKey(queryAndAssert<GrButton>(element, '#saveBtn'), Key.ENTER);
    await waitEventLoop();

    assert.isTrue(editedSpy.called);
    assert.equal(input.value, 'new text');
    assert.isFalse(element.editing);
  });

  test('edit and then escape key', async () => {
    const editedSpy = sinon.spy();
    element.addEventListener('changed', editedSpy);
    assert.isFalse(element.editing);

    label.click();
    await waitEventLoop();

    assert.isTrue(element.editing);
    assert.isFalse(editedSpy.called);

    element.inputText = 'new text';
    pressKey(input, Key.ESC);
    await waitEventLoop();

    assert.isFalse(editedSpy.called);
    // Text changes should be discarded.
    assert.equal(input.value, 'value text');
    assert.isFalse(element.editing);
  });

  test('cancel button', async () => {
    const editedSpy = sinon.spy();
    element.addEventListener('changed', editedSpy);
    assert.isFalse(element.editing);

    label.click();
    await waitEventLoop();

    assert.isTrue(element.editing);
    assert.isFalse(editedSpy.called);

    element.inputText = 'new text';
    // Press escape:
    queryAndAssert<GrButton>(element, '#cancelBtn').click();
    await waitEventLoop();

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

  suite('autocomplete tests', () => {
    let element: GrEditableLabel;
    let autocomplete: GrAutocomplete;
    let suggestions: Array<AutocompleteSuggestion>;
    let labelSaved = false;

    setup(async () => {
      element = await fixture<GrEditableLabel>(html`
        <gr-editable-label
          autocomplete
          value="value text"
          .query=${() => Promise.resolve(suggestions)}
          @changed=${() => {
            labelSaved = true;
          }}
        ></gr-editable-label>
      `);

      autocomplete = element.grAutocomplete!;
    });

    test('autocomplete suggestions shown esc closes suggestions', async () => {
      suggestions = [{name: 'value text 1'}, {name: 'value text 2'}];
      await element.open();

      await waitUntil(() => !autocomplete.suggestionsDropdown!.isHidden);

      pressKey(autocomplete.input!, Key.ESC);

      await waitUntil(() => autocomplete.suggestionsDropdown!.isHidden);
      assert.isTrue(element.dropdown?.opened);
    });

    test('autocomplete suggestions closed esc closes dialogue', async () => {
      suggestions = [{name: 'value text 1'}, {name: 'value text 2'}];
      await element.open();
      await waitUntil(() => !autocomplete.suggestionsDropdown!.isHidden);
      // Press esc to close suggestions.
      pressKey(autocomplete.input!, Key.ESC);
      await waitUntil(() => autocomplete.suggestionsDropdown!.isHidden);

      pressKey(autocomplete.input!, Key.ESC);

      await element.updateComplete;
      // Dialogue is closed, save not triggered.
      assert.isTrue(autocomplete.suggestionsDropdown?.isHidden);
      assert.isFalse(element.dropdown?.opened);
      assert.isFalse(labelSaved);
    });

    test('autocomplete suggestions shown enter chooses suggestions', async () => {
      suggestions = [{name: 'value text 1'}, {name: 'value text 2'}];
      await element.open();

      // Waiting until dropdown not hidden, will ensure dialog is open and input
      // is focused, but not that the suggestion has loaded.
      await waitUntil(() => !autocomplete.suggestionsDropdown!.isHidden);
      await autocomplete.latestSuggestionUpdateComplete;

      pressKey(autocomplete.input!, Key.ENTER);

      await waitUntil(() => autocomplete.suggestionsDropdown!.isHidden);
      await element.updateComplete;
      // The value was picked from suggestions, suggestions are hidden, dialogue
      // is shown, save has not been triggered.
      assert.strictEqual(element.inputText, 'value text 1');
      assert.isTrue(autocomplete.suggestionsDropdown?.isHidden);
      assert.isTrue(element.dropdown?.opened);
      assert.isFalse(labelSaved);
    });

    test('autocomplete suggestions closed enter saves suggestion', async () => {
      suggestions = [{name: 'value text 1'}, {name: 'value text 2'}];
      await element.open();
      // Waiting until dropdown not hidden, will ensure dialog is open and input
      // is focused, but not that the suggestion has loaded.
      await waitUntil(() => !autocomplete.suggestionsDropdown!.isHidden);
      await autocomplete.latestSuggestionUpdateComplete;

      // Press enter to close suggestions.
      pressKey(autocomplete.input!, Key.ENTER);

      await waitUntil(() => autocomplete.suggestionsDropdown!.isHidden);

      pressKey(autocomplete.input!, Key.ENTER);

      await element.updateComplete;
      // Dialogue is closed, save triggered.
      assert.isTrue(autocomplete.suggestionsDropdown?.isHidden);
      assert.isFalse(element.dropdown?.opened);
      assert.isTrue(labelSaved);
    });
  });
});
