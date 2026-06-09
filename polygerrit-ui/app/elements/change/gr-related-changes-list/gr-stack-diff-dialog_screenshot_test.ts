/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-stack-diff-dialog';
import {assert, fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrStackDiffDialog} from './gr-stack-diff-dialog';
import {stubRestApi, visualDiffDarkTheme} from '../../../test/test-utils';
import {CommitId, RepoName} from '../../../types/common';
import {DiffInfo} from '../../../types/diff';
import {FileInfoStatus} from '../../../api/rest-api';
import {
  createCommitInfoWithRequiredCommit,
  createRelatedChangeAndCommitInfo,
} from '../../../test/test-data-generators';

suite('gr-stack-diff-dialog screenshot tests', () => {
  let element: GrStackDiffDialog;

  setup(async () => {
    stubRestApi('getProjectCommitDiff').resolves({
      'foo/bar/baz.ts': {
        status: FileInfoStatus.ADDED,
        lines_inserted: 10,
        lines_deleted: 0,
        size_delta: 100,
        size: 100,
      },
      'foo/qux.ts': {
        status: FileInfoStatus.MODIFIED,
        lines_inserted: 5,
        lines_deleted: 3,
        size_delta: 20,
        size: 150,
      },
      'foo/deleted.ts': {
        status: FileInfoStatus.DELETED,
        lines_inserted: 0,
        lines_deleted: 15,
        size_delta: -200,
        size: 0,
      },
    });

    stubRestApi('getProjectCommitFileDiff').resolves({
      meta_a: {
        name: 'foo/qux.ts',
        content_type: 'text/typescript',
        lines: 10,
      },
      meta_b: {
        name: 'foo/qux.ts',
        content_type: 'text/typescript',
        lines: 12,
      },
      content: [
        {ab: ['// Unchanged line 1', '// Unchanged line 2']},
        {
          a: ['// Deleted line 3'],
          b: ['// Inserted line 3', '// Another insertion'],
        },
        {ab: ['// Unchanged line 4']},
      ],
    } as DiffInfo);

    element = await fixture<GrStackDiffDialog>(
      html`<gr-stack-diff-dialog></gr-stack-diff-dialog>`
    );
    element.repo = 'test-repo' as RepoName;
    element.relatedChanges = [
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            'abcdefabcdef1234567890abcdefabcdef123456' as CommitId
          ),
          subject: 'First related change subject',
        },
      },
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            '7890abcdefabcdef1234567890abcdefabcdef12' as CommitId
          ),
          subject: 'Second related change subject',
          parents: [
            {
              commit: 'parentcommit1234567890abcdefabcdef12345' as CommitId,
              subject: 'Parent subject',
            },
          ],
        },
      },
    ];
    await element.updateComplete;
  });

  test('dialog screenshots', async () => {
    const openPromise = element.open();
    await element.updateComplete;
    await openPromise;
    await element.updateComplete;

    const dialogElement = element.shadowRoot?.querySelector('dialog');
    assert.isDefined(dialogElement);
    await visualDiff(dialogElement!, 'gr-stack-diff-dialog');
    await visualDiffDarkTheme(dialogElement!, 'gr-stack-diff-dialog');
  });
});
