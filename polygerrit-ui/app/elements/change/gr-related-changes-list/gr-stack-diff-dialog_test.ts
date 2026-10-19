/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-stack-diff-dialog';
import {assert, fixture, html} from '@open-wc/testing';
import {GrStackDiffDialog} from './gr-stack-diff-dialog';
import {
  query,
  queryAndAssert,
  stubRestApi,
  waitUntil,
} from '../../../test/test-utils';
import {
  CommitId,
  PatchSetNumber,
  RelatedChangeAndCommitInfo,
  RepoName,
} from '../../../types/common';
import {FileInfoStatus} from '../../../api/rest-api';
import {DiffInfo} from '../../../types/diff';
import {
  createCommitInfoWithRequiredCommit,
  createParsedChange,
  createRelatedChangeAndCommitInfo,
  createRevision,
} from '../../../test/test-data-generators';
import * as sinon from 'sinon';

function relatedChange(sha: string, parentSha: string) {
  return {
    ...createRelatedChangeAndCommitInfo(),
    commit: {
      ...createCommitInfoWithRequiredCommit(sha as CommitId),
      subject: `Change ${sha}`,
      parents: [
        {commit: parentSha as CommitId, subject: `Parent ${parentSha}`},
      ],
    },
  };
}

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
      element.baseCommitId,
      'parentcommit1234567890abcdefabcdef12345'
    );
    assert.equal(
      element.targetCommitId,
      'abcdefabcdef1234567890abcdefabcdef123456'
    );
    assert.deepEqual(Object.keys(element.files), ['foo/bar.ts']);
    assert.equal(element.selectedFile, 'foo/bar.ts');
  });

  test('changing base or target commit reloads file list', async () => {
    await element.open();
    await element.updateComplete;
    getProjectCommitDiffStub.resetHistory();

    const baseSelect = queryAndAssert<HTMLElement & {value: string}>(
      element,
      '#baseSelect'
    );
    baseSelect.value = 'parent-sha';
    baseSelect.dispatchEvent(new CustomEvent('change'));
    await element.updateComplete;

    assert.isTrue(getProjectCommitDiffStub.calledOnce);
  });

  suite('smart relation chain handling', () => {
    setup(async () => {
      element.change = {
        ...createParsedChange(),
        revisions: {
          'current-sha': {...createRevision(), _number: 1 as PatchSetNumber},
        },
        current_revision: 'current-sha' as CommitId,
      };
      element.patchNum = 1 as PatchSetNumber;
      // Sorted from newest to oldest, like the REST API returns it.
      element.relatedChanges = [
        // Indirect relation: branched off an older patchset, so it is based on
        // a commit that is not part of the current chain.
        relatedChange('indirect-sha', 'indirect-base-sha'),
        relatedChange('child-sha', 'current-sha'),
        relatedChange('current-sha', 'connected-base-sha'),
      ];
      await element.updateComplete;
    });

    test('open initializes to connected chain, skipping indirect relations', async () => {
      await element.open();
      await element.updateComplete;

      // Target should be the top of the connected chain (child-sha), NOT indirect-sha
      assert.equal(element.targetCommitId, 'child-sha');
      // Base should be the base parent of the connected chain (connected-base-sha)
      assert.equal(element.baseCommitId, 'connected-base-sha');
    });

    test('options render indirect labels and all root parents', async () => {
      await element.open();
      await element.updateComplete;

      const baseParents = element.getBaseParents();
      assert.deepEqual(baseParents, [
        'connected-base-sha' as CommitId,
        'indirect-base-sha' as CommitId,
      ]);

      const baseOptions = element.renderBaseOptions();
      // Should have 2 base parents + 3 changes = 5 options
      assert.equal(baseOptions.length, 5);

      // Indirect relations should be identified correctly
      assert.isTrue(element.isIndirectRelation(element.relatedChanges[0]));
      assert.isFalse(element.isIndirectRelation(element.relatedChanges[1]));
      assert.isFalse(element.isIndirectRelation(element.relatedChanges[2]));
    });

    test('auto-adjusts base when target is changed to indirect relation', async () => {
      await element.open();
      await element.updateComplete;
      assert.equal(element.baseCommitId, 'connected-base-sha');
      assert.equal(element.targetCommitId, 'child-sha');

      const targetSelect = queryAndAssert<HTMLElement & {value: string}>(
        element,
        '#targetSelect'
      );
      targetSelect.value = 'indirect-sha';
      targetSelect.dispatchEvent(new CustomEvent('change'));
      await element.updateComplete;

      assert.equal(element.targetCommitId, 'indirect-sha');
      // Base should have auto-adjusted to indirect-base-sha
      assert.equal(element.baseCommitId, 'indirect-base-sha');
    });

    test('auto-adjusts target when base is changed to divergent chain', async () => {
      await element.open();
      await element.updateComplete;

      // Start by selecting indirect chain
      const targetSelect = queryAndAssert<HTMLElement & {value: string}>(
        element,
        '#targetSelect'
      );
      targetSelect.value = 'indirect-sha';
      targetSelect.dispatchEvent(new CustomEvent('change'));
      await element.updateComplete;
      assert.equal(element.baseCommitId, 'indirect-base-sha');
      assert.equal(element.targetCommitId, 'indirect-sha');

      // Now switch base to connected chain
      const baseSelect = queryAndAssert<HTMLElement & {value: string}>(
        element,
        '#baseSelect'
      );
      baseSelect.value = 'connected-base-sha';
      baseSelect.dispatchEvent(new CustomEvent('change'));
      await element.updateComplete;

      assert.equal(element.baseCommitId, 'connected-base-sha');
      // Target should have auto-adjusted to child-sha
      assert.equal(element.targetCommitId, 'child-sha');
    });

    test('renders reset button on error and restores connected chain', async () => {
      await element.open();
      await element.updateComplete;

      getProjectCommitDiffStub.rejects(
        new Error('Commits are not in ancestor/descendant relationship')
      );

      // Trigger a reload that causes an error
      const targetSelect = queryAndAssert<HTMLElement & {value: string}>(
        element,
        '#targetSelect'
      );
      targetSelect.value = 'indirect-sha';
      targetSelect.dispatchEvent(new CustomEvent('change'));
      await waitUntil(() => !!query(element, '#resetChainButton'));

      const resetBtn = queryAndAssert<HTMLElement>(
        element,
        '#resetChainButton'
      );
      assert.isOk(resetBtn);

      // Reset stub to succeed
      getProjectCommitDiffStub.resolves({'foo/bar.ts': {lines_inserted: 1}});
      resetBtn.click();
      await waitUntil(() => element.targetCommitId === 'child-sha');
      await element.updateComplete;

      assert.equal(element.targetCommitId, 'child-sha');
      assert.equal(element.baseCommitId, 'connected-base-sha');
    });
  });

  suite('connected chain detection', () => {
    function setChain(relatedChanges: RelatedChangeAndCommitInfo[]) {
      element.change = {
        ...createParsedChange(),
        revisions: {
          'current-sha': {...createRevision(), _number: 1 as PatchSetNumber},
        },
        current_revision: 'current-sha' as CommitId,
      };
      element.patchNum = 1 as PatchSetNumber;
      element.relatedChanges = relatedChanges;
      return element.updateComplete;
    }

    test('indirect relation sorted below the current change is not connected', async () => {
      // The indirect relation is older than the current change, so it cannot
      // be detected by position in the list alone.
      await setChain([
        relatedChange('child-sha', 'current-sha'),
        relatedChange('current-sha', 'ancestor-sha'),
        relatedChange('ancestor-sha', 'root-sha'),
        relatedChange('stale-sha', 'other-root-sha'),
      ]);

      assert.deepEqual([...element.getConnectedRevisions()].sort(), [
        'ancestor-sha',
        'child-sha',
        'current-sha',
      ]);
    });

    test('chain branching off the same root parent is not connected', async () => {
      await setChain([
        relatedChange('sibling-sha', 'root-sha'),
        relatedChange('current-sha', 'root-sha'),
      ]);

      // Both chains share the root parent, but they are not ancestors of each
      // other, so diffing them would be rejected by the backend.
      assert.deepEqual([...element.getConnectedRevisions()], ['current-sha']);
      assert.isFalse(
        element.isAncestor('sibling-sha' as CommitId, 'current-sha' as CommitId)
      );
    });

    test('connected revisions are empty without a current change', async () => {
      element.change = undefined;
      element.relatedChanges = [relatedChange('a-sha', 'b-sha')];
      await element.updateComplete;

      assert.equal(element.getConnectedRevisions().size, 0);
      // Without a connected chain nothing is marked as indirect.
      assert.isFalse(element.isIndirectRelation(element.relatedChanges[0]));
    });
  });
});
