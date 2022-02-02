/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup-karma';
import './hljs-util';
import {
  highlightedStringToRanges,
  removeFirstSpan,
  SpanType,
} from './hljs-util';

suite('file hljs-util', () => {
  suite('function removeFirstSpan()', () => {
    test('no matches', async () => {
      assert.isUndefined(removeFirstSpan(''));
      assert.isUndefined(removeFirstSpan('span'));
      assert.isUndefined(removeFirstSpan('<span>'));
      assert.isUndefined(removeFirstSpan('</span'));
      assert.isUndefined(removeFirstSpan('asdf'));
    });

    test('simple opening match', async () => {
      const removal = removeFirstSpan('asdf<span class="c">asdf');
      assert.deepEqual(removal, {
        type: SpanType.OPENING,
        lineAfter: 'asdfasdf',
        class: 'c',
        offset: 4,
      });
    });

    test('simple closing match', async () => {
      const removal = removeFirstSpan('asdf</span>asdf');
      assert.deepEqual(removal, {
        type: SpanType.CLOSING,
        lineAfter: 'asdfasdf',
        class: undefined,
        offset: 4,
      });
    });
  });

  suite('function highlightedStringToRanges()', () => {
    test('no ranges', async () => {
      assert.deepEqual(highlightedStringToRanges(''), [[]]);
      assert.deepEqual(highlightedStringToRanges('\n'), [[], []]);
      assert.deepEqual(highlightedStringToRanges('asdf\nasdf\nasdf'), [
        [],
        [],
        [],
      ]);
    });

    test('one line, one span', async () => {
      assert.deepEqual(
        highlightedStringToRanges('asdf<span class="c">qwer</span>asdf'),
        [[{start: 4, length: 4, className: 'c'}]]
      );
      assert.deepEqual(
        highlightedStringToRanges('<span class="d">asdfqwer</span>'),
        [[{start: 0, length: 8, className: 'd'}]]
      );
    });

    test('one line, two spans one after another', async () => {
      assert.deepEqual(
        highlightedStringToRanges(
          'asdf<span class="c">qwer</span>zxcv<span class="d">qwer</span>asdf'
        ),
        [
          [
            {start: 4, length: 4, className: 'c'},
            {start: 12, length: 4, className: 'd'},
          ],
        ]
      );
    });

    test('one line, two nested spans', async () => {
      assert.deepEqual(
        highlightedStringToRanges(
          'asdf<span class="c">qwer<span class="d">zxcv</span>qwer</span>asdf'
        ),
        [
          [
            {start: 4, length: 12, className: 'c'},
            {start: 8, length: 4, className: 'd'},
          ],
        ]
      );
    });

    test('two lines, one span each', async () => {
      assert.deepEqual(
        highlightedStringToRanges(
          'asdf<span class="c">qwer</span>asdf\n' +
            'asd<span class="d">qwe</span>asd'
        ),
        [
          [{start: 4, length: 4, className: 'c'}],
          [{start: 3, length: 3, className: 'd'}],
        ]
      );
    });

    test('one span over two lines', async () => {
      assert.deepEqual(
        highlightedStringToRanges(
          'asdf<span class="c">qwer\n' + 'asdf</span>qwer'
        ),
        [
          [{start: 4, length: 4, className: 'c'}],
          [{start: 0, length: 4, className: 'c'}],
        ]
      );
    });

    test('two spans over two lines', async () => {
      assert.deepEqual(
        highlightedStringToRanges(
          'asdf<span class="c">qwer<span class="d">zxcv\n' +
            'asdf</span>qwer</span>zxcv'
        ),
        [
          [
            {start: 4, length: 8, className: 'c'},
            {start: 8, length: 4, className: 'd'},
          ],
          [
            {start: 0, length: 8, className: 'c'},
            {start: 0, length: 4, className: 'd'},
          ],
        ]
      );
    });

    test('two spans over four lines', async () => {
      assert.deepEqual(
        highlightedStringToRanges(
          'asdf<span class="c">qwer\n' +
            'asdf<span class="d">qwer\n' +
            'asdf</span>qwer\n' +
            'asdf</span>qwer'
        ),
        [
          [{start: 4, length: 4, className: 'c'}],
          [
            {start: 0, length: 8, className: 'c'},
            {start: 4, length: 4, className: 'd'},
          ],
          [
            {start: 0, length: 8, className: 'c'},
            {start: 0, length: 4, className: 'd'},
          ],
          [{start: 0, length: 4, className: 'c'}],
        ]
      );
    });
  });
});
