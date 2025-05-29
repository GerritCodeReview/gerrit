/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import {GrCommentsSummary} from './gr-comments-summary';
import {
  createComment,
  createCommentThread,
} from '../../../test/test-data-generators';

suite('gr-comments-summary test', () => {
  let element: GrCommentsSummary;

  setup(async () => {
    element = await fixture(
      html`<gr-comments-summary
        showCommentCategoryName
        clickableChips
      ></gr-comments-summary>`
    );
  });

  test('is defined', () => {
    const el = document.createElement('gr-comments-summary');
    assert.instanceOf(el, GrCommentsSummary);
  });

  test('renders', async () => {
    element.commentThreads = [
      createCommentThread([createComment()]),
      createCommentThread([{...createComment(), unresolved: true}]),
    ];
    element.draftCount = 3;
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `<gr-summary-chip
          category="drafts"
          icon="rate_review"
          iconFilled
          styletype="info"
        >
          3 drafts
        </gr-summary-chip>
        <gr-summary-chip category="unresolved" styletype="warning">
          <gr-avatar-stack imageSize="32">
            <gr-icon
              class="unresolvedIcon"
              filled
              icon="chat_bubble"
              slot="fallback"
            ></gr-icon>
          </gr-avatar-stack>
          1 unresolved
        </gr-summary-chip>
        <gr-summary-chip
          category="show all"
          icon="mark_chat_read"
          styletype="check"
        >
          1 resolved
        </gr-summary-chip>`
    );
  });
});
