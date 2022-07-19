/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import {fixture, html} from '@open-wc/testing-helpers';
import {GrConfirmDeleteCommentDialog} from './gr-confirm-delete-comment-dialog';
import './gr-confirm-delete-comment-dialog';

suite('gr-confirm-delete-comment-dialog tests', () => {
  let element: GrConfirmDeleteCommentDialog;

  setup(async () => {
    element = await fixture(
      html`<gr-confirm-delete-comment-dialog></gr-confirm-delete-comment-dialog>`
    );
  });

  test('render', () => {
    // prettier and shadowDom string disagree about wrapping in <p> tag.
    expect(element).shadowDom.to
      .equal(/* prettier-ignore */ /* HTML */ `
      <gr-dialog confirm-label="Delete" role="dialog">
        <div class="header" slot="header">Delete Comment</div>
        <div class="main" slot="main">
          <p>
            This is an admin function. Please only use in exceptional
          circumstances.
          </p>
          <label for="messageInput"> Enter comment delete reason </label>
          <iron-autogrow-textarea
            aria-disabled="false"
            autocomplete="on"
            class="message"
            id="messageInput"
            placeholder="<Insert reasoning here>"
          >
          </iron-autogrow-textarea>
        </div>
      </gr-dialog>
    `);
  });
});
