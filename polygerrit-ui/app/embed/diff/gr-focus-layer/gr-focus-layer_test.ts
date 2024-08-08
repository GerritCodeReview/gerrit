/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';

import {assert} from '@open-wc/testing';
import {SinonStub} from 'sinon';

import {DiffRangesToFocus, Side, GrDiffLineType} from '../../../api/diff';

import {GrFocusLayer} from './gr-focus-layer';
import {GrDiffLine} from '../gr-diff/gr-diff-line';

const ONE_RANGE: DiffRangesToFocus = {
  left: [{start: 1, end: 2}],
  right: [{start: 9, end: 10}],
};

const RANGES: DiffRangesToFocus = {
  left: [
    {start: 1, end: 2},
    {start: 13, end: 14},
    {start: 25, end: 76},
  ],
  right: [
    {start: 1, end: 2},
    {start: 23, end: 24},
    {start: 55, end: 65},
  ],
};

suite('gr-focus-layer', () => {
  let layer: GrFocusLayer;
  const line = new GrDiffLine(GrDiffLineType.ADD);

  function createLineElement(lineNumber: number, side: Side) {
    const lineNumberEl = document.createElement('div');
    lineNumberEl.setAttribute('data-side', side);
    lineNumberEl.setAttribute('data-value', lineNumber.toString());
    lineNumberEl.className = side;
    return lineNumberEl;
  }

  function createTextElement() {
    const textElement = document.createElement('div');
    textElement.innerText = 'A line of code';
    return textElement;
  }

  suite('setRanges and notify', () => {
    let listener: SinonStub;

    setup(() => {
      layer = new GrFocusLayer();
      listener = sinon.stub();
      layer.addListener(listener);
    });

    test('empty ranges do not notify', () => {
      layer.annotated = true;
      layer.setRanges();
      assert.isFalse(listener.called);
    });

    test('do not notify while annotated is false', () => {
      layer.setRanges(RANGES);
      assert.isFalse(listener.called);
    });

    test('initial ranges', () => {
      layer.annotated = true;
      layer.setRanges(ONE_RANGE);
      assert.isTrue(listener.called);
      assert.equal(listener.callCount, 2);
      assert.equal(listener.getCall(0).args[0], 1);
      assert.equal(listener.getCall(0).args[1], 2);
      assert.equal(listener.getCall(1).args[0], 9);
      assert.equal(listener.getCall(1).args[1], 10);
    });

    test('old ranges and new range', () => {
      layer.annotated = true;
      layer.setRanges(ONE_RANGE);
      listener.reset();
      layer.annotate(
        createTextElement(),
        createLineElement(100, Side.RIGHT),
        line,
        Side.RIGHT
      );
      layer.annotate(
        createTextElement(),
        createLineElement(101, Side.RIGHT),
        line,
        Side.RIGHT
      );
      layer.setRanges(RANGES);
      assert.isTrue(listener.called);
      assert.equal(listener.callCount, 7);
      assert.equal(listener.getCall(3).args[0], 100);
      assert.equal(listener.getCall(3).args[1], 101);
      assert.equal(listener.getCall(3).args[2], Side.RIGHT);
    });
  });

  suite('annotate', () => {
    function hasOutOfFocusClass(lineNumber: number, side: Side) {
      const textEl = createTextElement();
      layer.annotate(textEl, createLineElement(lineNumber, side), line, side);
      return textEl.classList.contains('is-out-of-focus-range');
    }

    setup(() => {
      layer = new GrFocusLayer();
      layer.setRanges(RANGES);
    });

    test('annotated is true after annotate', () => {
      assert.isFalse(hasOutOfFocusClass(1, Side.LEFT));
      assert.isTrue(layer.annotated);
    });

    test('line 1-2 are focussed on both sides', () => {
      assert.isFalse(hasOutOfFocusClass(1, Side.LEFT));
      assert.isFalse(hasOutOfFocusClass(2, Side.RIGHT));
      assert.isFalse(hasOutOfFocusClass(1, Side.LEFT));
      assert.isFalse(hasOutOfFocusClass(2, Side.RIGHT));
    });

    test('line 3-12 are not focussed on left side', () => {
      for (let index = 3; index < 12; index++) {
        assert.isTrue(hasOutOfFocusClass(index, Side.LEFT));
      }
    });

    test('line 3-22 are not focussed on right side', () => {
      for (let index = 3; index < 22; index++) {
        assert.isTrue(hasOutOfFocusClass(index, Side.RIGHT));
      }
    });

    test('line 13-14 are focussed on left side', () => {
      assert.isFalse(hasOutOfFocusClass(13, Side.LEFT));
      assert.isFalse(hasOutOfFocusClass(14, Side.LEFT));
    });
  });
});
