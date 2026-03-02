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
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {GrDiffAiResult} from './gr-diff-ai-result';
import './gr-diff-ai-result';
import {
  CreateCommentPart,
  ResponsePartType,
} from '../../../models/chat/chat-model';

suite('gr-diff-ai-result screenshot tests', () => {
  let element: GrDiffAiResult;

  setup(async () => {
    element = await fixture(html`<gr-diff-ai-result></gr-diff-ai-result>`);
  });

  test('renders', async () => {
    element.result = {
      id: 1,
      commentCreationId: 'comment-1',
      type: ResponsePartType.CREATE_COMMENT,
      content: '',
      comment: {
        message:
          'Here is a suggestion: please consider using a more functional approach.',
        path: 'test.ts',
      },
    } as CreateCommentPart;
    await element.updateComplete;

    // Create a container with a fixed width to stabilize the component's dimensions.
    const container = document.createElement('div');
    container.style.width = '600px';
    container.style.display = 'inline-block';
    container.appendChild(element);
    document.body.appendChild(container);

    await visualDiff(container, 'gr-diff-ai-result');
    await visualDiffDarkTheme(container, 'gr-diff-ai-result');

    document.body.removeChild(container);
  });
});
