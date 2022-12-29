/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {fixture, html, assert} from '@open-wc/testing';
import {GrConfirmDeleteCommentDialog} from './gr-confirm-delete-comment-dialog';
import './gr-confirm-delete-comment-dialog';
import {GrDialog} from '../gr-dialog/gr-dialog';

suite('gr-confirm-delete-comment-dialog tests', () => {
  let element: GrConfirmDeleteCommentDialog;

  setup(async () => {
    element = await fixture(
      html`<gr-confirm-delete-comment-dialog></gr-confirm-delete-comment-dialog>`
    );
  });

  test('render', async () => {
    element.message = 'Just cause';
    await element.updateComplete;

    // prettier and shadowDom string disagree about wrapping in <p> tag.
    assert.shadowDom.equal(
      element,
      /* prettier-ignore */ /* HTML */ `
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
    `
    );
  });

  test('dialog is disabled when message is empty', async () => {
    element.message = '';
    await element.updateComplete;

    assert.isTrue(
      (element.shadowRoot!.querySelector('gr-dialog') as GrDialog).disabled
    );
  });
});
