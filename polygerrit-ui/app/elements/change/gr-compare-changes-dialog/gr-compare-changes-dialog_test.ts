/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert, fixture, html} from '@open-wc/testing';
import '../../../test/common-test-setup';
import {
  createCommitInfoWithRequiredCommit,
  createParsedChange,
  createRelatedChangeAndCommitInfo,
} from '../../../test/test-data-generators';
import {
  CommitId,
  NumericChangeId,
  RelatedChangeAndCommitInfo,
  RepoName,
} from '../../../types/common';
import './gr-compare-changes-dialog';
import {GrCompareChangesDialog} from './gr-compare-changes-dialog';
import {stubRestApi} from '../../../test/test-utils';

suite('gr-compare-changes-dialog tests', () => {
  let element: GrCompareChangesDialog;

  setup(async () => {
    element = await fixture(
      html`<gr-compare-changes-dialog></gr-compare-changes-dialog>`
    );
  });

  function createRelatedChange(
    changeNum: number,
    commitId: string,
    subject: string,
    parentCommitId?: string,
    revisionNumber = 1,
    currentRevisionNumber = 1
  ): RelatedChangeAndCommitInfo {
    const commit = createCommitInfoWithRequiredCommit(commitId);
    commit.subject = subject;
    if (parentCommitId) {
      commit.parents = [
        {commit: parentCommitId as CommitId, subject: 'Parent subject'},
      ];
    }
    return {
      ...createRelatedChangeAndCommitInfo(),
      _change_number: changeNum as NumericChangeId,
      _revision_number: revisionNumber,
      _current_revision_number: currentRevisionNumber,
      commit,
    };
  }

  test('renders', async () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <dialog id="compareModal" tabindex="-1">
          <gr-dialog cancel-label="Close" confirm-label="" role="dialog">
            <div class="header" slot="header">
              <div class="change-picker">
                <gr-dropdown-list></gr-dropdown-list>
                <span class="arrow">→</span>
                <gr-dropdown-list></gr-dropdown-list>
              </div>
            </div>
            <div class="main" slot="main">
              <div class="empty-state">No files changed</div>
            </div>
          </gr-dialog>
        </dialog>
      `
    );
  });

  suite('buildDropdownOptions', () => {
    test('children on top, parents below, base at bottom', async () => {
      // Create a chain: child -> parent -> grandparent
      // relatedChanges[0] is newest (child), last is oldest (grandparent)
      const grandparent = createRelatedChange(
        100,
        'grandparent-commit',
        'Grandparent change',
        'base-commit' // has a parent
      );
      const parent = createRelatedChange(
        101,
        'parent-commit',
        'Parent change',
        'grandparent-commit'
      );
      const child = createRelatedChange(
        102,
        'child-commit',
        'Child change',
        'parent-commit'
      );

      element.relatedChanges = [child, parent, grandparent];
      element.baseCommit = 'base-commit' as CommitId;

      // Access private method for testing
      (element as any).buildDropdownOptions();

      // Left options should have: child, parent, grandparent, Base
      assert.equal(element.leftOptions.length, 4);
      assert.equal(element.leftOptions[0].text, 'Child change');
      assert.equal(element.leftOptions[1].text, 'Parent change');
      assert.equal(element.leftOptions[2].text, 'Grandparent change');
      assert.equal(element.leftOptions[3].text, 'Base');

      // Right options should have: child, parent, grandparent (no Base)
      assert.equal(element.rightOptions.length, 3);
      assert.equal(element.rightOptions[0].text, 'Child change');
      assert.equal(element.rightOptions[1].text, 'Parent change');
      assert.equal(element.rightOptions[2].text, 'Grandparent change');
    });

    test('no base option when no parent exists (root commit)', async () => {
      const rootChange = createRelatedChange(
        100,
        'root-commit',
        'Root change'
        // no parent
      );

      element.relatedChanges = [rootChange];
      element.baseCommit = undefined;

      (element as any).buildDropdownOptions();

      // Left options should only have the change, no Base
      assert.equal(element.leftOptions.length, 1);
      assert.equal(element.leftOptions[0].text, 'Root change');

      // Right options same
      assert.equal(element.rightOptions.length, 1);
    });

    test('shows patchset info in bottomText', async () => {
      const change = createRelatedChange(
        123,
        'test-commit',
        'Test subject',
        'parent-commit',
        2, // revision_number
        2 // current_revision_number
      );

      element.relatedChanges = [change];
      element.baseCommit = 'parent-commit' as CommitId;

      (element as any).buildDropdownOptions();

      assert.equal(element.leftOptions[0].bottomText, 'Change 123 PS2');
    });

    test('shows outdated indicator for old patchsets', async () => {
      const change = createRelatedChange(
        123,
        'test-commit',
        'Test subject',
        'parent-commit',
        1, // revision_number (old)
        3 // current_revision_number (newer exists)
      );

      element.relatedChanges = [change];
      element.baseCommit = 'parent-commit' as CommitId;

      (element as any).buildDropdownOptions();

      assert.equal(
        element.leftOptions[0].bottomText,
        'Change 123 PS1 (outdated)'
      );
    });

    test('truncates long subjects in triggerText', async () => {
      const longSubject =
        'This is a very long commit subject that exceeds thirty characters';
      const change = createRelatedChange(
        123,
        'test-commit',
        longSubject,
        'parent-commit'
      );

      element.relatedChanges = [change];
      element.baseCommit = 'parent-commit' as CommitId;

      (element as any).buildDropdownOptions();

      // triggerText should be truncated to 30 chars + ellipsis + patchset
      assert.equal(
        element.leftOptions[0].triggerText,
        'This is a very long commit sub… (PS1)'
      );
      // But full text should remain intact
      assert.equal(element.leftOptions[0].text, longSubject);
    });
  });

  suite('open()', () => {
    test('sets default selection to BASE -> newest change', async () => {
      stubRestApi('getProjectDiffFiles').resolves({});

      const parent = createRelatedChange(
        100,
        'parent-commit',
        'Parent change',
        'base-commit'
      );
      const child = createRelatedChange(
        101,
        'child-commit',
        'Child change',
        'parent-commit'
      );

      await element.open({
        project: 'test-project' as RepoName,
        relatedChanges: [child, parent],
        currentChange: createParsedChange(),
      });

      assert.equal(element.selectedLeft, 'BASE');
      assert.equal(element.selectedRight, 'child-commit');
    });

    test('falls back to oldest change when no base exists', async () => {
      stubRestApi('getProjectDiffFiles').resolves({});

      const rootChange = createRelatedChange(
        100,
        'root-commit',
        'Root change'
        // no parent
      );
      const child = createRelatedChange(
        101,
        'child-commit',
        'Child change',
        'root-commit'
      );

      await element.open({
        project: 'test-project' as RepoName,
        relatedChanges: [child, rootChange],
        currentChange: createParsedChange(),
      });

      // Should fall back to oldest change since no base
      assert.equal(element.selectedLeft, 'root-commit');
      assert.equal(element.selectedRight, 'child-commit');
    });
  });

  suite('getStatusClass', () => {
    test('returns correct class for file status', () => {
      assert.equal((element as any).getStatusClass('A'), 'added');
      assert.equal((element as any).getStatusClass('D'), 'deleted');
      assert.equal((element as any).getStatusClass('R'), 'renamed');
      assert.equal((element as any).getStatusClass('M'), 'modified');
      assert.equal((element as any).getStatusClass(''), 'modified');
    });
  });

  suite('loading files', () => {
    test('loads and sorts files alphabetically', async () => {
      const filesResponse = {
        'src/main/java/Foo.java': {status: 'M', lines_inserted: 10},
        'src/main/java/Bar.java': {status: 'A', lines_inserted: 20},
        'README.md': {status: 'M', lines_deleted: 5},
        '/COMMIT_MSG': {status: 'M'}, // Should be filtered out
      } as any;
      stubRestApi('getProjectDiffFiles').resolves(filesResponse);

      element.project = 'test-project' as RepoName;
      element.oldCommit = 'old-commit' as CommitId;
      element.newCommit = 'new-commit' as CommitId;

      await (element as any).loadFiles();

      // Should filter out /COMMIT_MSG and sort alphabetically
      assert.equal(element.files.length, 3);
      assert.equal(element.files[0].path, 'README.md');
      assert.equal(element.files[1].path, 'src/main/java/Bar.java');
      assert.equal(element.files[2].path, 'src/main/java/Foo.java');
    });

    test('does not load if project or commits are missing', async () => {
      const stub = stubRestApi('getProjectDiffFiles').resolves({});

      element.project = undefined;
      await (element as any).loadFiles();
      assert.isFalse(stub.called);

      element.project = 'test-project' as RepoName;
      element.oldCommit = undefined;
      await (element as any).loadFiles();
      assert.isFalse(stub.called);
    });
  });
});
