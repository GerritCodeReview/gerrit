/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-diff-text';
import {GrDiffText} from './gr-diff-text';
import {fixture, html, assert} from '@open-wc/testing';

const LINE_BREAK = '<span class="gr-diff br"></span>';

const TAB = '<span class="" style=""></span>';

const TAB_IGNORE = ['class', 'style'];

suite('gr-diff-text test', () => {
  let element: GrDiffText;

  setup(async () => {
    element = await fixture<GrDiffText>(
      html`<gr-diff-text tabsize="4" linelimit="10"></gr-diff-text>`
    );
  });

  const check = async (
    text: string,
    html: string,
    ignoreAttributes: string[] = []
  ) => {
    element.text = text;
    await element.updateComplete;
    assert.lightDom.equal(element, html, {ignoreAttributes});
  };

  suite('lit rendering', () => {
    test('renderText newlines 1', async () => {
      await check('abcdef', 'abcdef');
      await check('a'.repeat(20), `aaaaaaaaaa${LINE_BREAK}aaaaaaaaaa`);
    });

    test('renderText newlines 2', async () => {
      await check(
        '<span class="thumbsup">👍</span>',
        '&lt;span clas' +
          LINE_BREAK +
          's="thumbsu' +
          LINE_BREAK +
          'p"&gt;👍&lt;/span' +
          LINE_BREAK +
          '&gt;'
      );
    });

    test('renderText newlines 3', async () => {
      await check(
        '01234\t56789',
        '01234' + TAB + '56' + LINE_BREAK + '789',
        TAB_IGNORE
      );
    });

    test('renderText newlines 4', async () => {
      element.lineLimit = 20;
      await element.updateComplete;
      await check(
        '👍'.repeat(58),
        '👍'.repeat(20) +
          LINE_BREAK +
          '👍'.repeat(20) +
          LINE_BREAK +
          '👍'.repeat(18)
      );
    });

    test('tab wrapper style', async () => {
      element.lineLimit = 100;
      element.text = '\t';
      await element.updateComplete;
      for (const size of [1, 3, 8, 55]) {
        element.tabSize = size;
        await element.updateComplete;
        await check(
          '\t',
          /* HTML */ `
            <span class="gr-diff tab" style="tab-size: ${size};"> </span>
          `
        );
      }
    });

    test('tab wrapper insertion', async () => {
      await check('abc\tdef', 'abc' + TAB + 'def', TAB_IGNORE);
    });

    test('escaping HTML', async () => {
      element.lineLimit = 100;
      await element.updateComplete;
      await check(
        '<script>alert("XSS");<' + '/script>',
        '&lt;script&gt;alert("XSS");&lt;/script&gt;'
      );
      await check('& < > " \' / `', '&amp; &lt; &gt; " \' / `');
    });

    test('text length with tabs and unicode', async () => {
      async function expectTextLength(
        text: string,
        tabSize: number,
        expected: number
      ) {
        element.text = text;
        element.tabSize = tabSize;
        element.lineLimit = expected;
        await element.updateComplete;
        const result = element.innerHTML;

        // Must not contain a line break.
        assert.isNotOk(element.querySelector('span.br'));

        // Increasing the line limit by 1 should not change anything.
        element.lineLimit = expected + 1;
        await element.updateComplete;
        const resultPlusOne = element.innerHTML;
        assert.equal(resultPlusOne, result);

        // Increasing the line limit to infinity should not change anything.
        element.lineLimit = Infinity;
        await element.updateComplete;
        const resultInf = element.innerHTML;
        assert.equal(resultInf, result);

        // Decreasing the line limit by 1 should introduce a line break.
        element.lineLimit = expected + 1;
        await element.updateComplete;
        assert.isNotOk(element.querySelector('span.br'));
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
  });
});
