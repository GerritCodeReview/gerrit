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
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {GrCommentThread} from './gr-comment-thread';
import './gr-comment-thread';
import {
  CommentInfo,
  DraftInfo,
  NumericChangeId,
  RepoName,
  SavingState,
  Timestamp,
  UrlEncodedCommentId,
} from '../../../types/common';
import {
  createAccountDetailWithId,
  createThread,
} from '../../../test/test-data-generators';
import {
  ChangeChildView,
  changeViewModelToken,
} from '../../../models/views/change';
import {GerritView} from '../../../services/router/router-model';
import {testResolver} from '../../../test/common-test-setup';

const c1: CommentInfo = {
  author: createAccountDetailWithId(1),
  id: 'the-root' as UrlEncodedCommentId,
  message: 'start the conversation',
  updated: '2021-11-01 10:11:12.000000000' as Timestamp,
};

const c2: CommentInfo = {
  author: createAccountDetailWithId(2),
  id: 'the-reply' as UrlEncodedCommentId,
  message: 'keep it going',
  updated: '2021-11-02 10:11:12.000000000' as Timestamp,
  in_reply_to: 'the-root' as UrlEncodedCommentId,
};

const c3: DraftInfo = {
  author: createAccountDetailWithId(1),
  id: 'the-draft' as UrlEncodedCommentId,
  message: 'stop it',
  updated: '2021-11-03 10:11:12.000000000' as Timestamp,
  in_reply_to: 'the-reply' as UrlEncodedCommentId,
  savingState: SavingState.OK,
};

suite('gr-comment-thread screenshot tests', () => {
  let element: GrCommentThread;

  setup(async () => {
    testResolver(changeViewModelToken).setState({
      view: GerritView.CHANGE,
      childView: ChangeChildView.OVERVIEW,
      changeNum: 1 as NumericChangeId,
      repo: 'test-repo-name' as RepoName,
    });
    element = await fixture(html`<gr-comment-thread></gr-comment-thread>`);
    element.changeNum = 1 as NumericChangeId;
    element.showFileName = true;
    element.showFilePath = true;
    element.repoName = 'test-repo-name' as RepoName;
    await element.updateComplete;
  });

  test('unresolved', async () => {
    element.thread = createThread(c1, {...c2, unresolved: true});
    await element.updateComplete;

    await visualDiff(element, 'gr-comment-thread-unresolved');
    await visualDiffDarkTheme(element, 'gr-comment-thread-unresolved');
  });

  test('resolved', async () => {
    element.thread = createThread(c1, c2);
    await element.updateComplete;

    await visualDiff(element, 'gr-comment-thread-resolved');
    await visualDiffDarkTheme(element, 'gr-comment-thread-resolved');
  });

  test('with draft', async () => {
    element.thread = createThread(c1, c2, c3);
    await element.updateComplete;

    await visualDiff(element, 'gr-comment-thread-with-draft');
    await visualDiffDarkTheme(element, 'gr-comment-thread-with-draft');
  });
});
