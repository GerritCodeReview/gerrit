/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import '../../../test/common-test-setup';
import {Side} from '../../../constants/constants';
import {
  FULL_CONTEXT,
  FullContext,
} from '../../../embed/diff/gr-diff/gr-diff-utils';
import {
  createComment,
  createCommentThread,
  createDiff,
} from '../../../test/test-data-generators';
import {DiffInfo} from '../../../types/diff';
import {DiffBlockStatus} from './markdown-diff-util';
import {
  extractSkipRanges,
  getPartialExpandIndices,
  groupRichDiffRows,
  lineOfInterestHasChanged,
  MIN_COLLAPSED_LINES,
  RichDiffCollapsibleRow,
} from './rich-diff-context-util';

function makeRow(
  status: DiffBlockStatus,
  startLine: number,
  endLine = startLine,
  opts?: {
    hasLeftThread?: boolean;
    hasRightThread?: boolean;
    leftOnly?: boolean;
    rightOnly?: boolean;
    leftStartLine?: number;
    leftEndLine?: number;
    rightStartLine?: number;
    rightEndLine?: number;
    rangeStartLine?: number;
    rangeEndLine?: number;
  }
): RichDiffCollapsibleRow {
  const comment = {
    ...createComment(),
    line: opts?.rangeEndLine ?? startLine,
    range:
      opts?.rangeStartLine !== undefined
        ? {
            start_line: opts.rangeStartLine,
            start_character: 0,
            end_line: opts.rangeEndLine ?? opts.rangeStartLine,
            end_character: 5,
          }
        : undefined,
  };
  const thread = createCommentThread([comment]);
  return {
    status,
    leftStartLine: opts?.rightOnly
      ? undefined
      : opts?.leftStartLine ?? startLine,
    leftEndLine: opts?.rightOnly ? undefined : opts?.leftEndLine ?? endLine,
    rightStartLine: opts?.leftOnly
      ? undefined
      : opts?.rightStartLine ?? startLine,
    rightEndLine: opts?.leftOnly ? undefined : opts?.rightEndLine ?? endLine,
    leftThreads: opts?.hasLeftThread ? [thread] : [],
    rightThreads: opts?.hasRightThread ? [thread] : [],
  };
}

