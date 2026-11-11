/**
 * @license
 * Copyright (C) 2026 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-confirm-submit-dialog';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrConfirmSubmitDialog} from './gr-confirm-submit-dialog';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {testResolver} from '../../../test/common-test-setup';
import {relatedChangesModelToken} from '../../../models/change/related-changes-model';
import {configModelToken} from '../../../models/config/config-model';
import {
  createChange,
  createChangeConfig,
  createParsedChange,
  createServerInfo,
} from '../../../test/test-data-generators';
import {RepoName, TopicName} from '../../../types/common';

suite('gr-confirm-submit-dialog screenshot tests', () => {
  let element: GrConfirmSubmitDialog;

  setup(async () => {
    element = await fixture<GrConfirmSubmitDialog>(
      html`<gr-confirm-submit-dialog></gr-confirm-submit-dialog>`
    );
    element.action = {label: 'Submit'};
    element.initialised = true;
  });

  test('no topic', async () => {
    element.change = {
      ...createParsedChange(),
      subject: 'My change subject',
      revisions: {},
    };
    await element.updateComplete;

    await visualDiff(element, 'gr-confirm-submit-dialog-no-topic');
    await visualDiffDarkTheme(element, 'gr-confirm-submit-dialog-no-topic');
  });

  test('with topic, all changes submittable', async () => {
    const relatedChangesModel = testResolver(relatedChangesModelToken);
    relatedChangesModel.updateState({
      sameTopicChanges: [
        {
          ...createChange(),
          subject: 'Change A',
          project: 'repo/a' as RepoName,
          submittable: true,
        },
        {
          ...createChange(),
          subject: 'Change B',
          project: 'repo/b' as RepoName,
          submittable: true,
        },
      ],
    });
    element.change = {
      ...createParsedChange(),
      subject: 'My change subject',
      topic: 'my-feature-topic' as TopicName,
      revisions: {},
    };
    await element.updateComplete;

    await visualDiff(element, 'gr-confirm-submit-dialog-topic-all-submittable');
    await visualDiffDarkTheme(
      element,
      'gr-confirm-submit-dialog-topic-all-submittable'
    );
  });

  test('with topic, some changes not submittable', async () => {
    const relatedChangesModel = testResolver(relatedChangesModelToken);
    relatedChangesModel.updateState({
      sameTopicChanges: [
        {
          ...createChange(),
          subject: 'Change A',
          project: 'repo/a' as RepoName,
          submittable: true,
        },
        {
          ...createChange(),
          subject: 'Change B - not ready',
          project: 'repo/b' as RepoName,
          submittable: false,
        },
      ],
    });
    element.change = {
      ...createParsedChange(),
      subject: 'My change subject',
      topic: 'my-feature-topic' as TopicName,
      revisions: {},
    };
    await element.updateComplete;

    await visualDiff(element, 'gr-confirm-submit-dialog-topic-unsubmittable');
    await visualDiffDarkTheme(
      element,
      'gr-confirm-submit-dialog-topic-unsubmittable'
    );
  });

  test('whole topic enforced, all changes submittable', async () => {
    const configModel = testResolver(configModelToken);
    configModel.updateState({
      serverConfig: {
        ...createServerInfo(),
        change: {...createChangeConfig(), submit_whole_topic: true},
      },
    });
    const relatedChangesModel = testResolver(relatedChangesModelToken);
    relatedChangesModel.updateState({
      sameTopicChanges: [
        {
          ...createChange(),
          subject: 'Change A',
          project: 'repo/a' as RepoName,
          submittable: true,
        },
        {
          ...createChange(),
          subject: 'Change B',
          project: 'repo/b' as RepoName,
          submittable: true,
        },
      ],
    });
    element.change = {
      ...createParsedChange(),
      subject: 'My change subject',
      topic: 'my-feature-topic' as TopicName,
      revisions: {},
    };
    await element.updateComplete;

    await visualDiff(
      element,
      'gr-confirm-submit-dialog-topic-enforced-all-submittable'
    );
    await visualDiffDarkTheme(
      element,
      'gr-confirm-submit-dialog-topic-enforced-all-submittable'
    );
  });

  test('whole topic enforced, some changes not submittable', async () => {
    const configModel = testResolver(configModelToken);
    configModel.updateState({
      serverConfig: {
        ...createServerInfo(),
        change: {...createChangeConfig(), submit_whole_topic: true},
      },
    });
    const relatedChangesModel = testResolver(relatedChangesModelToken);
    relatedChangesModel.updateState({
      sameTopicChanges: [
        {
          ...createChange(),
          subject: 'Change A',
          project: 'repo/a' as RepoName,
          submittable: true,
        },
        {
          ...createChange(),
          subject: 'Change B - not ready',
          project: 'repo/b' as RepoName,
          submittable: false,
        },
      ],
    });
    element.change = {
      ...createParsedChange(),
      subject: 'My change subject',
      topic: 'my-feature-topic' as TopicName,
      revisions: {},
    };
    await element.updateComplete;

    await visualDiff(
      element,
      'gr-confirm-submit-dialog-topic-enforced-unsubmittable'
    );
    await visualDiffDarkTheme(
      element,
      'gr-confirm-submit-dialog-topic-enforced-unsubmittable'
    );
  });
});
