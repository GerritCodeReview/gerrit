/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import {
  createParsedChange,
  createRevision,
  createThread,
} from '../../../test/test-data-generators';
import {queryAndAssert} from '../../../test/test-utils';
import {EDIT, PatchSetNum} from '../../../types/common';
import {GrConfirmSubmitDialog} from './gr-confirm-submit-dialog';
import './gr-confirm-submit-dialog';

const basicFixture = fixtureFromElement('gr-confirm-submit-dialog');

suite('gr-confirm-submit-dialog tests', () => {
  let element: GrConfirmSubmitDialog;

  setup(() => {
    element = basicFixture.instantiate();
    element.initialised = true;
  });

  test('display', async () => {
    element.action = {label: 'my-label'};
    element.change = {
      ...createParsedChange(),
      subject: 'my-subject',
      revisions: {},
    };
    await element.updateComplete;
    const header = queryAndAssert(element, '.header');
    assert.equal(header.textContent!.trim(), 'my-label');

    const message = queryAndAssert(element, '.main p');
    assert.isNotEmpty(message.textContent);
    assert.notEqual(message.textContent!.indexOf('my-subject'), -1);
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
        d442ff05d6c4f2a3af0eeca1f67374b39f9dc3d8: {
          ...createRevision(),
          _number: EDIT,
        },
      },
      unresolved_comment_count: 0,
    };

    assert.isTrue(element.computeHasChangeEdit());

    element.change = {
      ...createParsedChange(),
      revisions: {
        d442ff05d6c4f2a3af0eeca1f67374b39f9dc3d8: {
          ...createRevision(),
          _number: 2 as PatchSetNum,
        },
      },
    };
    assert.isFalse(element.computeHasChangeEdit());
  });
});
