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
import {GrMessage} from './gr-message';
import './gr-message';
import {ChangeMessage, CommentThread, Timestamp} from '../../../types/common';
import {
  createAccountDetailWithId,
  createChangeMessage,
  createCommentThread,
} from '../../../test/test-data-generators';

const author = createAccountDetailWithId(1);

const msg: ChangeMessage = {
  ...createChangeMessage(),
  author,
  message: 'Review comments published',
  date: '2021-11-01 10:11:12.000000000' as Timestamp,
};

const msg_with_scores: ChangeMessage = {
  ...createChangeMessage(),
  author,
  message: 'Patch Set 1: Verified+1 Code-Review-2 Trybot-Label3+1',
  date: '2021-11-01 10:11:12.000000000' as Timestamp,
};

const labelExtremes = {
  Verified: {max: 1, min: -1},
  'Code-Review': {max: 2, min: -2},
  'Trybot-Label3': {max: 3, min: 0},
};

const thread_normal: CommentThread = createCommentThread([
  {
    author,
    message: 'This is a normal comment',
    updated: '2021-11-01 10:11:12.000000000' as Timestamp,
  },
]);

const thread_ai: CommentThread = createCommentThread([
  {
    author,
    message: 'This is an AI comment',
    updated: '2021-11-01 10:11:12.000000000' as Timestamp,
    is_ai: true,
  },
]);

suite('gr-message screenshot tests', () => {
  let element: GrMessage;

  setup(async () => {
    element = await fixture(html`<gr-message></gr-message>`);
    element.message = msg;
  });

  test('collapsed normal', async () => {
    element.commentThreads = [thread_normal];
    element.message = {...msg, expanded: false};
    await element.updateComplete;

    await visualDiff(element, 'gr-message-collapsed-normal');
    await visualDiffDarkTheme(element, 'gr-message-collapsed-normal');
  });

  test('collapsed AI', async () => {
    element.commentThreads = [thread_ai];
    element.message = {...msg, expanded: false};
    await element.updateComplete;

    await visualDiff(element, 'gr-message-collapsed-ai');
    await visualDiffDarkTheme(element, 'gr-message-collapsed-ai');
  });

  test('expanded AI', async () => {
    element.commentThreads = [thread_ai];
    element.message = {...msg, expanded: true};
    await element.updateComplete;

    await visualDiff(element, 'gr-message-expanded-ai');
    await visualDiffDarkTheme(element, 'gr-message-expanded-ai');
  });

  test('collapsed with scores', async () => {
    element.message = {...msg_with_scores, expanded: false};
    element.labelExtremes = labelExtremes;
    await element.updateComplete;

    await visualDiff(element, 'gr-message-collapsed-scores');
    await visualDiffDarkTheme(element, 'gr-message-collapsed-scores');
  });

  test('expanded with scores', async () => {
    element.message = {...msg_with_scores, expanded: true};
    element.labelExtremes = labelExtremes;
    await element.updateComplete;

    await visualDiff(element, 'gr-message-expanded-scores');
    await visualDiffDarkTheme(element, 'gr-message-expanded-scores');
  });
});
