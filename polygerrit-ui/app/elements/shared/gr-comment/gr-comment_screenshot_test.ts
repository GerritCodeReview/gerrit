/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {CommentInfo, UrlEncodedCommentId} from '../../../api/rest-api';
import {GrComment} from './gr-comment';
import './gr-comment';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {createComment, createDraft} from '../../../test/test-data-generators';
import {
  Comment,
  AccountId,
  DraftInfo,
  EmailAddress,
  Timestamp,
} from '../../../types/common';

const comment: CommentInfo = {
  ...createComment(),
  author: {
    name: 'Mr. Peanutbutter',
    email: 'tenn1sballchaser@aol.com' as EmailAddress,
  },
  id: 'baf0414d_60047215' as UrlEncodedCommentId,
  line: 5,
  message: 'This is the test comment message.',
  updated: '2015-12-08 19:48:33.843000000' as Timestamp,
};

const draft: DraftInfo = {
  ...createDraft(),
  author: {
    name: 'Mr. Peanutbutter',
    email: 'tenn1sballchaser@aol.com' as EmailAddress,
  },
  id: 'baf0414d_60047215' as UrlEncodedCommentId,
  line: 5,
  message: 'This is the test draft message.',
  updated: '2015-12-08 19:48:33.843000000' as Timestamp,
};

const account = {
  email: 'dhruvsri@google.com' as EmailAddress,
  name: 'Dhruv Srivastava',
  _account_id: 1083225 as AccountId,
  registered_on: '2015-12-08 19:48:33.843000000' as Timestamp,
};

suite('gr-comment screenshot tests', () => {
  async function setupComment(
    comment: Comment,
    editing = false,
    initiallyCollapsed = false
  ) {
    const element = await fixture<GrComment>(html`<gr-comment
      .comment=${comment}
      .editing=${editing}
      .initiallyCollapsed=${initiallyCollapsed}
    ></gr-comment>`);
    element.account = account;
    element.showPatchset = true;
    await element.updateComplete;
    return element;
  }

  test('comment', async () => {
    const element = await setupComment(comment);
    await visualDiff(element, 'gr-comment');
    await visualDiffDarkTheme(element, 'gr-comment');
  });

  test('draft', async () => {
    const element = await setupComment(draft);
    await visualDiff(element, 'gr-comment-draft');
    await visualDiffDarkTheme(element, 'gr-comment-draft');
  });

  test('draft editing', async () => {
    const element = await setupComment(draft, true);
    await visualDiff(element, 'gr-comment-draft-editing');
    await visualDiffDarkTheme(element, 'gr-comment-draft-editing');
  });

  test('comment collapsed', async () => {
    const element = await setupComment(comment, false, true);
    await visualDiff(element, 'gr-comment-collapsed');
    await visualDiffDarkTheme(element, 'gr-comment-collapsed');
  });

  test('unresolved comment', async () => {
    const element = await setupComment({...comment, unresolved: true});
    await visualDiff(element, 'gr-comment-unresolved-comment');
    await visualDiffDarkTheme(element, 'gr-comment-unresolved-comment');
  });

  test('unresolved draft', async () => {
    const element = await setupComment({...draft, unresolved: true});
    await visualDiff(element, 'gr-comment-unresolved-draft');
    await visualDiffDarkTheme(element, 'gr-comment-unresolved-draft');
  });
});
