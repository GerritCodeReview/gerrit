/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import '../../../test/common-test-setup';
import './gr-diff-ai-result';
import {GrDiffAiResult} from './gr-diff-ai-result';
import {
  CreateCommentPart,
  ResponsePartType,
} from '../../../models/chat/chat-model';

suite('gr-diff-ai-result tests', () => {
  let element: GrDiffAiResult;

  setup(async () => {
    element = document.createElement('gr-diff-ai-result');
    document.body.appendChild(element);
    await element.updateComplete;
  });

  teardown(() => {
    if (element) element.remove();
  });

  test('renders', async () => {
    element.result = {
      id: 1,
      commentCreationId: 'comment-1',
      type: ResponsePartType.CREATE_COMMENT,
      content: '',
      comment: {
        message: 'This is a great suggestion!',
        path: 'test.ts',
      },
    } as CreateCommentPart;
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      `
      <div id="container">
        <div
          class="comment-box font-normal info"
          tabindex="0"
        >
          <div class="header">
            <div class="icon">
              <gr-icon custom="" icon="ai"></gr-icon>
            </div>
            <div class="name">AI Suggestion</div>
          </div>
          <div class="details">
            <div class="message">
              <gr-formatted-text></gr-formatted-text>
            </div>
            <div class="actions">
              <gr-button
                aria-disabled="false"
                class="add-as-comment-button"
                primary=""
                role="button"
                tabindex="0"
              >
                Add as Comment
              </gr-button>
            </div>
          </div>
        </div>
      </div>
      `
    );
  });
});
