/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {CoverageType, Side} from '../../../api/diff';
import {GrCoverageLayer, mergeRanges} from './gr-coverage-layer';
import {assert} from '@open-wc/testing';
import {SinonStub} from 'sinon';

const RANGES = [
  {
    type: CoverageType.COVERED,
    side: Side.RIGHT,
    code_range: {
      start_line: 1,
      end_line: 2,
    },
  },
  {
    type: CoverageType.NOT_COVERED,
    side: Side.RIGHT,
    code_range: {
      start_line: 3,
      end_line: 4,
    },
  },
  {
    type: CoverageType.PARTIALLY_COVERED,
    side: Side.RIGHT,
    code_range: {
      start_line: 5,
      end_line: 6,
    },
  },
  {
    type: CoverageType.NOT_INSTRUMENTED,
    side: Side.RIGHT,
    code_range: {
      start_line: 8,
      end_line: 9,
    },
  },
];

suite('gr-coverage-layer', () => {
  let layer: GrCoverageLayer;

  test('mergeRanges', () => {
    assert.deepEqual(mergeRanges([]), []);
    assert.deepEqual(mergeRanges([{start: 1, end: 2}]), [{start: 1, end: 2}]);
    assert.deepEqual(
      mergeRanges([
        {start: 1, end: 2},
        {start: 2, end: 3},
      ]),
      [{start: 1, end: 3}]
    );
    assert.deepEqual(
      mergeRanges([
        {start: 2, end: 3},
        {start: 1, end: 2},
      ]),
      [{start: 1, end: 3}]
    );
    assert.deepEqual(
      mergeRanges([
        {start: 1, end: 3},
        {start: 4, end: 5},
      ]),
      [
        {start: 1, end: 3},
        {start: 4, end: 5},
      ]
    );
  });

  suite('setRanges and notify', () => {
    let listener: SinonStub;

    setup(() => {
      layer = new GrCoverageLayer(Side.RIGHT);
      listener = sinon.stub();
      layer.addListener(listener);
    });

    test('empty ranges do not notify', () => {
      layer.annotated = true;
      layer.setRanges([]);
      assert.isFalse(listener.called);
    });

    test('do not notify while annotated is false', () => {
      layer.setRanges(RANGES);
      assert.isFalse(listener.called);
    });

    test('RANGES', () => {
      layer.annotated = true;
      layer.setRanges(RANGES);
      assert.isTrue(listener.called);
      assert.equal(listener.callCount, 2);
      assert.equal(listener.getCall(0).args[0], 1);
      assert.equal(listener.getCall(0).args[1], 6);
      assert.equal(listener.getCall(1).args[0], 8);
      assert.equal(listener.getCall(1).args[1], 9);
    });
  });

  suite('annotate', () => {
    function createLine(lineNumber: number) {
      const lineEl = document.createElement('div');
      lineEl.setAttribute('data-side', Side.RIGHT);
      lineEl.setAttribute('data-value', lineNumber.toString());
      lineEl.className = Side.RIGHT;
      return lineEl;
    }

    function checkLine(
      lineNumber: number,
      className: string,
      negated?: boolean
    ) {
      const content = document.createElement('div');
      const line = createLine(lineNumber);
      layer.annotate(content, line);
      let contains = line.classList.contains(className);
      if (negated) contains = !contains;
      assert.isTrue(contains);
    }

    setup(() => {
      layer = new GrCoverageLayer(Side.RIGHT);
      layer.setRanges(RANGES);
    });

    test('line 1-2 are covered', () => {
      checkLine(1, 'COVERED');
      checkLine(2, 'COVERED');
    });

    test('line 3-4 are not covered', () => {
      checkLine(3, 'NOT_COVERED');
      checkLine(4, 'NOT_COVERED');
    });

    test('line 5-6 are partially covered', () => {
      checkLine(5, 'PARTIALLY_COVERED');
      checkLine(6, 'PARTIALLY_COVERED');
    });

    test('line 7 is implicitly not instrumented', () => {
      checkLine(7, 'COVERED', true);
      checkLine(7, 'NOT_COVERED', true);
      checkLine(7, 'PARTIALLY_COVERED', true);
      checkLine(7, 'NOT_INSTRUMENTED', true);
    });

    test('line 8-9 are not instrumented', () => {
      checkLine(8, 'NOT_INSTRUMENTED');
      checkLine(9, 'NOT_INSTRUMENTED');
    });

    test('coverage correct, if annotate is called out of order', () => {
      checkLine(8, 'NOT_INSTRUMENTED');
      checkLine(1, 'COVERED');
      checkLine(5, 'PARTIALLY_COVERED');
      checkLine(3, 'NOT_COVERED');
      checkLine(6, 'PARTIALLY_COVERED');
      checkLine(4, 'NOT_COVERED');
      checkLine(9, 'NOT_INSTRUMENTED');
      checkLine(2, 'COVERED');
    });
  });
});
