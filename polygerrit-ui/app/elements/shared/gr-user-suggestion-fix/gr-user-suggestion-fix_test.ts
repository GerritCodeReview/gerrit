/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-user-suggestion-fix';
import {fixture, html, assert} from '@open-wc/testing';
import {GrUserSuggetionFix} from './gr-user-suggestion-fix';
import {
  CommentModel,
  commentModelToken,
} from '../gr-comment-model/gr-comment-model';
import {wrapInProvider} from '../../../models/di-provider-element';
import {createComment} from '../../../test/test-data-generators';

suite('gr-user-suggestion-fix tests', () => {
  let element: GrUserSuggetionFix;

  setup(async () => {
    const commentModel = new CommentModel({comment: createComment()});
    element = (
      await fixture<GrUserSuggetionFix>(
        wrapInProvider(
          html` <gr-user-suggestion-fix>Hello World</gr-user-suggestion-fix> `,
          commentModelToken,
          commentModel
        )
      )
    ).querySelector<GrUserSuggetionFix>('gr-user-suggestion-fix')!;
    await element.updateComplete;
  });

  test('render', async () => {
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `<div class="header">
          <div class="title">
            <span>Suggested edit</span>
            <a
              href="https://gerrit-review.googlesource.com/Documentation/user-suggest-edits.html"
              rel="noopener noreferrer"
              target="_blank"
              ><gr-icon icon="help" title="read documentation"></gr-icon
            ></a>
          </div>
          <div class="copyButton">
            <gr-copy-clipboard
              hideinput=""
              multiline=""
              text="Hello World"
              copytargetname="Suggested edit"
            ></gr-copy-clipboard>
          </div>
          <div>
            <gr-button class="action show-fix" secondary="" flatten=""
              >Show edit</gr-button
            >
          </div>
        </div>
        <code>Hello World</code>`
    );
  });
});
