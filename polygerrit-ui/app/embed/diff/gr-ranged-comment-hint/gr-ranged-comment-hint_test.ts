/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-ranged-comment-hint';
import {CommentRange} from '../../../types/common';
import {GrRangedCommentHint} from './gr-ranged-comment-hint';
import {queryAndAssert, waitEventLoop} from '../../../test/test-utils';
import {GrRangeHeader} from '../gr-range-header/gr-range-header';
import {assert, fixture, html} from '@open-wc/testing';

suite('gr-ranged-comment-hint tests', () => {
  let element: GrRangedCommentHint;

  setup(async () => {
    element = await fixture(
      html`<gr-ranged-comment-hint></gr-ranged-comment-hint>`
    );
    await waitEventLoop();
  });

  test('shows line range', async () => {
    element.range = {
      start_line: 2,
      start_character: 1,
      end_line: 5,
      end_character: 3,
    } as CommentRange;
    await waitEventLoop();
    const textDiv = queryAndAssert<GrRangeHeader>(element, 'gr-range-header');
    assert.equal(textDiv?.innerText.trim(), 'Long comment range 2 - 5');
  });
});
