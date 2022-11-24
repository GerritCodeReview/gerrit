/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-autocomplete-dropdown';
import {GrAutocompleteDropdown} from './gr-autocomplete-dropdown';
import {
  pressKey,
  queryAll,
  queryAndAssert,
  waitEventLoop,
  waitUntil,
} from '../../../test/test-utils';
import {assertIsDefined} from '../../../utils/common-util';
import {fixture, html, assert} from '@open-wc/testing';
import {Key} from '../../../utils/dom-util';

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
    await waitEventLoop();
  });

  teardown(() => {
    element.close();
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="dropdown-content" id="suggestions" role="listbox">
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

  test('escape key', async () => {
    const closeSpy = sinon.spy(element, 'close');
    pressKey(element, Key.ESC);
    await waitEventLoop();
    assert.isTrue(closeSpy.called);
  });

  test('tab key', () => {
    const handleTabSpy = sinon.spy(element, 'handleTab');
    const itemSelectedStub = sinon.stub();
    element.addEventListener('item-selected', itemSelectedStub);
    pressKey(element, Key.TAB);
    assert.isTrue(handleTabSpy.called);
    assert.equal(element.cursor.index, 0);
    assert.isTrue(itemSelectedStub.called);
    assert.deepEqual(itemSelectedStub.lastCall.args[0].detail, {
      trigger: 'tab',
      selected: element.getCursorTarget(),
    });
  });

  test('enter key', () => {
    const handleEnterSpy = sinon.spy(element, 'handleEnter');
    const itemSelectedStub = sinon.stub();
    element.addEventListener('item-selected', itemSelectedStub);
    pressKey(element, Key.ENTER);
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
    pressKey(element, 'ArrowDown');
    assert.isFalse(nextSpy.called);
    assert.equal(element.cursor.index, 0);
    element.isHidden = false;
    pressKey(element, 'ArrowDown');
    assert.isTrue(nextSpy.called);
    assert.equal(element.cursor.index, 1);
  });

  test('up key', () => {
    element.isHidden = true;
    const prevSpy = sinon.spy(element.cursor, 'previous');
    pressKey(element, 'ArrowUp');
    assert.isFalse(prevSpy.called);
    assert.equal(element.cursor.index, 0);
    element.isHidden = false;
    element.cursor.setCursorAtIndex(1);
    assert.equal(element.cursor.index, 1);
    pressKey(element, 'ArrowUp');
    assert.isTrue(prevSpy.called);
    assert.equal(element.cursor.index, 0);
  });

  test('tapping selects item', async () => {
    const itemSelectedStub = sinon.stub();
    element.addEventListener('item-selected', itemSelectedStub);

    suggestionsEl().querySelectorAll('li')[1].click();
    await waitEventLoop();
    assert.deepEqual(itemSelectedStub.lastCall.args[0].detail, {
      trigger: 'click',
      selected: suggestionsEl().querySelectorAll('li')[1],
    });
  });

  test('tapping child still selects item', async () => {
    const itemSelectedStub = sinon.stub();
    element.addEventListener('item-selected', itemSelectedStub);
    const lastElChild = queryAll<HTMLLIElement>(suggestionsEl(), 'li')[0]
      ?.lastElementChild;
    assertIsDefined(lastElChild);
    (lastElChild as HTMLSpanElement).click();
    await waitEventLoop();
    assert.deepEqual(itemSelectedStub.lastCall.args[0].detail, {
      trigger: 'click',
      selected: queryAll<HTMLElement>(suggestionsEl(), 'li')[0],
    });
  });

  test('updated suggestions resets cursor stops', async () => {
    const resetStopsSpy = sinon.spy(element, 'computeCursorStopsAndRefit');
    element.suggestions = [];
    await waitUntil(() => resetStopsSpy.called);
  });
});
