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
import './gr-cursor-manager.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';
import {AbortStop, CursorMoveResult} from './gr-cursor-manager.js';

const basicTestFixutre = fixtureFromTemplate(html`
    <gr-cursor-manager cursor-target-class="targeted"></gr-cursor-manager>
    <ul>
      <li>A</li>
      <li>B</li>
      <li>C</li>
      <li>D</li>
    </ul>
`);

suite('gr-cursor-manager tests', () => {
  let element;
  let list;

  setup(() => {
    const fixtureElements = basicTestFixutre.instantiate();
    element = fixtureElements[0];
    list = fixtureElements[1];
  });

  test('core cursor functionality', () => {
    // The element is initialized into the proper state.
    assert.isArray(element.stops);
    assert.equal(element.stops.length, 0);
    assert.equal(element.index, -1);
    assert.isNotOk(element.target);

    // Initialize the cursor with its stops.
    element.stops = [...list.querySelectorAll('li')];

    // It should have the stops but it should not be targeting any of them.
    assert.isNotNull(element.stops);
    assert.equal(element.stops.length, 4);
    assert.equal(element.index, -1);
    assert.isNotOk(element.target);

    // Select the third stop.
    element.setCursor(list.children[2]);

    // It should update its internal state and update the element's class.
    assert.equal(element.index, 2);
    assert.equal(element.target, list.children[2]);
    assert.isTrue(list.children[2].classList.contains('targeted'));
    assert.isFalse(element.isAtStart());
    assert.isFalse(element.isAtEnd());

    // Progress the cursor.
    let result = element.next();

    // Confirm that the next stop is selected and that the previous stop is
    // unselected.
    assert.equal(result, CursorMoveResult.MOVED);
    assert.equal(element.index, 3);
    assert.equal(element.target, list.children[3]);
    assert.isTrue(element.isAtEnd());
    assert.isFalse(list.children[2].classList.contains('targeted'));
    assert.isTrue(list.children[3].classList.contains('targeted'));

    // Progress the cursor.
    result = element.next();

    // We should still be at the end.
    assert.equal(result, CursorMoveResult.CLIPPED);
    assert.equal(element.index, 3);
    assert.equal(element.target, list.children[3]);
    assert.isTrue(element.isAtEnd());

    // Wind the cursor all the way back to the first stop.
    result = element.previous();
    assert.equal(result, CursorMoveResult.MOVED);
    result = element.previous();
    assert.equal(result, CursorMoveResult.MOVED);
    result = element.previous();
    assert.equal(result, CursorMoveResult.MOVED);

    // The element state should reflect the start of the list.
    assert.equal(element.index, 0);
    assert.equal(element.target, list.children[0]);
    assert.isTrue(element.isAtStart());
    assert.isTrue(list.children[0].classList.contains('targeted'));

    const newLi = document.createElement('li');
    newLi.textContent = 'Z';
    list.insertBefore(newLi, list.children[0]);
    element.stops = [...list.querySelectorAll('li')];

    assert.equal(element.index, 1);

    // De-select all targets.
    element.unsetCursor();

    // There should now be no cursor target.
    assert.isFalse(list.children[1].classList.contains('targeted'));
    assert.isNotOk(element.target);
    assert.equal(element.index, -1);
  });

  test('isAtStart() returns true when there are no stops', () => {
    element.stops = [];
    assert.isTrue(element.isAtStart());
  });

  test('isAtEnd() returns true when there are no stops', () => {
    element.stops = [];
    assert.isTrue(element.isAtEnd());
  });

  test('next() goes to first element when no cursor is set', () => {
    element.stops = [...list.querySelectorAll('li')];
    const result = element.next();

    assert.equal(result, CursorMoveResult.MOVED);
    assert.equal(element.index, 0);
    assert.equal(element.target, list.children[0]);
    assert.isTrue(list.children[0].classList.contains('targeted'));
    assert.isTrue(element.isAtStart());
    assert.isFalse(element.isAtEnd());
  });

  test('next() resets the cursor when there are no stops', () => {
    element.stops = [];
    const result = element.next();

    assert.equal(result, CursorMoveResult.NO_STOPS);
    assert.equal(element.index, -1);
    assert.isNotOk(element.target);
    assert.isFalse(list.children[1].classList.contains('targeted'));
  });

  test('previous() goes to last element when no cursor is set', () => {
    element.stops = [...list.querySelectorAll('li')];
    const result = element.previous();

    assert.equal(result, CursorMoveResult.MOVED);
    const lastIndex = list.children.length - 1;
    assert.equal(element.index, lastIndex);
    assert.equal(element.target, list.children[lastIndex]);
    assert.isTrue(list.children[lastIndex].classList.contains('targeted'));
    assert.isFalse(element.isAtStart());
    assert.isTrue(element.isAtEnd());
  });

  test('previous() resets the cursor when there are no stops', () => {
    element.stops = [];
    const result = element.previous();

    assert.equal(result, CursorMoveResult.NO_STOPS);
    assert.equal(element.index, -1);
    assert.isNotOk(element.target);
    assert.isFalse(list.children[1].classList.contains('targeted'));
  });

  test('_moveCursor', () => {
    // Initialize the cursor with its stops.
    element.stops = [...list.querySelectorAll('li')];
    // Select the first stop.
    element.setCursor(list.children[0]);
    const getTargetHeight = sinon.stub();

    // Move the cursor without an optional get target height function.
    element._moveCursor(1);
    assert.isFalse(getTargetHeight.called);

    // Move the cursor with an optional get target height function.
    element._moveCursor(1, {getTargetHeight});
    assert.isTrue(getTargetHeight.called);
  });

  test('_moveCursor from for invalid index does not check height', () => {
    element.stops = [];
    const getTargetHeight = sinon.stub();
    element._moveCursor(1, () => false, {getTargetHeight});
    assert.isFalse(getTargetHeight.called);
  });

  test('setCursorAtIndex with noScroll', () => {
    sinon.stub(element, '_targetIsVisible').callsFake(() => false);
    const scrollStub = sinon.stub(window, 'scrollTo');
    element.stops = [...list.querySelectorAll('li')];
    element.scrollMode = 'keep-visible';

    element.setCursorAtIndex(1, true);
    assert.isFalse(scrollStub.called);

    element.setCursorAtIndex(2);
    assert.isTrue(scrollStub.called);
  });

  test('move with filter', () => {
    const isLetterB = function(row) {
      return row.textContent === 'B';
    };
    element.stops = [...list.querySelectorAll('li')];
    // Start cursor at the first stop.
    element.setCursor(list.children[0]);

    // Move forward to meet the next condition.
    element.next({filter: isLetterB});
    assert.equal(element.index, 1);

    // Nothing else meets the condition, should be at last stop.
    element.next({filter: isLetterB});
    assert.equal(element.index, 3);

    // Should stay at last stop if try to proceed.
    element.next({filter: isLetterB});
    assert.equal(element.index, 3);

    // Go back to the previous condition met. Should be back at.
    // stop 1.
    element.previous({filter: isLetterB});
    assert.equal(element.index, 1);

    // Go back. No more meet the condition. Should be at stop 0.
    element.previous({filter: isLetterB});
    assert.equal(element.index, 0);
  });

  test('focusOnMove prop', () => {
    const listEls = [...list.querySelectorAll('li')];
    for (let i = 0; i < listEls.length; i++) {
      sinon.spy(listEls[i], 'focus');
    }
    element.stops = listEls;
    element.setCursor(list.children[0]);

    element.focusOnMove = false;
    element.next();
    assert.isFalse(element.target.focus.called);

    element.focusOnMove = true;
    element.next();
    assert.isTrue(element.target.focus.called);
  });

  suite('_scrollToTarget', () => {
    let scrollStub;
    setup(() => {
      element.stops = [...list.querySelectorAll('li')];
      element.scrollMode = 'keep-visible';

      // There is a target which has a targetNext
      element.setCursor(list.children[0]);
      element._moveCursor(1);
      scrollStub = sinon.stub(window, 'scrollTo');
      window.innerHeight = 60;
    });

    test('Called when top and bottom not visible', () => {
      sinon.stub(element, '_targetIsVisible').returns(false);
      element._scrollToTarget();
      assert.isTrue(scrollStub.called);
    });

    test('Not called when top and bottom visible', () => {
      sinon.stub(element, '_targetIsVisible').returns(true);
      element._scrollToTarget();
      assert.isFalse(scrollStub.called);
    });

    test('Called when top is visible, bottom is not, scroll is lower', () => {
      const visibleStub = sinon.stub(element, '_targetIsVisible').callsFake(
          () => visibleStub.callCount === 2);
      sinon.stub(element, '_getWindowDims').returns({
        scrollX: 123,
        scrollY: 15,
        innerHeight: 1000,
        pageYOffset: 0,
      });
      sinon.stub(element, '_calculateScrollToValue').returns(20);
      element._scrollToTarget();
      assert.isTrue(scrollStub.called);
      assert.isTrue(scrollStub.calledWithExactly(123, 20));
      assert.equal(visibleStub.callCount, 2);
    });

    test('Called when top is visible, bottom not, scroll is higher', () => {
      const visibleStub = sinon.stub(element, '_targetIsVisible').callsFake(
          () => visibleStub.callCount === 2);
      sinon.stub(element, '_getWindowDims').returns({
        scrollX: 123,
        scrollY: 25,
        innerHeight: 1000,
        pageYOffset: 0,
      });
      sinon.stub(element, '_calculateScrollToValue').returns(20);
      element._scrollToTarget();
      assert.isFalse(scrollStub.called);
      assert.equal(visibleStub.callCount, 2);
    });

    test('_calculateScrollToValue', () => {
      sinon.stub(element, '_getWindowDims').returns({
        scrollX: 123,
        scrollY: 25,
        innerHeight: 300,
        pageYOffset: 0,
      });
      assert.equal(element._calculateScrollToValue(1000, {offsetHeight: 10}),
          905);
    });
  });

  suite('AbortStops', () => {
    test('next() does not skip AbortStops', () => {
      element.stops = [
        document.createElement('li'),
        new AbortStop(),
        document.createElement('li'),
      ];
      element.setCursorAtIndex(0);

      const result = element.next();

      assert.equal(result, CursorMoveResult.ABORTED);
      assert.equal(element.index, 0);
    });

    test('setCursorAtIndex() does not target AbortStops', () => {
      element.stops = [
        document.createElement('li'),
        new AbortStop(),
        document.createElement('li'),
      ];
      element.setCursorAtIndex(1);
      assert.equal(element.index, -1);
    });

    test('moveToStart() does not target AbortStop', () => {
      element.stops = [
        new AbortStop(),
        document.createElement('li'),
        document.createElement('li'),
      ];
      element.moveToStart();
      assert.equal(element.index, -1);
    });

    test('moveToEnd() does not target AbortStop', () => {
      element.stops = [
        document.createElement('li'),
        document.createElement('li'),
        new AbortStop(),
      ];
      element.moveToEnd();
      assert.equal(element.index, -1);
    });
  });
});
