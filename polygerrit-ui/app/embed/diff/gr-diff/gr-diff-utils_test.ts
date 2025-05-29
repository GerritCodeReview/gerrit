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
  FULL_CONTEXT,
  FullContext,
  getDataFromCommentThreadEl,
  getRange,
  GrDiffCommentThread,
  GrDiffThreadElement,
} from './gr-diff-utils';
import {FILE, LOST, Side} from '../../../api/diff';
import {createDefaultDiffPrefs} from '../../../constants/constants';

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
});
