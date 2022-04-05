/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {CoverageRange, CoverageType, Side} from '../../../api/diff';
import '../../../test/common-test-setup-karma';
import '../gr-diff/gr-diff-line';
import './gr-coverage-layer';
import {GrCoverageLayer} from './gr-coverage-layer';

const basicFixture = fixtureFromElement('gr-coverage-layer');

suite('gr-coverage-layer', () => {
  let element: GrCoverageLayer;

  setup(() => {
    const initialCoverageRanges: CoverageRange[] = [
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

    element = basicFixture.instantiate();
    element.coverageRanges = initialCoverageRanges;
    element.side = Side.RIGHT;
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
      element.annotate(content, line);
      let contains = line.classList.contains(className);
      if (negated) contains = !contains;
      assert.isTrue(contains);
    }

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
