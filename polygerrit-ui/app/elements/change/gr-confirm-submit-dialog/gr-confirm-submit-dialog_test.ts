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
  createThread,
} from '../../../test/test-data-generators';
import {EDIT, RepoName, TopicName} from '../../../types/common';
import {GrConfirmSubmitDialog} from './gr-confirm-submit-dialog';
import './gr-confirm-submit-dialog';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {assert, fixture, html} from '@open-wc/testing';
import {testResolver} from '../../../test/common-test-setup';
import {relatedChangesModelToken} from '../../../models/change/related-changes-model';

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

  test('renders topic changes list when submitWholeTopic is checked', async () => {
    const relatedChangesModel = testResolver(relatedChangesModelToken);
    relatedChangesModel.updateState({
      sameTopicChanges: [
        {
          ...createChange(),
          subject: 'Change A',
          project: 'repo/a' as RepoName,
        },
        {
          ...createChange(),
          subject: 'Change B',
          project: 'repo/b' as RepoName,
        },
      ],
    });
    element.action = {label: 'Submit'};
    element.change = {
      ...createParsedChange(),
      subject: 'my-subject',
      topic: 'my-topic' as TopicName,
      revisions: {},
    };
    await element.updateComplete;

    // List should not be visible before checking the checkbox
    assert.isNull(element.shadowRoot!.querySelector('.topicChangesList'));

    element.submitWholeTopic = true;
    await element.updateComplete;

    const items = element.shadowRoot!.querySelectorAll(
      '.topicChangeLine gr-related-change'
    );
    assert.equal(items.length, 2);
    assert.equal(
      items[0].querySelector('[slot="name"]')?.textContent?.trim(),
      'Change A'
    );
    assert.equal(
      items[1].querySelector('[slot="name"]')?.textContent?.trim(),
      'Change B'
    );
  });

  test('warning shown when topic has unsubmittable changes', async () => {
    const relatedChangesModel = testResolver(relatedChangesModelToken);
    relatedChangesModel.updateState({
      sameTopicChanges: [
        {
          ...createChange(),
          subject: 'Change A',
          project: 'repo/a' as RepoName,
          submittable: false,
        },
      ],
    });
    element.action = {label: 'Submit'};
    element.change = {
      ...createParsedChange(),
      subject: 'my-subject',
      topic: 'my-topic' as TopicName,
      revisions: {},
    };
    element.submitWholeTopic = false;
    await element.updateComplete;

    const warning = element.shadowRoot!.querySelector('.warningBox');
    assert.isNotNull(warning);
    assert.include(
      warning.textContent,
      'Some changes in this topic are not ready to submit.'
    );
  });

  test('no warning shown when all topic changes are submittable', async () => {
    const relatedChangesModel = testResolver(relatedChangesModelToken);
    relatedChangesModel.updateState({
      sameTopicChanges: [
        {
          ...createChange(),
          subject: 'Change A',
          project: 'repo/a' as RepoName,
          submittable: true,
        },
      ],
    });
    element.action = {label: 'Submit'};
    element.change = {
      ...createParsedChange(),
      subject: 'my-subject',
      topic: 'my-topic' as TopicName,
      revisions: {},
    };
    element.submitWholeTopic = true;
    await element.updateComplete;

    assert.isNull(element.shadowRoot!.querySelector('.warningBox'));
  });

  test('submit button disabled when submitting whole topic with unsubmittable changes', async () => {
    const relatedChangesModel = testResolver(relatedChangesModelToken);
    relatedChangesModel.updateState({
      sameTopicChanges: [
        {
          ...createChange(),
          subject: 'Change A',
          project: 'repo/a' as RepoName,
          submittable: false,
        },
      ],
    });
    element.action = {label: 'Submit'};
    element.change = {
      ...createParsedChange(),
      subject: 'my-subject',
      topic: 'my-topic' as TopicName,
      revisions: {},
    };
    element.submitWholeTopic = true;
    await element.updateComplete;

    const dialog = element.shadowRoot!.querySelector('gr-dialog') as GrDialog;
    assert.isTrue(dialog.disabled);
  });

  test('submit button enabled when all topic changes are submittable', async () => {
    const relatedChangesModel = testResolver(relatedChangesModelToken);
    relatedChangesModel.updateState({
      sameTopicChanges: [
        {
          ...createChange(),
          subject: 'Change A',
          project: 'repo/a' as RepoName,
          submittable: true,
        },
      ],
    });
    element.action = {label: 'Submit'};
    element.change = {
      ...createParsedChange(),
      subject: 'my-subject',
      topic: 'my-topic' as TopicName,
      revisions: {},
    };
    element.submitWholeTopic = true;
    await element.updateComplete;

    const dialog = element.shadowRoot!.querySelector('gr-dialog') as GrDialog;
    assert.isFalse(dialog.disabled);
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
