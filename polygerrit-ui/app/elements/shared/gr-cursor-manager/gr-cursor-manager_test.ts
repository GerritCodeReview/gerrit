/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {fixture, html, assert} from '@open-wc/testing';
import {AbortStop, CursorMoveResult} from '../../../api/core';
import {GrCursorManager} from './gr-cursor-manager';

suite('gr-cursor-manager tests', () => {
  let cursor: GrCursorManager;
  let list: Element;

  setup(async () => {
    list = await fixture(html` <ul>
      <li>A</li>
      <li>B</li>
      <li>C</li>
      <li>D</li>
    </ul>`);
    cursor = new GrCursorManager();
    cursor.cursorTargetClass = 'targeted';
  });

  test('core cursor functionality', () => {
    // The element is initialized into the proper state.
    assert.isArray(cursor.stops);
    assert.equal(cursor.stops.length, 0);
    assert.equal(cursor.index, -1);
    assert.isNotOk(cursor.target);

    // Initialize the cursor with its stops.
    cursor.stops = [...list.querySelectorAll('li')];

    // It should have the stops but it should not be targeting any of them.
    assert.isNotNull(cursor.stops);
    assert.equal(cursor.stops.length, 4);
    assert.equal(cursor.index, -1);
    assert.isNotOk(cursor.target);

    // Select the third stop.
    cursor.setCursor(list.children[2] as HTMLElement);

    // It should update its internal state and update the element's class.
    assert.equal(cursor.index, 2);
    assert.equal(cursor.target, list.children[2] as HTMLElement);
    assert.isTrue(list.children[2].classList.contains('targeted'));
    assert.isFalse(cursor.isAtStart());
    assert.isFalse(cursor.isAtEnd());

    // Progress the cursor.
    let result = cursor.next();

    // Confirm that the next stop is selected and that the previous stop is
    // unselected.
    assert.equal(result, CursorMoveResult.MOVED);
    assert.equal(cursor.index, 3);
    assert.equal(cursor.target, list.children[3] as HTMLElement);
    assert.isTrue(cursor.isAtEnd());
    assert.isFalse(list.children[2].classList.contains('targeted'));
    assert.isTrue(list.children[3].classList.contains('targeted'));

    // Progress the cursor.
    result = cursor.next();

    // We should still be at the end.
    assert.equal(result, CursorMoveResult.CLIPPED);
    assert.equal(cursor.index, 3);
    assert.equal(cursor.target, list.children[3] as HTMLElement);
    assert.isTrue(cursor.isAtEnd());

    // Wind the cursor all the way back to the first stop.
    result = cursor.previous();
    assert.equal(result, CursorMoveResult.MOVED);
    result = cursor.previous();
    assert.equal(result, CursorMoveResult.MOVED);
    result = cursor.previous();
    assert.equal(result, CursorMoveResult.MOVED);

    // The element state should reflect the start of the list.
    assert.equal(cursor.index, 0);
    assert.equal(cursor.target, list.children[0] as HTMLElement);
    assert.isTrue(cursor.isAtStart());
    assert.isTrue(list.children[0].classList.contains('targeted'));

    const newLi = document.createElement('li');
    newLi.textContent = 'Z';
    list.insertBefore(newLi, list.children[0]);
    cursor.stops = [...list.querySelectorAll('li')];

    assert.equal(cursor.index, 1);

    // De-select all targets.
    cursor.unsetCursor();

    // There should now be no cursor target.
    assert.isFalse(list.children[1].classList.contains('targeted'));
    assert.isNotOk(cursor.target);
    assert.equal(cursor.index, -1);
  });

  test('isAtStart() returns true when there are no stops', () => {
    cursor.stops = [];
    assert.isTrue(cursor.isAtStart());
  });

  test('isAtEnd() returns true when there are no stops', () => {
    cursor.stops = [];
    assert.isTrue(cursor.isAtEnd());
  });

  test('next() goes to first element when no cursor is set', () => {
    cursor.stops = [...list.querySelectorAll('li')];
    const result = cursor.next();

    assert.equal(result, CursorMoveResult.MOVED);
    assert.equal(cursor.index, 0);
    assert.equal(cursor.target, list.children[0] as HTMLElement);
    assert.isTrue(list.children[0].classList.contains('targeted'));
    assert.isTrue(cursor.isAtStart());
    assert.isFalse(cursor.isAtEnd());
  });

  test('next() resets the cursor when there are no stops', () => {
    cursor.stops = [];
    const result = cursor.next();

    assert.equal(result, CursorMoveResult.NO_STOPS);
    assert.equal(cursor.index, -1);
    assert.isNotOk(cursor.target);
    assert.isFalse(list.children[1].classList.contains('targeted'));
  });

  test('previous() goes to last element when no cursor is set', () => {
    cursor.stops = [...list.querySelectorAll('li')];
    const result = cursor.previous();

    assert.equal(result, CursorMoveResult.MOVED);
    const lastIndex = list.children.length - 1;
    assert.equal(cursor.index, lastIndex);
    assert.equal(cursor.target, list.children[lastIndex] as HTMLElement);
    assert.isTrue(list.children[lastIndex].classList.contains('targeted'));
    assert.isFalse(cursor.isAtStart());
    assert.isTrue(cursor.isAtEnd());
  });

  test('previous() resets the cursor when there are no stops', () => {
    cursor.stops = [];
    const result = cursor.previous();

    assert.equal(result, CursorMoveResult.NO_STOPS);
    assert.equal(cursor.index, -1);
    assert.isNotOk(cursor.target);
    assert.isFalse(list.children[1].classList.contains('targeted'));
  });

  test('_moveCursor', () => {
    // Initialize the cursor with its stops.
    cursor.stops = [...list.querySelectorAll('li')];
    // Select the first stop.
    cursor.setCursor(list.children[0] as HTMLElement);
    const getTargetHeight = sinon.stub();

    // Move the cursor without an optional get target height function.
    cursor._moveCursor(1);
    assert.isFalse(getTargetHeight.called);

    // Move the cursor with an optional get target height function.
    cursor._moveCursor(1, {getTargetHeight});
    assert.isTrue(getTargetHeight.called);
  });

  test('_moveCursor from for invalid index does not check height', () => {
    cursor.stops = [];
    const getTargetHeight = sinon.stub();
    cursor._moveCursor(1, {filter: () => false, getTargetHeight});
    assert.isFalse(getTargetHeight.called);
  });

  test('setCursorAtIndex with noScroll', () => {
    sinon.stub(cursor, '_targetIsVisible').callsFake(() => false);
    const scrollStub = sinon.stub(window, 'scrollTo');
    cursor.stops = [...list.querySelectorAll('li')];
    cursor.scrollMode = 'keep-visible';

    cursor.setCursorAtIndex(1, true);
    assert.isFalse(scrollStub.called);

    cursor.setCursorAtIndex(2);
    assert.isTrue(scrollStub.called);
  });

  test('move with filter', () => {
    const isLetterB = function (row: HTMLElement) {
      return row.textContent === 'B';
    };
    cursor.stops = [...list.querySelectorAll('li')];
    // Start cursor at the first stop.
    cursor.setCursor(list.children[0] as HTMLElement);

    // Move forward to meet the next condition.
    cursor.next({filter: isLetterB});
    assert.equal(cursor.index, 1);

    // Nothing else meets the condition, should be at last stop.
    cursor.next({filter: isLetterB});
    assert.equal(cursor.index, 3);

    // Should stay at last stop if try to proceed.
    cursor.next({filter: isLetterB});
    assert.equal(cursor.index, 3);

    // Go back to the previous condition met. Should be back at.
    // stop 1.
    cursor.previous({filter: isLetterB});
    assert.equal(cursor.index, 1);

    // Go back. No more meet the condition. Should be at stop 0.
    cursor.previous({filter: isLetterB});
    assert.equal(cursor.index, 0);
  });

  test('focusOnMove prop', () => {
    const listEls = [...list.querySelectorAll('li')];
    const listFocusStubs = listEls.map(listEl => sinon.spy(listEl, 'focus'));
    cursor.stops = listEls;
    cursor.setCursor(list.children[0] as HTMLElement);

    cursor.focusOnMove = false;
    cursor.next();
    assert.equal(listEls[1], cursor.target);
    assert.isFalse(listFocusStubs[1].called);

    cursor.focusOnMove = true;
    cursor.next();
    assert.equal(listEls[2], cursor.target);
    assert.isTrue(listFocusStubs[2].called);
  });

  suite('circular options', () => {
    const options = {circular: true};
    setup(() => {
      cursor.stops = [...list.querySelectorAll('li')];
    });

    test('previous() on first element goes to last element', () => {
      cursor.setCursor(list.children[0] as HTMLElement);
      cursor.previous(options);
      assert.equal(cursor.index, list.children.length - 1);
    });

    test('next() on last element goes to first element', () => {
      cursor.setCursor(list.children[list.children.length - 1] as HTMLElement);
      cursor.next(options);
      assert.equal(cursor.index, 0);
    });
  });

  suite('_scrollToTarget', () => {
    let scrollStub: sinon.SinonStub;
    setup(() => {
      cursor.stops = [...list.querySelectorAll('li')];
      cursor.scrollMode = 'keep-visible';

      // There is a target which has a targetNext
      cursor.setCursor(list.children[0] as HTMLElement);
      cursor._moveCursor(1);
      scrollStub = sinon.stub(window, 'scrollTo');
      window.innerHeight = 60;
    });

    test('Called when top and bottom not visible', () => {
      sinon.stub(cursor, '_targetIsVisible').returns(false);
      cursor._scrollToTarget();
      assert.isTrue(scrollStub.called);
    });

    test('Not called when top and bottom visible', () => {
      sinon.stub(cursor, '_targetIsVisible').returns(true);
      cursor._scrollToTarget();
      assert.isFalse(scrollStub.called);
    });

    test('Called when top is visible, bottom is not, scroll is lower', () => {
      const visibleStub = sinon
        .stub(cursor, '_targetIsVisible')
        .callsFake(() => visibleStub.callCount === 2);
      window.scrollX = 123;
      window.scrollY = 15;
      window.innerHeight = 1000;
      window.pageYOffset = 0;
      sinon.stub(cursor, '_calculateScrollToValue').returns(20);
      cursor._scrollToTarget();
      assert.isTrue(scrollStub.called);
      assert.isTrue(scrollStub.calledWithExactly(123, 20));
      assert.equal(visibleStub.callCount, 2);
    });

    test('Called when top is visible, bottom not, scroll is higher', () => {
      const visibleStub = sinon
        .stub(cursor, '_targetIsVisible')
        .callsFake(() => visibleStub.callCount === 2);
      window.scrollX = 123;
      window.scrollY = 25;
      window.innerHeight = 1000;
      window.pageYOffset = 0;
      sinon.stub(cursor, '_calculateScrollToValue').returns(20);
      cursor._scrollToTarget();
      assert.isFalse(scrollStub.called);
      assert.equal(visibleStub.callCount, 2);
    });

    test('_calculateScrollToValue', () => {
      window.scrollX = 123;
      window.scrollY = 25;
      window.innerHeight = 300;
      window.pageYOffset = 0;
      const fakeElement = {offsetHeight: 10} as HTMLElement;
      assert.equal(cursor._calculateScrollToValue(1000, fakeElement), 905);
    });
  });

  suite('AbortStops', () => {
    test('next() does not skip AbortStops', () => {
      cursor.stops = [
        document.createElement('li'),
        new AbortStop(),
        document.createElement('li'),
      ];
      cursor.setCursorAtIndex(0);

      const result = cursor.next();

      assert.equal(result, CursorMoveResult.ABORTED);
      assert.equal(cursor.index, 0);
    });

    test('setCursorAtIndex() does not target AbortStops', () => {
      cursor.stops = [
        document.createElement('li'),
        new AbortStop(),
        document.createElement('li'),
      ];
      cursor.setCursorAtIndex(1);
      assert.equal(cursor.index, -1);
    });

    test('moveToStart() does not target AbortStop', () => {
      cursor.stops = [
        new AbortStop(),
        document.createElement('li'),
        document.createElement('li'),
      ];
      cursor.moveToStart();
      assert.equal(cursor.index, -1);
    });

    test('moveToEnd() does not target AbortStop', () => {
      cursor.stops = [
        document.createElement('li'),
        document.createElement('li'),
        new AbortStop(),
      ];
      cursor.moveToEnd();
      assert.equal(cursor.index, -1);
    });
  });
});
