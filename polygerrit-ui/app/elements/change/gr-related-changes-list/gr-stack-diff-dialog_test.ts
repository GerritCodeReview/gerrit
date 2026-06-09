/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-stack-diff-dialog';
import {assert, fixture, html} from '@open-wc/testing';
import {GrStackDiffDialog} from './gr-stack-diff-dialog';
import {stubRestApi} from '../../../test/test-utils';
import {CommitId, RepoName} from '../../../types/common';
import {FileInfoStatus} from '../../../api/rest-api';
import {DiffInfo} from '../../../types/diff';
import {
  createCommitInfoWithRequiredCommit,
  createRelatedChangeAndCommitInfo,
} from '../../../test/test-data-generators';
import * as sinon from 'sinon';

suite('gr-stack-diff-dialog tests', () => {
  let element: GrStackDiffDialog;
  let getProjectCommitDiffStub: sinon.SinonStub;

  setup(async () => {
    getProjectCommitDiffStub = stubRestApi('getProjectCommitDiff').resolves({
      'foo/bar.ts': {
        status: FileInfoStatus.ADDED,
        lines_inserted: 10,
        lines_deleted: 0,
        size_delta: 100,
        size: 100,
      },
    });

    stubRestApi('getProjectCommitFileDiff').resolves({
      meta_a: {
        name: 'foo/bar.ts',
        content_type: 'text/typescript',
        lines: 10,
      },
      meta_b: {
        name: 'foo/bar.ts',
        content_type: 'text/typescript',
        lines: 12,
      },
      content: [],
    } as unknown as DiffInfo);

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
          subject: 'Related change 1',
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

  test('open initializes baseCommitId and targetCommitId', async () => {
    await element.open();
    await element.updateComplete;

    assert.equal(
      element['baseCommitId'],
      'parentcommit1234567890abcdefabcdef12345'
    );
    assert.equal(
      element['targetCommitId'],
      'abcdefabcdef1234567890abcdefabcdef123456'
    );
    assert.deepEqual(Object.keys(element['files']), ['foo/bar.ts']);
    assert.equal(element['selectedFile'], 'foo/bar.ts');
  });

  test('changing base or target commit reloads file list', async () => {
    await element.open();
    await element.updateComplete;
    getProjectCommitDiffStub.resetHistory();

    const baseSelect = element.shadowRoot?.querySelector(
      '#baseSelect'
    ) as HTMLElement & {value: string};
    assert.isDefined(baseSelect);
    baseSelect.value = 'parent-sha';
    baseSelect.dispatchEvent(new CustomEvent('change'));
    await element.updateComplete;

    assert.isTrue(getProjectCommitDiffStub.calledOnce);
  });
});
