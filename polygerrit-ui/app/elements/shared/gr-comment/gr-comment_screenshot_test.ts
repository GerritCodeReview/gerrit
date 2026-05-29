/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {GrComment} from './gr-comment';
import './gr-comment';
import {
  CommentInfo,
  Timestamp,
  UrlEncodedCommentId,
} from '../../../types/common';
import {
  createAccountDetailWithId,
  createComment,
} from '../../../test/test-data-generators';

const c_normal: CommentInfo = {
  ...createComment(),
  author: createAccountDetailWithId(1),
  id: 'normal-comment' as UrlEncodedCommentId,
  message: 'This is a normal comment',
  updated: '2021-11-01 10:11:12.000000000' as Timestamp,
};

const c_ai: CommentInfo = {
  ...createComment(),
  author: createAccountDetailWithId(2),
  id: 'ai-comment' as UrlEncodedCommentId,
  message: 'This is an AI generated comment',
  updated: '2021-11-02 10:11:12.000000000' as Timestamp,
  is_ai: true,
};

suite('gr-comment screenshot tests', () => {
  let element: GrComment;

  test('normal comment', async () => {
    element = await fixture(
      html`<gr-comment .comment=${c_normal}></gr-comment>`
    );
    await visualDiff(element, 'gr-comment-normal');
    await visualDiffDarkTheme(element, 'gr-comment-normal');
  });

  test('AI comment', async () => {
    element = await fixture(html`<gr-comment .comment=${c_ai}></gr-comment>`);
    await visualDiff(element, 'gr-comment-ai');
    await visualDiffDarkTheme(element, 'gr-comment-ai');
  });
});
