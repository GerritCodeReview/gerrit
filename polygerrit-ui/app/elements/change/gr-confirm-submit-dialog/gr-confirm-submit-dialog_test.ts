/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {
  createParsedChange,
  createRevision,
  createThread,
} from '../../../test/test-data-generators';
import {EDIT} from '../../../types/common';
import {GrConfirmSubmitDialog} from './gr-confirm-submit-dialog';
import './gr-confirm-submit-dialog';
import {fixture, html, assert} from '@open-wc/testing';

suite('gr-confirm-submit-dialog tests', () => {
  let element: GrConfirmSubmitDialog;

  setup(async () => {
    element = await fixture(
      html`<gr-confirm-submit-dialog></gr-confirm-submit-dialog>`
    );
    element.initialised = true;
  });

  test('render', async () => {
    element.action = {label: 'my-label'};
    element.change = {
      ...createParsedChange(),
      subject: 'my-subject',
      revisions: {},
    };
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-dialog
          confirm-label="Continue"
          confirm-on-enter=""
          id="dialog"
          role="dialog"
        >
          <div class="header" slot="header">my-label</div>
          <div class="main" slot="main">
            <gr-endpoint-decorator name="confirm-submit-change">
              <p>
                Ready to submit "
                <strong> my-subject </strong>
                "?
              </p>
              <gr-endpoint-param name="change"> </gr-endpoint-param>
              <gr-endpoint-param name="action"> </gr-endpoint-param>
            </gr-endpoint-decorator>
          </div>
        </gr-dialog>
      `
    );
  });

  test('computeUnresolvedCommentsWarning', () => {
    element.change = {...createParsedChange()};
    element.unresolvedThreads = [createThread()];
    assert.equal(
      element.computeUnresolvedCommentsWarning(),
      'Heads Up! 1 unresolved comment.'
    );

    element.unresolvedThreads = [...element.unresolvedThreads, createThread()];
    assert.equal(
      element.computeUnresolvedCommentsWarning(),
      'Heads Up! 2 unresolved comments.'
    );
  });

  test('computeHasChangeEdit', () => {
    element.change = {
      ...createParsedChange(),
      revisions: {
        d442ff05d6c4f2a3af0eeca1f67374b39f9dc3d8: createRevision(EDIT),
      },
      unresolved_comment_count: 0,
    };

    assert.isTrue(element.computeHasChangeEdit());

    element.change = {
      ...createParsedChange(),
      revisions: {
        d442ff05d6c4f2a3af0eeca1f67374b39f9dc3d8: createRevision(2),
      },
    };
    assert.isFalse(element.computeHasChangeEdit());
  });
});
