/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup-karma';
import './hljs-worker';
import {postProcess} from './hljs-worker';

suite('hljs-worker', () => {
  suite('postProcess', () => {
    test('easy lines', async () => {
      const lines = ['', ' ', 'asdf', 'span'];
      const processed = postProcess(lines.join('\n'));
      for (let i = 0; i < lines.length; i++) {
        assert.equal(processed[i], lines[i]);
      }
    });

    test('simple spans without carry over', async () => {
      const lines = [
        '<span class="c"></span>',
        ' <span class="c"> </span> ',
        'asdf<span class="c">asdf</span>asdf',
        '<div><span class="c"></span></div>',
      ];
      const processed = postProcess(lines.join('\n'));
      for (let i = 0; i < lines.length; i++) {
        assert.equal(processed[i], lines[i]);
      }
    });

    test('one carry over two lines', async () => {
      const lines = ['<span class="c">', '</span>'];
      const processed = postProcess(lines.join('\n'));
      for (let i = 0; i < lines.length; i++) {
        assert.equal(processed[i], '<span class="c"></span>');
      }
    });

    test('double carry over two lines', async () => {
      const lines = ['<span class="c"><span class="d">', '</span></span>'];
      const processed = postProcess(lines.join('\n'));
      for (let i = 0; i < lines.length; i++) {
        assert.equal(
          processed[i],
          '<span class="c"><span class="d"></span></span>'
        );
      }
    });

    test('one carry over five lines', async () => {
      const lines = ['<span class="c">', '', '', '', '</span>'];
      const processed = postProcess(lines.join('\n'));
      for (let i = 0; i < lines.length; i++) {
        assert.equal(processed[i], '<span class="c"></span>');
      }
    });

    test('carry growing and shrinking', async () => {
      const lines = [
        '<span class="c">',
        '<span class="d">',
        '<span class="e">',
        '',
        '</span>',
        '</span>',
        '</span>',
      ];
      const processed = postProcess(lines.join('\n'));
      assert.equal(processed[0], '<span class="c"></span>');
      assert.equal(
        processed[1],
        '<span class="c"><span class="d"></span></span>'
      );
      assert.equal(
        processed[2],
        '<span class="c"><span class="d"><span class="e"></span></span></span>'
      );
      assert.equal(
        processed[3],
        '<span class="c"><span class="d"><span class="e"></span></span></span>'
      );
      assert.equal(
        processed[4],
        '<span class="c"><span class="d"><span class="e"></span></span></span>'
      );
      assert.equal(
        processed[5],
        '<span class="c"><span class="d"></span></span>'
      );
      assert.equal(processed[6], '<span class="c"></span>');
    });
  });
});
