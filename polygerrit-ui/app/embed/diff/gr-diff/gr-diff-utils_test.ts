/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import '../../../test/common-test-setup';
import {
  createElementDiff,
  formatText,
  createTabWrapper,
  getRange,
  computeKeyLocations,
  GrDiffCommentThread,
  getDataFromCommentThreadEl,
  compareComments,
  GrDiffThreadElement,
  computeContext,
  FULL_CONTEXT,
  FullContext,
  computeLineLength,
} from './gr-diff-utils';
import {FILE, LOST, Side} from '../../../api/diff';
import {createDefaultDiffPrefs} from '../../../constants/constants';

const LINE_BREAK_HTML = '<span class="gr-diff br"></span>';

suite('gr-diff-utils tests', () => {
  test('createElementDiff classStr applies all classes', () => {
    const node = createElementDiff('div', 'test classes');
    assert.isTrue(node.classList.contains('gr-diff'));
    assert.isTrue(node.classList.contains('test'));
    assert.isTrue(node.classList.contains('classes'));
  });

  test('formatText newlines 1', () => {
    let text = 'abcdef';

    assert.equal(
      formatText(text, 'NONE', 4, 10, '').firstElementChild?.innerHTML,
      text
    );
    text = 'a'.repeat(20);
    assert.equal(
      formatText(text, 'NONE', 4, 10, '').firstElementChild?.innerHTML,
      'a'.repeat(10) + LINE_BREAK_HTML + 'a'.repeat(10)
    );
  });

  test('formatText newlines 2', () => {
    const text = '<span class="thumbsup">👍</span>';
    assert.equal(
      formatText(text, 'NONE', 4, 10, '').firstElementChild?.innerHTML,
      '&lt;span clas' +
        LINE_BREAK_HTML +
        's="thumbsu' +
        LINE_BREAK_HTML +
        'p"&gt;👍&lt;/span' +
        LINE_BREAK_HTML +
        '&gt;'
    );
  });

  test('formatText newlines 3', () => {
    const text = '01234\t56789';
    assert.equal(
      formatText(text, 'NONE', 4, 10, '').firstElementChild?.innerHTML,
      '01234' + createTabWrapper(3).outerHTML + '56' + LINE_BREAK_HTML + '789'
    );
  });

  test('formatText newlines 4', () => {
    const text = '👍'.repeat(58);
    assert.equal(
      formatText(text, 'NONE', 4, 20, '').firstElementChild?.innerHTML,
      '👍'.repeat(20) +
        LINE_BREAK_HTML +
        '👍'.repeat(20) +
        LINE_BREAK_HTML +
        '👍'.repeat(18)
    );
  });

  test('tab wrapper style', () => {
    const pattern = new RegExp(
      '^<span class="gr-diff tab" ' +
        'style="((?:-moz-)?tab-size: (\\d+);.?)+">\\t<\\/span>$'
    );

    for (const size of [1, 3, 8, 55]) {
      const html = createTabWrapper(size).outerHTML;
      assert.match(html, pattern);
      assert.equal(html.match(pattern)?.[2], size.toString());
    }
  });

  test('tab wrapper insertion', () => {
    const html = 'abc\tdef';
    const tabSize = 8;
    const wrapper = createTabWrapper(tabSize - 3);
    assert.ok(wrapper);
    assert.equal(wrapper.innerText, '\t');
    assert.equal(
      formatText(html, 'NONE', tabSize, Infinity, '').firstElementChild
        ?.innerHTML,
      'abc' + wrapper.outerHTML + 'def'
    );
  });

  test('escaping HTML', () => {
    let input = '<script>alert("XSS");<' + '/script>';
    let expected = '&lt;script&gt;alert("XSS");&lt;/script&gt;';

    let result = formatText(input, 'NONE', 1, Number.POSITIVE_INFINITY, '')
      .firstElementChild?.innerHTML;
    assert.equal(result, expected);

    input = '& < > " \' / `';
    expected = '&amp; &lt; &gt; " \' / `';
    result = formatText(input, 'NONE', 1, Number.POSITIVE_INFINITY, '')
      .firstElementChild?.innerHTML;
    assert.equal(result, expected);
  });

  test('text length with tabs and unicode', () => {
    function expectTextLength(text: string, tabSize: number, expected: number) {
      // Formatting to |expected| columns should not introduce line breaks.
      const result = formatText(text, 'NONE', tabSize, expected, '')
        .firstElementChild!;
      assert.isNotOk(
        result.querySelector('.contentText > .br'),
        '  Expected the result of: \n' +
          `      _formatText(${text}', 'NONE',  ${tabSize}, ${expected})\n` +
          '  to not contain a br. But the actual result HTML was:\n' +
          `      '${result.innerHTML}'\nwhereupon`
      );

      // Increasing the line limit should produce the same markup.
      assert.equal(
        formatText(text, 'NONE', tabSize, Infinity, '').firstElementChild
          ?.innerHTML,
        result.innerHTML
      );
      assert.equal(
        formatText(text, 'NONE', tabSize, expected + 1, '').firstElementChild
          ?.innerHTML,
        result.innerHTML
      );

      // Decreasing the line limit should introduce line breaks.
      if (expected > 0) {
        const tooSmall = formatText(text, 'NONE', tabSize, expected - 1, '')
          .firstElementChild!;
        assert.isOk(
          tooSmall.querySelector('.contentText .br'),
          '  Expected the result of: \n' +
            `      _formatText(${text}', ${tabSize}, ${expected - 1})\n` +
            '  to contain a br. But the actual result HTML was:\n' +
            `      '${tooSmall.innerHTML}'\nwhereupon`
        );
      }
    }
    expectTextLength('12345', 4, 5);
    expectTextLength('\t\t12', 4, 10);
    expectTextLength('abc💢123', 4, 7);
    expectTextLength('abc\t', 8, 8);
    expectTextLength('abc\t\t', 10, 20);
    expectTextLength('', 10, 0);
    // 17 Thai combining chars.
    expectTextLength('ก้้้้้้้้้้้้้้้้', 4, 17);
    expectTextLength('abc\tde', 10, 12);
    expectTextLength('abc\tde\t', 10, 20);
    expectTextLength('\t\t\t\t\t', 20, 100);
  });

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
