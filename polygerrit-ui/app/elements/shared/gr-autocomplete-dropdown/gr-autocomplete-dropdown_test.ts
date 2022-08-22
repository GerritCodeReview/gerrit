/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-autocomplete-dropdown';
import {GrAutocompleteDropdown} from './gr-autocomplete-dropdown';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {queryAll, queryAndAssert} from '../../../test/test-utils';
import {assertIsDefined} from '../../../utils/common-util';
import {fixture, html} from '@open-wc/testing-helpers';

suite('gr-autocomplete-dropdown', () => {
  let element: GrAutocompleteDropdown;

  const suggestionsEl = () => queryAndAssert(element, '#suggestions');

  setup(async () => {
    element = await fixture(
      html`<gr-autocomplete-dropdown></gr-autocomplete-dropdown>`
    );
    element.open();
    element.suggestions = [
      {dataValue: 'test value 1', name: 'test name 1', text: '1', label: 'hi'},
      {dataValue: 'test value 2', name: 'test name 2', text: '2'},
    ];
    await flush();
  });

  teardown(() => {
    element.close();
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div
          class="dropdown-content"
          id="suggestions"
          role="listbox"
          slot="dropdown-content"
        >
          <ul>
            <li
              aria-label="test name 1"
              class="autocompleteOption selected"
              data-index="0"
              data-value="test value 1"
              role="option"
              tabindex="-1"
            >
              <span> 1 </span>
              <span class="label"> hi </span>
            </li>
            <li
              aria-label="test name 2"
              class="autocompleteOption"
              data-index="1"
              data-value="test value 2"
              role="option"
              tabindex="-1"
            >
              <span> 2 </span>
              <span class="hide label"> </span>
            </li>
            <dom-repeat style="display: none;">
              <template is="dom-repeat"> </template>
            </dom-repeat>
          </ul>
        </div>
      `
    );
  });

  test('shows labels', () => {
    const els = queryAll<HTMLElement>(suggestionsEl(), 'li');
    assert.equal(els[0].innerText.trim(), '1\nhi');
    assert.equal(els[1].innerText.trim(), '2');
  });

  test('escape key', () => {
    const closeSpy = sinon.spy(element, 'close');
    MockInteractions.pressAndReleaseKeyOn(element, 27, null, 'Escape');
    flush();
    assert.isTrue(closeSpy.called);
  });

  test('tab key', () => {
    const handleTabSpy = sinon.spy(element, '_handleTab');
    const itemSelectedStub = sinon.stub();
    element.addEventListener('item-selected', itemSelectedStub);
    MockInteractions.pressAndReleaseKeyOn(element, 9, null, 'Tab');
    assert.isTrue(handleTabSpy.called);
    assert.equal(element.cursor.index, 0);
    assert.isTrue(itemSelectedStub.called);
    assert.deepEqual(itemSelectedStub.lastCall.args[0].detail, {
      trigger: 'tab',
      selected: element.getCursorTarget(),
    });
  });

  test('enter key', () => {
    const handleEnterSpy = sinon.spy(element, '_handleEnter');
    const itemSelectedStub = sinon.stub();
    element.addEventListener('item-selected', itemSelectedStub);
    MockInteractions.pressAndReleaseKeyOn(element, 13, null, 'Enter');
    assert.isTrue(handleEnterSpy.called);
    assert.equal(element.cursor.index, 0);
    assert.deepEqual(itemSelectedStub.lastCall.args[0].detail, {
      trigger: 'enter',
      selected: element.getCursorTarget(),
    });
  });

  test('down key', () => {
    element.isHidden = true;
    const nextSpy = sinon.spy(element.cursor, 'next');
    MockInteractions.pressAndReleaseKeyOn(element, 40, null, 'ArrowDown');
    assert.isFalse(nextSpy.called);
    assert.equal(element.cursor.index, 0);
    element.isHidden = false;
    MockInteractions.pressAndReleaseKeyOn(element, 40, null, 'ArrowDown');
    assert.isTrue(nextSpy.called);
    assert.equal(element.cursor.index, 1);
  });

  test('up key', () => {
    element.isHidden = true;
    const prevSpy = sinon.spy(element.cursor, 'previous');
    MockInteractions.pressAndReleaseKeyOn(element, 38, null, 'ArrowUp');
    assert.isFalse(prevSpy.called);
    assert.equal(element.cursor.index, 0);
    element.isHidden = false;
    element.cursor.setCursorAtIndex(1);
    assert.equal(element.cursor.index, 1);
    MockInteractions.pressAndReleaseKeyOn(element, 38, null, 'ArrowUp');
    assert.isTrue(prevSpy.called);
    assert.equal(element.cursor.index, 0);
  });

  test('tapping selects item', () => {
    const itemSelectedStub = sinon.stub();
    element.addEventListener('item-selected', itemSelectedStub);

    MockInteractions.tap(suggestionsEl().querySelectorAll('li')[1]);
    flush();
    assert.deepEqual(itemSelectedStub.lastCall.args[0].detail, {
      trigger: 'click',
      selected: suggestionsEl().querySelectorAll('li')[1],
    });
  });

  test('tapping child still selects item', () => {
    const itemSelectedStub = sinon.stub();
    element.addEventListener('item-selected', itemSelectedStub);
    const lastElChild = queryAll<HTMLElement>(suggestionsEl(), 'li')[0]
      ?.lastElementChild;
    assertIsDefined(lastElChild);
    MockInteractions.tap(lastElChild);
    flush();
    assert.deepEqual(itemSelectedStub.lastCall.args[0].detail, {
      trigger: 'click',
      selected: queryAll<HTMLElement>(suggestionsEl(), 'li')[0],
    });
  });

  test('updated suggestions resets cursor stops', () => {
    const resetStopsSpy = sinon.spy(element, 'onSuggestionsChanged');
    element.suggestions = [];
    assert.isTrue(resetStopsSpy.called);
  });
});
