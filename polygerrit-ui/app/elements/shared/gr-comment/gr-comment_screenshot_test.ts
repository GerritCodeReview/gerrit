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
import {queryAndAssert, visualDiffDarkTheme} from '../../../test/test-utils';
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
import {GrFormattedText} from '../gr-formatted-text/gr-formatted-text';

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

const c_suggestion: CommentInfo = {
  ...createComment(),
  author: createAccountDetailWithId(1),
  id: 'suggestion-comment' as UrlEncodedCommentId,
  message:
    'Please consider this change:\n```suggestion\n  private newMethod() {\n    console.log("new");\n  }\n```',
  updated: '2021-11-01 10:11:12.000000000' as Timestamp,
};

const c_hidden_prompt: CommentInfo = {
  ...createComment(),
  author: createAccountDetailWithId(1),
  id: 'hidden-prompt-comment' as UrlEncodedCommentId,
  message: 'Looks good:\n```hidden prompt\n  const x = 1;\n```',
  updated: '2021-11-01 10:11:12.000000000' as Timestamp,
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

  test('comment with suggestion and raw markdown expanded', async () => {
    element = await fixture(
      html`<gr-comment .comment=${c_suggestion}></gr-comment>`
    );
    element['commentModel'].updateState({
      comment: c_suggestion,
      commentedText: '  private oldMethod() {\n    console.log("old");\n  }',
    });
    await element.updateComplete;

    const formattedText = queryAndAssert<GrFormattedText>(
      element,
      'gr-formatted-text'
    );
    await formattedText.updateComplete;

    const summary = queryAndAssert<HTMLElement>(
      formattedText,
      'summary.raw-markdown-summary'
    );
    summary.click();
    await formattedText.updateComplete;

    await visualDiff(element, 'gr-comment-suggestion-raw-markdown');
    await visualDiffDarkTheme(element, 'gr-comment-suggestion-raw-markdown');
  });

  test('comment with hidden prompt code block language and raw markdown expanded', async () => {
    element = await fixture(
      html`<gr-comment .comment=${c_hidden_prompt}></gr-comment>`
    );
    await element.updateComplete;

    const formattedText = queryAndAssert<GrFormattedText>(
      element,
      'gr-formatted-text'
    );
    await formattedText.updateComplete;

    const summary = queryAndAssert<HTMLElement>(
      formattedText,
      'summary.raw-markdown-summary'
    );
    summary.click();
    await formattedText.updateComplete;

    await visualDiff(element, 'gr-comment-hidden-prompt-raw-markdown');
    await visualDiffDarkTheme(element, 'gr-comment-hidden-prompt-raw-markdown');
  });
});
