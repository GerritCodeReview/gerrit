/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup-karma';
import './syntax-util';
import {
  highlightedStringToRanges,
  removeFirstSpan,
  SpanType,
} from './syntax-util';

suite('file syntax-util', () => {
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
      assert.deepEqual(highlightedStringToRanges(''), [{ranges: []}]);
      assert.deepEqual(highlightedStringToRanges('\n'), [
        {ranges: []},
        {ranges: []},
      ]);
      assert.deepEqual(highlightedStringToRanges('asdf\nasdf\nasdf'), [
        {ranges: []},
        {ranges: []},
        {ranges: []},
      ]);
    });

    test('one line, one span', async () => {
      assert.deepEqual(
        highlightedStringToRanges('asdf<span class="c">qwer</span>asdf'),
        [{ranges: [{start: 4, length: 4, className: 'c'}]}]
      );
      assert.deepEqual(
        highlightedStringToRanges('<span class="d">asdfqwer</span>'),
        [{ranges: [{start: 0, length: 8, className: 'd'}]}]
      );
    });

    test('one line, two spans one after another', async () => {
      assert.deepEqual(
        highlightedStringToRanges(
          'asdf<span class="c">qwer</span>zxcv<span class="d">qwer</span>asdf'
        ),
        [
          {
            ranges: [
              {start: 4, length: 4, className: 'c'},
              {start: 12, length: 4, className: 'd'},
            ],
          },
        ]
      );
    });

    test('one line, two nested spans', async () => {
      assert.deepEqual(
        highlightedStringToRanges(
          'asdf<span class="c">qwer<span class="d">zxcv</span>qwer</span>asdf'
        ),
        [
          {
            ranges: [
              {start: 4, length: 12, className: 'c'},
              {start: 8, length: 4, className: 'd'},
            ],
          },
        ]
      );
    });

    test('one complex line with escaped HTML', async () => {
      assert.deepEqual(
        highlightedStringToRanges(
          '  <span class="tag">&lt;<span class="name">span</span> <span class="attr">class</span>=<span class="string">&quot;title&quot;</span>&gt;</span>[[name]]<span class="tag">&lt;/<span class="name">span</span>&gt;</span>'
        ),
        [
          {
            ranges: [
              // '  <span class="title">[[name]]</span>'
              {start: 2, length: 20, className: 'tag'},
              {start: 3, length: 4, className: 'name'},
              {start: 8, length: 5, className: 'attr'},
              {start: 14, length: 7, className: 'string'},
              {start: 30, length: 7, className: 'tag'},
              {start: 32, length: 4, className: 'name'},
            ],
          },
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
          {ranges: [{start: 4, length: 4, className: 'c'}]},
          {ranges: [{start: 3, length: 3, className: 'd'}]},
        ]
      );
    });

    test('one span over two lines', async () => {
      assert.deepEqual(
        highlightedStringToRanges(
          'asdf<span class="c">qwer\n' + 'asdf</span>qwer'
        ),
        [
          {ranges: [{start: 4, length: 4, className: 'c'}]},
          {ranges: [{start: 0, length: 4, className: 'c'}]},
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
          {
            ranges: [
              {start: 4, length: 8, className: 'c'},
              {start: 8, length: 4, className: 'd'},
            ],
          },
          {
            ranges: [
              {start: 0, length: 8, className: 'c'},
              {start: 0, length: 4, className: 'd'},
            ],
          },
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
          {ranges: [{start: 4, length: 4, className: 'c'}]},
          {
            ranges: [
              {start: 0, length: 8, className: 'c'},
              {start: 4, length: 4, className: 'd'},
            ],
          },
          {
            ranges: [
              {start: 0, length: 8, className: 'c'},
              {start: 0, length: 4, className: 'd'},
            ],
          },
          {ranges: [{start: 0, length: 4, className: 'c'}]},
        ]
      );
    });
  });
});
