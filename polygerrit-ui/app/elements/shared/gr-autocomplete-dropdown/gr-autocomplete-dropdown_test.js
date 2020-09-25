/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import './gr-autocomplete-dropdown.js';

const basicFixture = fixtureFromElement('gr-autocomplete-dropdown');

suite('gr-autocomplete-dropdown', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
    element.open();
    element.suggestions = [
      {dataValue: 'test value 1', name: 'test name 1', text: 1, label: 'hi'},
      {dataValue: 'test value 2', name: 'test name 2', text: 2}];
    flush();
  });

  teardown(() => {
    if (element.isOpen) element.close();
  });

  test('shows labels', () => {
    const els = element.$.suggestions.querySelectorAll('li');
    assert.equal(els[0].innerText.trim(), '1\nhi');
    assert.equal(els[1].innerText.trim(), '2');
  });

  test('escape key', done => {
    const closeSpy = sinon.spy(element, 'close');
    MockInteractions.pressAndReleaseKeyOn(element, 27);
    flush();
    assert.isTrue(closeSpy.called);
    done();
  });

  test('tab key', () => {
    const handleTabSpy = sinon.spy(element, '_handleTab');
    const itemSelectedStub = sinon.stub();
    element.addEventListener('item-selected', itemSelectedStub);
    MockInteractions.pressAndReleaseKeyOn(element, 9);
    assert.isTrue(handleTabSpy.called);
    assert.equal(element.$.cursor.index, 0);
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
    MockInteractions.pressAndReleaseKeyOn(element, 13);
    assert.isTrue(handleEnterSpy.called);
    assert.equal(element.$.cursor.index, 0);
    assert.deepEqual(itemSelectedStub.lastCall.args[0].detail, {
      trigger: 'enter',
      selected: element.getCursorTarget(),
    });
  });

  test('down key', () => {
    element.isHidden = true;
    const nextSpy = sinon.spy(element.$.cursor, 'next');
    MockInteractions.pressAndReleaseKeyOn(element, 40);
    assert.isFalse(nextSpy.called);
    assert.equal(element.$.cursor.index, 0);
    element.isHidden = false;
    MockInteractions.pressAndReleaseKeyOn(element, 40);
    assert.isTrue(nextSpy.called);
    assert.equal(element.$.cursor.index, 1);
  });

  test('up key', () => {
    element.isHidden = true;
    const prevSpy = sinon.spy(element.$.cursor, 'previous');
    MockInteractions.pressAndReleaseKeyOn(element, 38);
    assert.isFalse(prevSpy.called);
    assert.equal(element.$.cursor.index, 0);
    element.isHidden = false;
    element.$.cursor.setCursorAtIndex(1);
    assert.equal(element.$.cursor.index, 1);
    MockInteractions.pressAndReleaseKeyOn(element, 38);
    assert.isTrue(prevSpy.called);
    assert.equal(element.$.cursor.index, 0);
  });

  test('tapping selects item', () => {
    const itemSelectedStub = sinon.stub();
    element.addEventListener('item-selected', itemSelectedStub);

    MockInteractions.tap(element.$.suggestions.querySelectorAll('li')[1]);
    flush();
    assert.deepEqual(itemSelectedStub.lastCall.args[0].detail, {
      trigger: 'click',
      selected: element.$.suggestions.querySelectorAll('li')[1],
    });
  });

  test('tapping child still selects item', () => {
    const itemSelectedStub = sinon.stub();
    element.addEventListener('item-selected', itemSelectedStub);

    MockInteractions.tap(element.$.suggestions.querySelectorAll('li')[0]
        .lastElementChild);
    flush();
    assert.deepEqual(itemSelectedStub.lastCall.args[0].detail, {
      trigger: 'click',
      selected: element.$.suggestions.querySelectorAll('li')[0],
    });
  });

  test('updated suggestions resets cursor stops', () => {
    const resetStopsSpy = sinon.spy(element, '_resetCursorStops');
    element.suggestions = [];
    assert.isTrue(resetStopsSpy.called);
  });
});

