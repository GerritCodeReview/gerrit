/**
 * @license
 * Copyright (C) 2026 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {assert} from '@open-wc/testing';
import {highlightedCodeFragment} from './code-fence';

suite('code fence', () => {
  test('renders nested ranges on dense lines without changing the text', () => {
    const text = '🐟' + '1,'.repeat(1024) + 'tail';
    const tokens = Array.from({length: 1024}, (_, i) => {
      return {start: 2 + i * 2, length: 1, className: 'number'};
    });
    const fragment = highlightedCodeFragment(text + '\n', [
      {
        ranges: [
          {start: 0, length: text.length - 4, className: 'string'},
          {start: 2, length: 3, className: 'unknown-token'},
          ...tokens,
          {start: text.length - 4, length: 4, className: 'comment'},
        ],
      },
    ]);
    const code = document.createElement('code');
    code.append(fragment);
    assert.equal(code.textContent, text + '\n');
    assert.lengthOf(
      code.querySelectorAll('.gr-syntax-string > .gr-syntax-number'),
      1024
    );
    assert.equal(
      code.querySelector(':scope > .gr-syntax-comment')?.textContent,
      'tail'
    );
    assert.isNull(code.querySelector('.gr-syntax-unknown-token'));
  });
});
