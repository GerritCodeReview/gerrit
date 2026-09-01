/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import '../../../test/common-test-setup';
import {
  compareComments,
  computeContext,
  computeKeyLocations,
  computeLineLength,
  createRevertFixSuggestion,
  FULL_CONTEXT,
  FullContext,
  getDataFromCommentThreadEl,
  getRange,
  GrDiffCommentThread,
  GrDiffThreadElement,
} from './gr-diff-utils';
import {FILE, GrDiffLineType, LOST, Side} from '../../../api/diff';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {GrDiffGroup, GrDiffGroupType} from './gr-diff-group';
import {GrDiffLine} from './gr-diff-line';
import {PROVIDED_FIX_ID} from '../../../utils/comment-util';

suite('gr-diff-utils tests', () => {
  test('getRange returns undefined with start_line = 0', () => {
    const range = {
      start_line: 0,
      end_line: 12,
      start_character: 0,
      end_character: 0,
    };
    const threadEl = document.createElement('div');
    threadEl.className = 'comment-thread';
    threadEl.setAttribute('diff-side', 'right');
    threadEl.setAttribute('line-num', '1');
    threadEl.setAttribute('range', JSON.stringify(range));
    threadEl.setAttribute('slot', 'right-1');
    assert.isUndefined(getRange(threadEl));
  });

  suite('computeContext', () => {
    test('computeContext 1', () => {
      assert.equal(computeContext(1, FullContext.YES, 2), FULL_CONTEXT);
      assert.equal(computeContext(1, FullContext.NO, 2), 1);
      assert.equal(computeContext(1, FullContext.UNDECIDED, 2), 1);
    });

    test('computeContext 0', () => {
      assert.equal(computeContext(0, FullContext.YES, 2), FULL_CONTEXT);
      assert.equal(computeContext(0, FullContext.NO, 2), 0);
      assert.equal(computeContext(0, FullContext.UNDECIDED, 2), 0);
    });

    test('computeContext FULL_CONTEXT', () => {
      assert.equal(
        computeContext(FULL_CONTEXT, FullContext.YES, 2),
        FULL_CONTEXT
      );
      assert.equal(computeContext(FULL_CONTEXT, FullContext.NO, 2), 2);
      assert.equal(
        computeContext(FULL_CONTEXT, FullContext.UNDECIDED, 2),
        FULL_CONTEXT
      );
    });
  });

  suite('computeLineLength', () => {
    test('computeLineLength(1, ...)', () => {
      assert.equal(
        computeLineLength(
          {...createDefaultDiffPrefs(), line_length: 1},
          'a.txt'
        ),
        1
      );
      assert.equal(
        computeLineLength(
          {...createDefaultDiffPrefs(), line_length: 1},
          undefined
        ),
        1
      );
    });

    test('computeLineLength(1, "/COMMIT_MSG")', () => {
      assert.equal(
        computeLineLength(
          {...createDefaultDiffPrefs(), line_length: 1},
          '/COMMIT_MSG'
        ),
        72
      );
    });
  });

  suite('key locations', () => {
    test('lineOfInterest is a key location', () => {
      const lineOfInterest = {lineNum: 789, side: Side.LEFT};
      assert.deepEqual(computeKeyLocations(lineOfInterest, []), {
        left: {789: true},
        right: {},
      });
    });

    test('line comments are key locations', async () => {
      const comments: GrDiffCommentThread[] = [{side: Side.RIGHT, line: 3}];
      assert.deepEqual(computeKeyLocations(undefined, comments), {
        left: {},
        right: {3: true},
      });
    });

    test('file comments are key locations', async () => {
      const comments: GrDiffCommentThread[] = [{side: Side.LEFT, line: FILE}];
      assert.deepEqual(computeKeyLocations(undefined, comments), {
        left: {FILE: true},
        right: {},
      });
    });

    test('lots of key locations', () => {
      const lineOfInterest = {lineNum: 789, side: Side.LEFT};
      const comments: GrDiffCommentThread[] = [
        {side: Side.LEFT, line: FILE},
        {side: Side.LEFT, line: 2},
        {side: Side.LEFT, line: 111},
        {side: Side.RIGHT, line: LOST},
        {side: Side.RIGHT, line: 13},
        {side: Side.RIGHT, line: 19},
      ];
      assert.deepEqual(computeKeyLocations(lineOfInterest, comments), {
        left: {FILE: true, 2: true, 111: true, 789: true},
        right: {LOST: true, 13: true, 19: true},
      });
    });
  });

  suite('toCommentThreadModel', () => {
    test('simple example', () => {
      const el = document.createElement(
        'div'
      ) as unknown as GrDiffThreadElement;
      el.className = 'comment-thread';
      el.setAttribute('diff-side', 'left');
      el.setAttribute('line-num', '3');
      el.rootId = 'ab12';

      assert.deepEqual(getDataFromCommentThreadEl(el), {
        line: 3,
        side: Side.LEFT,
        range: undefined,
        rootId: 'ab12',
      });
    });

    test('FILE default', () => {
      const el = document.createElement(
        'div'
      ) as unknown as GrDiffThreadElement;
      el.className = 'comment-thread';
      el.setAttribute('diff-side', 'left');
      el.rootId = 'ab12';

      assert.deepEqual(getDataFromCommentThreadEl(el), {
        line: FILE,
        side: Side.LEFT,
        range: undefined,
        rootId: 'ab12',
      });
    });

    test('undefined', () => {
      const el = document.createElement(
        'div'
      ) as unknown as GrDiffThreadElement;
      assert.isUndefined(getDataFromCommentThreadEl(el));
      el.className = 'comment-thread';
      assert.isUndefined(getDataFromCommentThreadEl(el));
      el.setAttribute('line-num', '3');
      assert.isUndefined(getDataFromCommentThreadEl(el));
    });
  });

  suite('compare comments', () => {
    test('sort array of comments', () => {
      const comments: GrDiffCommentThread[] = [
        {side: Side.RIGHT, line: 3},
        {side: Side.RIGHT, line: 2},
        {side: Side.RIGHT, line: 1},
        {side: Side.RIGHT, line: LOST},
        {side: Side.RIGHT, line: FILE},
        {side: Side.LEFT, line: 3},
        {side: Side.LEFT, line: 2},
        {
          side: Side.LEFT,
          line: 1,
          rootId: 'b',
          range: {
            start_line: 1,
            start_character: 0,
            end_line: 5,
            end_character: 14,
          },
        },
        {
          side: Side.LEFT,
          line: 1,
          rootId: 'b',
          range: {
            start_line: 1,
            start_character: 0,
            end_line: 2,
            end_character: 4,
          },
        },
        {side: Side.LEFT, line: 1, rootId: 'b'},
        {side: Side.LEFT, line: 1, rootId: 'a'},
        {side: Side.LEFT, line: 1},
        {side: Side.LEFT, line: LOST},
      ];
      const commentsOrdered: GrDiffCommentThread[] = [
        comments[12],
        comments[11],
        comments[10],
        comments[9],
        comments[8],
        comments[7],
        comments[6],
        comments[5],
        comments[4],
        comments[3],
        comments[2],
        comments[1],
        comments[0],
      ];
      assert.sameOrderedMembers(
        comments.sort(compareComments),
        commentsOrdered
      );
    });
  });

  suite('createRevertFixSuggestion', () => {
    test('returns undefined for non-delta group', () => {
      const line = new GrDiffLine(GrDiffLineType.BOTH, 1, 1);
      line.text = 'common line';
      const group = new GrDiffGroup({
        type: GrDiffGroupType.BOTH,
        lines: [line],
      });
      assert.isUndefined(createRevertFixSuggestion('foo.ts', group));
    });

    test('creates fix for modification', () => {
      const removeLine = new GrDiffLine(GrDiffLineType.REMOVE, 10, 0);
      removeLine.text = 'const a = 1;';
      const addLine = new GrDiffLine(GrDiffLineType.ADD, 0, 10);
      addLine.text = 'const a = 2;';
      const group = new GrDiffGroup({
        type: GrDiffGroupType.DELTA,
        lines: [removeLine, addLine],
      });
      const fix = createRevertFixSuggestion('foo.ts', group);
      assert.isDefined(fix);
      assert.equal(fix.fix_id, PROVIDED_FIX_ID);
      assert.equal(fix.description, 'Revert change');
      assert.deepEqual(fix.replacements, [
        {
          path: 'foo.ts',
          range: {
            start_line: 10,
            start_character: 0,
            end_line: 10,
            end_character: 12,
          },
          replacement: 'const a = 1;',
        },
      ]);
    });

    test('creates fix for pure addition in middle of file', () => {
      const prevLine = new GrDiffLine(GrDiffLineType.BOTH, 4, 4);
      prevLine.text = 'common line 4';
      const prevGroup = new GrDiffGroup({
        type: GrDiffGroupType.BOTH,
        lines: [prevLine],
      });

      const addLine1 = new GrDiffLine(GrDiffLineType.ADD, 0, 5);
      addLine1.text = 'new line 5';
      const addLine2 = new GrDiffLine(GrDiffLineType.ADD, 0, 6);
      addLine2.text = 'new line 6';
      const group = new GrDiffGroup({
        type: GrDiffGroupType.DELTA,
        lines: [addLine1, addLine2],
      });

      const nextLine = new GrDiffLine(GrDiffLineType.BOTH, 5, 7);
      nextLine.text = 'common line 7';
      const nextGroup = new GrDiffGroup({
        type: GrDiffGroupType.BOTH,
        lines: [nextLine],
      });

      const fix = createRevertFixSuggestion('foo.ts', group, [
        prevGroup,
        group,
        nextGroup,
      ]);
      assert.isDefined(fix);
      assert.deepEqual(fix.replacements, [
        {
          path: 'foo.ts',
          range: {
            start_line: 4,
            start_character: 0,
            end_line: 7,
            end_character: 0,
          },
          replacement: 'common line 4\n',
        },
      ]);
    });

    test('creates fix for pure addition of empty line in middle of file', () => {
      const prevLine = new GrDiffLine(GrDiffLineType.BOTH, 1, 1);
      prevLine.text = 'first line';
      const prevGroup = new GrDiffGroup({
        type: GrDiffGroupType.BOTH,
        lines: [prevLine],
      });

      const emptyLine = new GrDiffLine(GrDiffLineType.ADD, 0, 2);
      emptyLine.text = '';
      const group = new GrDiffGroup({
        type: GrDiffGroupType.DELTA,
        lines: [emptyLine],
      });

      const nextLine = new GrDiffLine(GrDiffLineType.BOTH, 2, 3);
      nextLine.text = 'second line';
      const nextGroup = new GrDiffGroup({
        type: GrDiffGroupType.BOTH,
        lines: [nextLine],
      });

      const fix = createRevertFixSuggestion('foo.ts', group, [
        prevGroup,
        group,
        nextGroup,
      ]);
      assert.isDefined(fix);
      assert.deepEqual(fix.replacements, [
        {
          path: 'foo.ts',
          range: {
            start_line: 1,
            start_character: 0,
            end_line: 3,
            end_character: 0,
          },
          replacement: 'first line\n',
        },
      ]);
    });

    test('creates fix for pure addition at beginning of file', () => {
      const addLine = new GrDiffLine(GrDiffLineType.ADD, 0, 1);
      addLine.text = '';
      const group = new GrDiffGroup({
        type: GrDiffGroupType.DELTA,
        lines: [addLine],
      });

      const nextLine = new GrDiffLine(GrDiffLineType.BOTH, 1, 2);
      nextLine.text = 'existing line';
      const nextGroup = new GrDiffGroup({
        type: GrDiffGroupType.BOTH,
        lines: [nextLine],
      });

      const fix = createRevertFixSuggestion('foo.ts', group, [
        group,
        nextGroup,
      ]);
      assert.isDefined(fix);
      assert.deepEqual(fix.replacements, [
        {
          path: 'foo.ts',
          range: {
            start_line: 1,
            start_character: 0,
            end_line: 2,
            end_character: 13,
          },
          replacement: 'existing line',
        },
      ]);
    });

    test('creates fix for pure addition at end of file', () => {
      const prevLine = new GrDiffLine(GrDiffLineType.BOTH, 10, 10);
      prevLine.text = 'prev line 10';
      const prevGroup = new GrDiffGroup({
        type: GrDiffGroupType.BOTH,
        lines: [prevLine],
      });

      const addLine = new GrDiffLine(GrDiffLineType.ADD, 0, 11);
      addLine.text = 'end addition';
      const group = new GrDiffGroup({
        type: GrDiffGroupType.DELTA,
        lines: [addLine],
      });

      const fix = createRevertFixSuggestion('foo.ts', group, [
        prevGroup,
        group,
      ]);
      assert.isDefined(fix);
      assert.deepEqual(fix.replacements, [
        {
          path: 'foo.ts',
          range: {
            start_line: 10,
            start_character: 0,
            end_line: 11,
            end_character: 12,
          },
          replacement: 'prev line 10\n',
        },
      ]);
    });

    test('creates fix for pure addition of whole file', () => {
      const addLine = new GrDiffLine(GrDiffLineType.ADD, 0, 1);
      addLine.text = 'whole file content';
      const group = new GrDiffGroup({
        type: GrDiffGroupType.DELTA,
        lines: [addLine],
      });

      const fix = createRevertFixSuggestion('foo.ts', group, [group]);
      assert.isDefined(fix);
      assert.deepEqual(fix.replacements, [
        {
          path: 'foo.ts',
          range: {
            start_line: 1,
            start_character: 0,
            end_line: 1,
            end_character: 18,
          },
          replacement: '',
        },
      ]);
    });

    test('creates fix for pure deletion in middle of file', () => {
      const removeLine1 = new GrDiffLine(GrDiffLineType.REMOVE, 5, 0);
      removeLine1.text = 'deleted line 5';
      const removeLine2 = new GrDiffLine(GrDiffLineType.REMOVE, 6, 0);
      removeLine2.text = 'deleted line 6';
      const group = new GrDiffGroup({
        type: GrDiffGroupType.DELTA,
        lines: [removeLine1, removeLine2],
      });

      const nextLine = new GrDiffLine(GrDiffLineType.BOTH, 7, 5);
      nextLine.text = 'common line';
      const nextGroup = new GrDiffGroup({
        type: GrDiffGroupType.BOTH,
        lines: [nextLine],
      });

      const fix = createRevertFixSuggestion('foo.ts', group, [
        group,
        nextGroup,
      ]);
      assert.isDefined(fix);
      assert.deepEqual(fix.replacements, [
        {
          path: 'foo.ts',
          range: {
            start_line: 5,
            start_character: 0,
            end_line: 5,
            end_character: 0,
          },
          replacement: 'deleted line 5\ndeleted line 6\n',
        },
      ]);
    });

    test('creates fix for pure deletion at end of file', () => {
      const prevLine = new GrDiffLine(GrDiffLineType.BOTH, 4, 4);
      prevLine.text = 'prev line 4';
      const prevGroup = new GrDiffGroup({
        type: GrDiffGroupType.BOTH,
        lines: [prevLine],
      });

      const removeLine = new GrDiffLine(GrDiffLineType.REMOVE, 5, 0);
      removeLine.text = 'deleted last line';
      const group = new GrDiffGroup({
        type: GrDiffGroupType.DELTA,
        lines: [removeLine],
      });

      const fix = createRevertFixSuggestion('foo.ts', group, [
        prevGroup,
        group,
      ]);
      assert.isDefined(fix);
      assert.deepEqual(fix.replacements, [
        {
          path: 'foo.ts',
          range: {
            start_line: 4,
            start_character: 11,
            end_line: 4,
            end_character: 11,
          },
          replacement: '\ndeleted last line',
        },
      ]);
    });

    test('returns undefined when group is not found in non-empty allGroups', () => {
      const otherGroup = new GrDiffGroup({
        type: GrDiffGroupType.BOTH,
        lines: [new GrDiffLine(GrDiffLineType.BOTH, 1, 1)],
      });
      const group = new GrDiffGroup({
        type: GrDiffGroupType.DELTA,
        lines: [new GrDiffLine(GrDiffLineType.ADD, 0, 5)],
      });

      const fix = createRevertFixSuggestion('foo.ts', group, [otherGroup]);
      assert.isUndefined(fix);
    });

    test('returns undefined when pure addition has startLine > 1 without surrounding context', () => {
      const group = new GrDiffGroup({
        type: GrDiffGroupType.DELTA,
        lines: [new GrDiffLine(GrDiffLineType.ADD, 0, 10)],
      });

      const fix = createRevertFixSuggestion('foo.ts', group, [group]);
      assert.isUndefined(fix);
    });

    test('returns undefined when pure addition has startLine > 1 without prevLine even if nextLine exists', () => {
      const addLine = new GrDiffLine(GrDiffLineType.ADD, 0, 5);
      addLine.text = 'added line';
      const group = new GrDiffGroup({
        type: GrDiffGroupType.DELTA,
        lines: [addLine],
      });

      const nextLine = new GrDiffLine(GrDiffLineType.BOTH, 6, 6);
      nextLine.text = 'next line';
      const nextGroup = new GrDiffGroup({
        type: GrDiffGroupType.BOTH,
        lines: [nextLine],
      });

      const fix = createRevertFixSuggestion('foo.ts', group, [
        group,
        nextGroup,
      ]);
      assert.isUndefined(fix);
    });
  });
});
