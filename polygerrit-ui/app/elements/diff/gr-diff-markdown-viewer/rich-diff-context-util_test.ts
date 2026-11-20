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
} from '../../../test/test-data-generators';
import {DiffBlockStatus} from './markdown-diff-util';
import {
  getPartialExpandIndices,
  groupRichDiffRows,
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
  }
): RichDiffCollapsibleRow {
  const thread = createCommentThread([{...createComment(), line: startLine}]);
  return {
    status,
    leftStartLine: opts?.rightOnly ? undefined : startLine,
    leftEndLine: opts?.rightOnly ? undefined : endLine,
    rightStartLine: opts?.leftOnly ? undefined : startLine,
    rightEndLine: opts?.leftOnly ? undefined : endLine,
    leftThreads: opts?.hasLeftThread ? [thread] : [],
    rightThreads: opts?.hasRightThread ? [thread] : [],
  };
}

suite('rich-diff-context-util tests', () => {
  test('returns empty array for empty input', () => {
    assert.deepEqual(groupRichDiffRows([]), []);
  });

  test('collapses unchanged rows outside context window at top and bottom', () => {
    // 15 blocks of 2 lines each separated by blank lines -> lines 1..45
    // Change is at index 7 (lines 22..23)
    const rows: RichDiffCollapsibleRow[] = [];
    for (let i = 0; i < 15; i++) {
      const start = i * 3 + 1;
      const end = start + 1;
      rows.push(makeRow(i === 7 ? 'modified' : 'unchanged', start, end));
    }

    const items = groupRichDiffRows(rows, {context: 3});

    // Anchor is lines 22..23 with context 3 -> window [19, 26]:
    // index 6 is lines 19..20 (visible)
    // index 7 is lines 22..23 (modified, visible)
    // index 8 is lines 25..26 (visible)
    // indices 0..5 (lines 1..17) collapsed at top
    // indices 9..14 (lines 28..44) collapsed at bottom
    assert.equal(items.length, 5);
    assert.equal(items[0].type, 'collapsed');
    if (items[0].type === 'collapsed') {
      assert.equal(items[0].section.startIndex, 0);
      assert.equal(items[0].section.endIndex, 5);
      assert.equal(items[0].section.startLine, 1);
      assert.equal(items[0].section.endLine, 17);
      assert.equal(items[0].section.lineCount, 17);
      assert.isFalse(items[0].section.hasVisibleAbove);
      assert.isTrue(items[0].section.hasVisibleBelow);
      assert.isFalse(items[0].section.canExpandFromStart);
      assert.isTrue(items[0].section.canExpandFromEnd);
    }

    assert.equal(items[1].type, 'row');
    assert.equal(items[2].type, 'row');
    assert.equal(items[3].type, 'row');

    assert.equal(items[4].type, 'collapsed');
    if (items[4].type === 'collapsed') {
      assert.equal(items[4].section.startIndex, 9);
      assert.equal(items[4].section.endIndex, 14);
      assert.isTrue(items[4].section.hasVisibleAbove);
      assert.isFalse(items[4].section.hasVisibleBelow);
      assert.isTrue(items[4].section.canExpandFromStart);
      assert.isFalse(items[4].section.canExpandFromEnd);
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

  test('does not collapse stretches shorter than MIN_COLLAPSED_LINES', () => {
    // Anchor at line 1, gap at lines 5..7 (3 lines < MIN_COLLAPSED_LINES=4), anchor at line 11
    const rows: RichDiffCollapsibleRow[] = [
      makeRow('modified', 1, 1),
      makeRow('unchanged', 5, 7),
      makeRow('modified', 11, 11),
    ];
    assert.equal(MIN_COLLAPSED_LINES, 4);
    const items = groupRichDiffRows(rows, {context: 0});
    assert.equal(items.length, 3);
    assert.isTrue(items.every(item => item.type === 'row'));
  });

  test('keeps unchanged rows with comment threads visible alongside their context', () => {
    const rows: RichDiffCollapsibleRow[] = [];
    for (let i = 0; i < 20; i++) {
      const start = i * 4 + 1;
      rows.push(
        makeRow(i === 19 ? 'modified' : 'unchanged', start, start + 1, {
          hasRightThread: i === 2,
        })
      );
    }

    const items = groupRichDiffRows(rows, {context: 3});
    const visibleRowIndices = items
      .filter(item => item.type === 'row')
      .map(item => (item.type === 'row' ? item.index : -1));
    assert.include(visibleRowIndices, 2);
    assert.include(visibleRowIndices, 19);
    // Rows far between index 2 and index 19 are collapsed
    assert.isTrue(items.some(item => item.type === 'collapsed'));
  });

  test('keeps lineOfInterest visible even when far from any diff hunk', () => {
    const rows: RichDiffCollapsibleRow[] = [];
    for (let i = 0; i < 20; i++) {
      const start = i * 4 + 1;
      rows.push(makeRow(i === 19 ? 'modified' : 'unchanged', start, start + 1));
    }

    // Target line 10 (inside row index 2: lines 9..10)
    const items = groupRichDiffRows(rows, {
      context: 2,
      lineOfInterest: {side: Side.RIGHT, lineNum: 10},
    });
    const visibleRowIndices = items
      .filter(item => item.type === 'row')
      .map(item => (item.type === 'row' ? item.index : -1));
    assert.include(visibleRowIndices, 2);
    assert.include(visibleRowIndices, 19);
  });

  test('preserves all rows for newly added or deleted files', () => {
    const addedRows = Array.from({length: 10}, (_, i) =>
      makeRow('added', i * 3 + 1, i * 3 + 2, {rightOnly: true})
    );
    const items = groupRichDiffRows(addedRows, {context: 3});
    assert.equal(items.length, 10);
    assert.isTrue(items.every(item => item.type === 'row'));
  });

  test('handles all-unchanged files: keeps small files visible and collapses large ones', () => {
    const smallUnchanged = [
      makeRow('unchanged', 1, 2),
      makeRow('unchanged', 4, 5),
    ];
    const smallItems = groupRichDiffRows(smallUnchanged, {context: 10});
    assert.equal(smallItems.length, 2);
    assert.isTrue(smallItems.every(item => item.type === 'row'));

    const largeUnchanged = Array.from({length: 10}, (_, i) =>
      makeRow('unchanged', i * 3 + 1, i * 3 + 2)
    );
    const largeItems = groupRichDiffRows(largeUnchanged, {context: 10});
    assert.equal(largeItems.length, 1);
    assert.equal(largeItems[0].type, 'collapsed');
    if (largeItems[0].type === 'collapsed') {
      assert.equal(largeItems[0].section.lineCount, 29);
      assert.isFalse(largeItems[0].section.canExpandFromStart);
      assert.isFalse(largeItems[0].section.canExpandFromEnd);
    }
  });

  test('respects expandedRowIndices and getPartialExpandIndices', () => {
    const rows: RichDiffCollapsibleRow[] = [];
    for (let i = 0; i < 15; i++) {
      const start = i * 3 + 1;
      rows.push(makeRow(i === 14 ? 'modified' : 'unchanged', start, start + 1));
    }

    const initial = groupRichDiffRows(rows, {context: 3});
    assert.equal(initial[0].type, 'collapsed');
    if (initial[0].type !== 'collapsed') return;

    const partialEnd = getPartialExpandIndices(
      initial[0].section,
      'from-end',
      10
    );
    assert.isAbove(partialEnd.length, 0);
    assert.isBelow(partialEnd.length, initial[0].section.rows.length);

    const afterPartial = groupRichDiffRows(rows, {
      context: 3,
      expandedRowIndices: new Set(partialEnd),
    });
    assert.equal(afterPartial[0].type, 'collapsed');
    if (afterPartial[0].type === 'collapsed') {
      assert.isBelow(
        afterPartial[0].section.rows.length,
        initial[0].section.rows.length
      );
    }
  });
});
