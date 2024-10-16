/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-user-suggestion-fix';
import {fixture, html, assert} from '@open-wc/testing';
import {GrUserSuggestionsFix} from './gr-user-suggestion-fix';
import {
  CommentModel,
  commentModelToken,
} from '../gr-comment-model/gr-comment-model';
import {wrapInProvider} from '../../../models/di-provider-element';
import {createComment} from '../../../test/test-data-generators';
import {getAppContext} from '../../../services/app-context';

suite('gr-user-suggestion-fix tests', () => {
  let element: GrUserSuggestionsFix;

  setup(async () => {
    const commentModel = new CommentModel(getAppContext().restApiService);
    commentModel.updateState({
      comment: createComment(),
    });
    element = (
      await fixture<GrUserSuggestionsFix>(
        wrapInProvider(
          html` <gr-user-suggestion-fix>Hello World</gr-user-suggestion-fix> `,
          commentModelToken,
          commentModel
        )
      )
    ).querySelector<GrUserSuggestionsFix>('gr-user-suggestion-fix')!;
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
              href="/Documentation/user-suggest-edits.html"
              rel="noopener noreferrer"
              target="_blank"
              ><gr-icon icon="help" title="read documentation"></gr-icon
            ></a>
          </div>
          <div class="copyButton">
            <gr-copy-clipboard
              buttontitle="Copy Suggested edit to clipboard"
              hideinput=""
              multiline=""
              text="Hello World"
              copytargetname="Suggested edit"
            ></gr-copy-clipboard>
          </div>
          <div>
            <gr-button
              aria-disabled="false"
              class="action show-fix"
              secondary=""
              role="button"
              tabindex="0"
              flatten=""
              >Show edit</gr-button
            ><gr-button
              aria-disabled="true"
              disabled=""
              class="action show-fix"
              secondary=""
              role="button"
              tabindex="-1"
              flatten=""
              title="Fix is still loading ..."
              >Apply edit</gr-button
            >
          </div>
        </div>
        <gr-suggestion-diff-preview></gr-suggestion-diff-preview>`
    );
  });
});