suite('rich-diff-context-util tests', () => {
  test('returns empty array for empty input', () => {
    assert.deepEqual(groupRichDiffRows([]), []);
    assert.deepEqual(
      getPartialExpandIndices({startIndex: 0, rows: []}, 'from-start'),
      []
    );
  });

  test('lineOfInterestHasChanged compares by side and lineNum value', () => {
    assert.isFalse(lineOfInterestHasChanged(undefined, undefined));
    assert.isFalse(
      lineOfInterestHasChanged(
        {side: Side.RIGHT, lineNum: 10},
        {side: Side.RIGHT, lineNum: 10}
      )
    );
    assert.isTrue(
      lineOfInterestHasChanged(
        {side: Side.LEFT, lineNum: 10},
        {side: Side.RIGHT, lineNum: 10}
      )
    );
    assert.isTrue(
      lineOfInterestHasChanged(
        {side: Side.RIGHT, lineNum: 11},
        {side: Side.RIGHT, lineNum: 10}
      )
    );
  });

  test('collapses unchanged rows outside context window at top and bottom', () => {
    // 15 blocks of 2 lines each separated by blank lines -> lines 1..44
    // Change is at index 7 (lines 22..23)
    const rows: RichDiffCollapsibleRow[] = [];
    for (let i = 0; i < 15; i++) {
      const start = i * 3 + 1;
      const end = start + 1;
      rows.push(makeRow(i === 7 ? 'modified' : 'unchanged', start, end));
    }

    const items = groupRichDiffRows(rows, {context: 3});

    assert.equal(items.length, 5);
    assert.equal(items[0].type, 'collapsed');
    if (items[0].type === 'collapsed') {
      assert.equal(items[0].section.gapId, 'gap-0-5');
      assert.equal(items[0].section.startIndex, 0);
      assert.equal(items[0].section.endIndex, 5);
      assert.equal(items[0].section.startLine, 1);
      assert.equal(items[0].section.endLine, 17);
      assert.equal(items[0].section.lineCount, 17);
      assert.isFalse(items[0].section.hasVisibleAbove);
      assert.isTrue(items[0].section.hasVisibleBelow);
      assert.isFalse(items[0].section.canExpandFromStart);
      assert.isTrue(items[0].section.canExpandFromEnd);
      assert.isDefined(items[0].section.partialEndRange);
    }

    assert.equal(items[1].type, 'row');
    assert.equal(items[2].type, 'row');
    assert.equal(items[3].type, 'row');

    assert.equal(items[4].type, 'collapsed');
    if (items[4].type === 'collapsed') {
      assert.equal(items[4].section.gapId, 'gap-9-14');
      assert.equal(items[4].section.startIndex, 9);
      assert.equal(items[4].section.endIndex, 14);
      assert.isTrue(items[4].section.hasVisibleAbove);
      assert.isFalse(items[4].section.hasVisibleBelow);
      assert.isTrue(items[4].section.canExpandFromStart);
      assert.isFalse(items[4].section.canExpandFromEnd);
      assert.isDefined(items[4].section.partialStartRange);
    }
  });

  test('collapses middle section between two distant hunks with both partial expand controls', () => {
    const rows: RichDiffCollapsibleRow[] = [];
    for (let i = 0; i < 15; i++) {
      const start = i * 3 + 1;
      const status: DiffBlockStatus =
        i === 0 || i === 14 ? 'modified' : 'unchanged';
      rows.push(makeRow(status, start, start + 1));
    }

    const items = groupRichDiffRows(rows, {context: 3});
    const collapsed = items.filter(item => item.type === 'collapsed');
    assert.equal(collapsed.length, 1);
    if (collapsed[0].type === 'collapsed') {
      assert.isTrue(collapsed[0].section.hasVisibleAbove);
      assert.isTrue(collapsed[0].section.hasVisibleBelow);
      assert.isTrue(collapsed[0].section.canExpandFromStart);
      assert.isTrue(collapsed[0].section.canExpandFromEnd);
    }
  });

  test('shows all rows when context is FULL_CONTEXT or showFullContext is YES', () => {
    const rows: RichDiffCollapsibleRow[] = [];
    for (let i = 0; i < 20; i++) {
      rows.push(
        makeRow(i === 10 ? 'modified' : 'unchanged', i * 3 + 1, i * 3 + 2)
      );
    }

    const fullPrefItems = groupRichDiffRows(rows, {context: FULL_CONTEXT});
    assert.equal(fullPrefItems.length, 20);
    assert.isTrue(fullPrefItems.every(item => item.type === 'row'));

    const overrideYesItems = groupRichDiffRows(rows, {
      context: 3,
      showFullContext: FullContext.YES,
    });
    assert.equal(overrideYesItems.length, 20);
    assert.isTrue(overrideYesItems.every(item => item.type === 'row'));

    // FullContext.NO overrides FULL_CONTEXT pref back to default context
    const overrideNoItems = groupRichDiffRows(rows, {
      context: FULL_CONTEXT,
      showFullContext: FullContext.NO,
    });
    assert.isTrue(overrideNoItems.some(item => item.type === 'collapsed'));
  });

  test('respects MIN_COLLAPSED_LINES boundary (3 stays visible, 4 collapses)', () => {
    assert.equal(MIN_COLLAPSED_LINES, 4);
    const rows3: RichDiffCollapsibleRow[] = [
      makeRow('modified', 1, 1),
      makeRow('unchanged', 5, 7), // 3 lines
      makeRow('modified', 11, 11),
    ];
    const items3 = groupRichDiffRows(rows3, {context: 0});
    assert.equal(items3.length, 3);
    assert.isTrue(items3.every(item => item.type === 'row'));

    const rows4: RichDiffCollapsibleRow[] = [
      makeRow('modified', 1, 1),
      makeRow('unchanged', 5, 8), // 4 lines -> collapses
      makeRow('modified', 12, 12),
    ];
    const items4 = groupRichDiffRows(rows4, {context: 0});
    assert.equal(items4.length, 3);
    assert.equal(items4[1].type, 'collapsed');
    if (items4[1].type === 'collapsed') {
      assert.equal(items4[1].section.lineCount, 4);
      // Single row collapsed section cannot partially expand
      assert.isFalse(items4[1].section.canExpandFromStart);
      assert.isFalse(items4[1].section.canExpandFromEnd);
    }
  });

  test('omits partial expand buttons when single row or remainder < MIN_COLLAPSED_LINES', () => {
    // Single 20-line unchanged block -> collapses, but no +10 button
    const singleRowGap: RichDiffCollapsibleRow[] = [
      makeRow('modified', 1, 1),
      makeRow('unchanged', 10, 30),
      makeRow('modified', 40, 40),
    ];
    const singleItems = groupRichDiffRows(singleRowGap, {context: 0});
    assert.equal(singleItems[1].type, 'collapsed');
    if (singleItems[1].type === 'collapsed') {
      assert.isFalse(singleItems[1].section.canExpandFromStart);
      assert.isFalse(singleItems[1].section.canExpandFromEnd);
    }

    // Two rows: first is 10 lines (10..19), second is 3 lines (21..23) -> expanding from start leaves 3 < 4
    const smallTailGap: RichDiffCollapsibleRow[] = [
      makeRow('modified', 1, 1),
      makeRow('unchanged', 10, 19),
      makeRow('unchanged', 21, 23),
      makeRow('modified', 40, 40),
    ];
    const tailItems = groupRichDiffRows(smallTailGap, {context: 0});
    assert.equal(tailItems[1].type, 'collapsed');
    if (tailItems[1].type === 'collapsed') {
      assert.isFalse(tailItems[1].section.canExpandFromStart);
      assert.isFalse(tailItems[1].section.canExpandFromEnd);
    }
  });

  test('keeps unchanged rows with comment threads and multi-line comment ranges visible', () => {
    const rows: RichDiffCollapsibleRow[] = [];
    for (let i = 0; i < 20; i++) {
      const start = i * 4 + 1;
      rows.push(
        makeRow(i === 19 ? 'modified' : 'unchanged', start, start + 1, {
          hasRightThread: i === 5,
          // Multi-line comment range spanning rows 1..5 (lines 5..22)
          rangeStartLine: i === 5 ? 5 : undefined,
          rangeEndLine: i === 5 ? 22 : undefined,
        })
      );
    }

    const items = groupRichDiffRows(rows, {context: 2});
    const visibleRowIndices = items
      .filter(item => item.type === 'row')
      .map(item => (item.type === 'row' ? item.index : -1));
    // Entire range (rows 1..5) and the modified row (19) remain visible
    for (let idx = 1; idx <= 5; idx++) {
      assert.include(visibleRowIndices, idx);
    }
    assert.include(visibleRowIndices, 19);
    // Distant middle rows between 6 and 18 are collapsed
    assert.isTrue(items.some(item => item.type === 'collapsed'));
  });

  test('handles lineOfInterest on left/right, blank lines, and invalid/special values', () => {
    const rows: RichDiffCollapsibleRow[] = [];
    for (let i = 0; i < 20; i++) {
      const leftStart = i * 4 + 1;
      const rightStart = leftStart + 100; // Diverged left vs right line numbers
      rows.push(
        makeRow(
          i === 19 ? 'modified' : 'unchanged',
          rightStart,
          rightStart + 1,
          {
            leftStartLine: leftStart,
            leftEndLine: leftStart + 1,
            rightStartLine: rightStart,
            rightEndLine: rightStart + 1,
          }
        )
      );
    }

    // Left-side lineOfInterest on a blank line (line 11 is between row 2 [9..10] and row 3 [13..14]) with context: 0
    const leftBlankItems = groupRichDiffRows(rows, {
      context: 0,
      lineOfInterest: {side: Side.LEFT, lineNum: 11},
    });
    const visibleLeftIndices = leftBlankItems
      .filter(item => item.type === 'row')
      .map(item => (item.type === 'row' ? item.index : -1));
    assert.include(visibleLeftIndices, 2);
    assert.include(visibleLeftIndices, 19);

    // Non-numeric / non-finite / non-positive lineOfInterest is safely ignored on small all-unchanged file
    const smallUnchanged = [
      makeRow('unchanged', 1, 2),
      makeRow('unchanged', 4, 5),
    ];
    for (const badLine of ['FILE', 'LOST', NaN, Infinity, -5, 0] as const) {
      const items = groupRichDiffRows(smallUnchanged, {
        context: 10,
        lineOfInterest: {
          side: Side.RIGHT,
          lineNum: badLine,
        },
      });
      assert.equal(items.length, 2);
      assert.isTrue(items.every(item => item.type === 'row'));
    }
  });

  test('preserves all rows for newly added or deleted files and anchors deleted hunks via left lines', () => {
    const addedRows = Array.from({length: 10}, (_, i) =>
      makeRow('added', i * 3 + 1, i * 3 + 2, {rightOnly: true})
    );
    assert.isTrue(
      groupRichDiffRows(addedRows, {context: 3}).every(
        item => item.type === 'row'
      )
    );

    const deletedRows = Array.from({length: 10}, (_, i) =>
      makeRow('deleted', i * 3 + 1, i * 3 + 2, {leftOnly: true})
    );
    assert.isTrue(
      groupRichDiffRows(deletedRows, {context: 3}).every(
        item => item.type === 'row'
      )
    );

    // Deleted hunk in middle with diverged left/right lines anchors nearby left context
    const mixedRows: RichDiffCollapsibleRow[] = [];
    for (let i = 0; i < 15; i++) {
      const leftStart = i * 3 + 1;
      const rightStart = leftStart + 50;
      if (i === 7) {
        mixedRows.push(
          makeRow('deleted', leftStart, leftStart + 1, {leftOnly: true})
        );
      } else {
        mixedRows.push(
          makeRow('unchanged', rightStart, rightStart + 1, {
            leftStartLine: leftStart,
            leftEndLine: leftStart + 1,
            rightStartLine: rightStart,
            rightEndLine: rightStart + 1,
            hasLeftThread: i === 1,
          })
        );
      }
    }
    const items = groupRichDiffRows(mixedRows, {context: 3});
    const visibleIndices = items
      .filter(item => item.type === 'row')
      .map(item => (item.type === 'row' ? item.index : -1));
    assert.include(visibleIndices, 1); // left thread
    assert.include(visibleIndices, 6); // context before deleted hunk
    assert.include(visibleIndices, 7); // deleted hunk
    assert.include(visibleIndices, 8); // context after deleted hunk
  });

  test('handles all-unchanged files: keeps small files visible and allows incremental expand on large ones', () => {
    const smallUnchanged = [
      makeRow('unchanged', 1, 2),
      makeRow('unchanged', 4, 5),
    ];
    const smallItems = groupRichDiffRows(smallUnchanged, {context: 10});
    assert.equal(smallItems.length, 2);
    assert.isTrue(smallItems.every(item => item.type === 'row'));

    // context: 0 collapses even a 5-line all-unchanged file (>= MIN_COLLAPSED_LINES)
    const zeroCtxUnchanged = groupRichDiffRows(smallUnchanged, {context: 0});
    assert.equal(zeroCtxUnchanged.length, 1);
    assert.equal(zeroCtxUnchanged[0].type, 'collapsed');

    const largeUnchanged = Array.from({length: 10}, (_, i) =>
      makeRow('unchanged', i * 3 + 1, i * 3 + 2)
    );
    const largeItems = groupRichDiffRows(largeUnchanged, {context: 10});
    assert.equal(largeItems.length, 1);
    assert.equal(largeItems[0].type, 'collapsed');
    if (largeItems[0].type === 'collapsed') {
      assert.equal(largeItems[0].section.lineCount, 29);
      assert.isTrue(largeItems[0].section.canExpandFromStart);
      assert.isFalse(largeItems[0].section.canExpandFromEnd);
    }
  });

  test('getPartialExpandIndices supports both from-start and from-end and preserves stable gapId', () => {
    const rows: RichDiffCollapsibleRow[] = [];
    for (let i = 0; i < 20; i++) {
      const start = i * 3 + 1;
      const status: DiffBlockStatus =
        i === 0 || i === 19 ? 'modified' : 'unchanged';
      rows.push(makeRow(status, start, start + 1));
    }

    const initial = groupRichDiffRows(rows, {context: 3});
    const collapsed = initial.find(item => item.type === 'collapsed');
    assert.isDefined(collapsed);
    if (!collapsed || collapsed.type !== 'collapsed') return;

    const initialGapId = collapsed.section.gapId;
    const partialStart = getPartialExpandIndices(
      collapsed.section,
      'from-start',
      {linesToExpand: 10}
    );
    const partialEnd = getPartialExpandIndices(collapsed.section, 'from-end', {
      linesToExpand: 10,
    });
    assert.isAbove(partialStart.length, 0);
    assert.isAbove(partialEnd.length, 0);
    // Ascending order from startIndex
    assert.equal(partialStart[0], collapsed.section.startIndex);
    assert.equal(partialEnd[0], collapsed.section.endIndex);

    // Passing skipRanges via options subtracts skipped lines from the budget
    const skipRows = [
      makeRow('unchanged', 1, 2),
      makeRow('unchanged', 103, 104),
      makeRow('unchanged', 106, 107),
    ];
    const withSkips = getPartialExpandIndices(
      {startIndex: 0, rows: skipRows},
      'from-start',
      {
        linesToExpand: 5,
        skipRanges: {
          left: [{start: 3, end: 102}],
          right: [{start: 3, end: 102}],
        },
      }
    );
    // Span 1..104 minus 100 skipped lines = 4 (< 5), so it advances to include row 2
    assert.deepEqual(withSkips, [0, 1, 2]);

    const afterStart = groupRichDiffRows(rows, {
      context: 3,
      expandedRowIndices: new Set(partialStart),
    });
    const collapsedAfterStart = afterStart.find(
      item => item.type === 'collapsed'
    );
    assert.isDefined(collapsedAfterStart);
    if (collapsedAfterStart?.type === 'collapsed') {
      assert.equal(collapsedAfterStart.section.gapId, initialGapId);
      assert.equal(
        collapsedAfterStart.section.startIndex,
        collapsed.section.startIndex + partialStart.length
      );
    }

    const afterBoth = groupRichDiffRows(rows, {
      context: 3,
      expandedRowIndices: new Set([...partialStart, ...partialEnd]),
    });
    const collapsedAfterBoth = afterBoth.find(
      item => item.type === 'collapsed'
    );
    assert.isDefined(collapsedAfterBoth);
    if (collapsedAfterBoth?.type === 'collapsed') {
      assert.equal(collapsedAfterBoth.section.gapId, initialGapId);
      assert.equal(
        collapsedAfterBoth.section.endIndex,
        collapsed.section.endIndex - partialEnd.length
      );
    }
  });

  test('extractSkipRanges excludes server-skipped lines from collapsed line counts and flags partial diff', () => {
    const diff: DiffInfo = {
      ...createDiff(),
      content: [
        {ab: ['line 1', 'line 2', 'line 3']},
        {skip: 500},
        {ab: ['line 504', 'line 505', 'line 506']},
        {a: ['old 507'], b: ['new 507']},
      ],
    };
    const skipRanges = extractSkipRanges(diff);
    assert.deepEqual(skipRanges.left, [{start: 4, end: 503}]);
    assert.deepEqual(skipRanges.right, [{start: 4, end: 503}]);

    const rows: RichDiffCollapsibleRow[] = [
      makeRow('unchanged', 1, 3),
      makeRow('unchanged', 504, 506),
      makeRow('modified', 520, 520),
    ];
    const items = groupRichDiffRows(rows, {context: 0, skipRanges});
    assert.equal(items[0].type, 'collapsed');
    if (items[0].type === 'collapsed') {
      // Raw span is 506 lines, minus 500 skipped lines = 6 actual expandable lines!
      assert.equal(items[0].section.lineCount, 6);
      assert.isTrue(items[0].section.hasSkippedLines);
    }
  });

  test('handles missing line numbers without mixing left/right coordinates', () => {
    const rows: RichDiffCollapsibleRow[] = [
      {
        status: 'unchanged',
        leftStartLine: 1,
        leftEndLine: 2,
        leftThreads: [],
        rightThreads: [],
      },
      {
        status: 'unchanged',
        rightStartLine: 100,
        rightEndLine: 101,
        leftThreads: [],
        rightThreads: [],
      },
      {
        status: 'unchanged',
        leftThreads: [],
        rightThreads: [],
      },
      {
        status: 'unchanged',
        leftThreads: [],
        rightThreads: [],
      },
      makeRow('modified', 200, 200),
    ];
    const items = groupRichDiffRows(rows, {context: 0});
    assert.equal(items[0].type, 'collapsed');
    if (items[0].type === 'collapsed') {
      // Falls back to summing row counts (2 + 2 + 1 + 1 = 6), never 101 - 1 + 1 = 101
      assert.equal(items[0].section.lineCount, 6);
    }
  });

  test('scales cleanly on large files with many anchors (O(N log A))', () => {
    const rows: RichDiffCollapsibleRow[] = [];
    for (let i = 0; i < 2000; i++) {
      const start = i * 20 + 1;
      rows.push(
        makeRow(i % 2 === 0 ? 'modified' : 'unchanged', start, start + 1)
      );
    }
    const items = groupRichDiffRows(rows, {context: 3});
    assert.equal(items.length, 2000);
  });
});
