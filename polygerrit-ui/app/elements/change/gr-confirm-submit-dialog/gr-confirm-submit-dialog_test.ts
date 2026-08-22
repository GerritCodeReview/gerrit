/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {
  createChange,
  createParsedChange,
  createRevision,
  createSubmittedTogetherInfo,
  createThread,
} from '../../../test/test-data-generators';
import {EDIT, NumericChangeId} from '../../../types/common';
import {GrConfirmSubmitDialog} from './gr-confirm-submit-dialog';
import './gr-confirm-submit-dialog';
import {assert, fixture, html} from '@open-wc/testing';

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

  test('computeOtherChangesWithUnresolvedComments', () => {
    element.change = {
      ...createParsedChange(),
      _number: 2 as NumericChangeId,
    };
    element.submittedTogether = {
      ...createSubmittedTogetherInfo(),
      changes: [
        {
          ...createChange(),
          _number: 1 as NumericChangeId,
          unresolved_comment_count: 2,
        },
        {
          ...createChange(),
          _number: 2 as NumericChangeId,
          unresolved_comment_count: 1,
        },
        {
          ...createChange(),
          _number: 3 as NumericChangeId,
          unresolved_comment_count: 0,
        },
        {
          ...createChange(),
          _number: 4 as NumericChangeId,
        },
      ],
    };

    const otherChanges = element.computeOtherChangesWithUnresolvedComments();
    assert.lengthOf(otherChanges, 1);
    assert.equal(otherChanges[0]._number, 1 as NumericChangeId);
  });

  test('computeOtherChangesUnresolvedCommentsWarning', () => {
    element.change = {
      ...createParsedChange(),
      _number: 2 as NumericChangeId,
    };
    element.submittedTogether = {
      ...createSubmittedTogetherInfo(),
      changes: [
        {
          ...createChange(),
          _number: 1 as NumericChangeId,
          unresolved_comment_count: 1,
        },
      ],
    };

    assert.equal(
      element.computeOtherChangesUnresolvedCommentsWarning(),
      'Heads Up! 1 other change in the submit group has unresolved comments:'
    );

    element.submittedTogether = {
      ...createSubmittedTogetherInfo(),
      changes: [
        {
          ...createChange(),
          _number: 1 as NumericChangeId,
          unresolved_comment_count: 1,
        },
        {
          ...createChange(),
          _number: 3 as NumericChangeId,
          unresolved_comment_count: 2,
        },
      ],
    };

    assert.equal(
      element.computeOtherChangesUnresolvedCommentsWarning(),
      'Heads Up! 2 other changes in the submit group have unresolved comments:'
    );
  });

  test('render with other changes in submit group having unresolved comments', async () => {
    element.action = {label: 'Submit'};
    element.change = {
      ...createParsedChange(),
      _number: 2 as NumericChangeId,
      subject: 'child-subject',
      revisions: {},
    };
    element.submittedTogether = {
      ...createSubmittedTogetherInfo(),
      changes: [
        {
          ...createChange(),
          _number: 1 as NumericChangeId,
          subject: 'parent-subject',
          unresolved_comment_count: 1,
        },
      ],
    };
    await element.updateComplete;

    const list = element.shadowRoot?.querySelector('.otherChangesList');
    assert.isNotNull(list);
    const item = list?.querySelector('li');
    assert.isNotNull(item);
    assert.include(item?.textContent ?? '', 'parent-subject');
    assert.include(item?.textContent ?? '', '(1 unresolved comment)');
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
