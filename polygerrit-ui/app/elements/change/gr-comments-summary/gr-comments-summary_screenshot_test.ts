/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrCommentsSummary} from './gr-comments-summary';
import './gr-comments-summary';
import {
  createComment,
  createCommentThread,
} from '../../../test/test-data-generators';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {waitEventLoop} from '../../../test/test-utils';

suite('gr-comments-summary screenshot tests', () => {
  let element: GrCommentsSummary;

  setup(async () => {
    element = await fixture(
      html`<gr-comments-summary
        showCommentCategoryName
        clickableChips
      ></gr-comments-summary>`
    );
  });

  test('screenshot', async () => {
    element.commentThreads = [
      createCommentThread([createComment()]),
      createCommentThread([{...createComment(), unresolved: true}]),
    ];
    element.draftCount = 3;
    await element.updateComplete;
    await waitEventLoop();

    await visualDiff(element, 'gr-comments-summary');
    await visualDiffDarkTheme(element, 'gr-comments-summary');
  });
});
