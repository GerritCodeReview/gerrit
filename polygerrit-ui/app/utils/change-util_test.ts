/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {ChangeStatus} from '../constants/constants';
import '../test/common-test-setup';
import {
  createAccountWithId,
  createChange,
  createChangeWithStatus,
  createRevisions,
  createServiceUserWithId,
} from '../test/test-data-generators';
import {
  AccountId,
  ChangeStates,
  CommitId,
  ListChangesOption,
  NumericChangeId,
  PatchSetNum,
} from '../types/common';
import {
  changeBaseURL,
  changeIsAbandoned,
  changeIsMerged,
  changeIsOpen,
  changePath,
  changeStatuses,
  hasHumanReviewer,
  isRemovableReviewer,
  listChangesOptionsToHex,
} from './change-util';

suite('change-util tests', () => {
  let originalCanonicalPath: string | undefined;

  suiteSetup(() => {
    originalCanonicalPath = window.CANONICAL_PATH;
    window.CANONICAL_PATH = '/r';
  });

  suiteTeardown(() => {
    window.CANONICAL_PATH = originalCanonicalPath;
  });

  test('changeBaseURL', () => {
    assert.deepEqual(
      changeBaseURL('test/project', 1 as NumericChangeId, '2' as PatchSetNum),
      '/r/changes/test%2Fproject~1/revisions/2'
    );
  });

  test('changePath', () => {
    assert.deepEqual(changePath(1 as NumericChangeId), '/r/c/1');
  });

  test('Open status', () => {
    const change = createChangeWithStatus(ChangeStatus.NEW, true);
    let statuses = changeStatuses(change);
    assert.deepEqual(statuses, []);

    change.submittable = false;
    statuses = changeStatuses(change, {mergeable: true});
    assert.deepEqual(statuses, [ChangeStates.ACTIVE]);

    change.submittable = true;
    statuses = changeStatuses(change, {mergeable: true});
    assert.deepEqual(statuses, [ChangeStates.READY_TO_SUBMIT]);

    change.mergeable = false;
    change.submittable = true;
    statuses = changeStatuses(change, {mergeable: false});
    assert.deepEqual(statuses, [ChangeStates.MERGE_CONFLICT]);

    change.mergeable = true;
    statuses = changeStatuses(change, {mergeable: true});
    assert.deepEqual(statuses, [ChangeStates.READY_TO_SUBMIT]);

    change.submittable = true;
    statuses = changeStatuses(change, {mergeable: false});
    assert.deepEqual(statuses, [ChangeStates.MERGE_CONFLICT]);
  });

  test('Merge conflict', () => {
    const change = createChangeWithStatus(ChangeStatus.NEW, false);
    const statuses = changeStatuses(change);
    assert.deepEqual(statuses, [ChangeStates.MERGE_CONFLICT]);
  });

  test('mergeable prop undefined', () => {
    const change = createChangeWithStatus(ChangeStatus.NEW);
    const statuses = changeStatuses(change);
    assert.deepEqual(statuses, []);
  });

  test('Merged status', () => {
    const change = createChangeWithStatus(ChangeStatus.MERGED);
    assert.deepEqual(changeStatuses(change), [ChangeStates.MERGED]);
    change.is_private = true;
    assert.deepEqual(changeStatuses(change), [ChangeStates.MERGED]);
    change.work_in_progress = true;
    assert.deepEqual(changeStatuses(change), [ChangeStates.MERGED]);
  });

  test('Merged and Reverted status', () => {
    const change = {
      ...createChange(),
      revisions: createRevisions(1),
      current_revision: 'rev1' as CommitId,
      status: ChangeStatus.MERGED,
    };
    assert.deepEqual(changeStatuses(change), [ChangeStates.MERGED]);
    assert.deepEqual(
      changeStatuses(change, {
        revertingChangeStatus: ChangeStatus.NEW,
        mergeable: true,
      }),
      [ChangeStates.MERGED, ChangeStates.REVERT_CREATED]
    );
    assert.deepEqual(
      changeStatuses(change, {
        revertingChangeStatus: ChangeStatus.MERGED,
        mergeable: true,
      }),
      [ChangeStates.MERGED, ChangeStates.REVERT_SUBMITTED]
    );
  });

  test('Abandoned status', () => {
    const change = createChangeWithStatus(ChangeStatus.ABANDONED, false);
    assert.deepEqual(changeStatuses(change), [ChangeStates.ABANDONED]);
    change.is_private = true;
    assert.deepEqual(changeStatuses(change), [ChangeStates.ABANDONED]);
    change.work_in_progress = true;
    assert.deepEqual(changeStatuses(change), [ChangeStates.ABANDONED]);
  });

  test('Revert status', () => {
    const change = {
      ...createChange(),
      revert_of: 123 as NumericChangeId,
    };
    assert.deepEqual(changeStatuses(change), [ChangeStates.REVERT]);
    change.is_private = true;
    assert.deepEqual(changeStatuses(change), [
      ChangeStates.REVERT,
      ChangeStates.PRIVATE,
    ]);
  });

  test('Revert that is submittable', () => {
    const change = {
      ...createChange(),
      revert_of: 123 as NumericChangeId,
      submittable: true,
    };
    assert.deepEqual(changeStatuses(change, {mergeable: true}), [
      ChangeStates.REVERT,
      ChangeStates.READY_TO_SUBMIT,
    ]);
  });

  test('Open status with private and wip', () => {
    const change = {
      ...createChange(),
      revisions: createRevisions(1),
      current_revision: 'rev1' as CommitId,
      status: ChangeStatus.NEW,
      mergeable: true,
      is_private: true,
      work_in_progress: true,
      labels: {},
    };
    const statuses = changeStatuses(change);
    assert.deepEqual(statuses, [ChangeStates.WIP, ChangeStates.PRIVATE]);
  });

  test('Merge conflict with private and wip', () => {
    const change = {
      ...createChange(),
      revisions: createRevisions(1),
      current_revision: 'rev1' as CommitId,
      status: ChangeStatus.NEW,
      mergeable: false,
      is_private: true,
      work_in_progress: true,
      labels: {},
    };
    const statuses = changeStatuses(change);
    assert.deepEqual(statuses, [
      ChangeStates.MERGE_CONFLICT,
      ChangeStates.WIP,
      ChangeStates.PRIVATE,
    ]);
  });

  test('hasHumanReviewer', () => {
    const owner = createAccountWithId(1);
    const change = {
      ...createChange(),
      _number: 1 as NumericChangeId,
      subject: 'Subject 1',
      owner,
      reviewers: {
        REVIEWER: [owner],
      },
    };
    assert.isFalse(hasHumanReviewer(change));

    change.reviewers.REVIEWER.push(createServiceUserWithId(2));
    assert.isFalse(hasHumanReviewer(change));

    change.reviewers.REVIEWER.push(createAccountWithId(3));
    assert.isTrue(hasHumanReviewer(change));
  });

  test('isRemovableReviewer', () => {
    let change = {
      ...createChange(),
      revisions: createRevisions(1),
      current_revision: 'rev1' as CommitId,
      status: ChangeStatus.NEW,
      mergeable: false,
      removable_reviewers: [{_account_id: 1 as AccountId}],
    };
    const reviewer = {_account_id: 1 as AccountId};

    assert.equal(isRemovableReviewer(change, reviewer), true);

    change = {
      ...createChange(),
      revisions: createRevisions(1),
      current_revision: 'rev1' as CommitId,
      status: ChangeStatus.NEW,
      mergeable: false,
      removable_reviewers: [{_account_id: 2 as AccountId}],
    };
    assert.equal(isRemovableReviewer(change, reviewer), false);
  });

  test('changeIsOpen', () => {
    const change = createChangeWithStatus(ChangeStatus.NEW, false);
    assert.isTrue(changeIsOpen(change));
    change.status = ChangeStatus.MERGED;
    assert.isFalse(changeIsOpen(change));
  });

  test('changeIsMerged', () => {
    const change = createChangeWithStatus(ChangeStatus.MERGED, false);
    assert.isTrue(changeIsMerged(change));
    change.status = ChangeStatus.NEW;
    assert.isFalse(changeIsMerged(change));
  });

  test('changeIsAbandoned', () => {
    const change = createChangeWithStatus(ChangeStatus.ABANDONED, false);
    assert.isTrue(changeIsAbandoned(change));
    change.status = ChangeStatus.NEW;
    assert.isFalse(changeIsAbandoned(change));
  });

  test('listChangesOptionsToHex', () => {
    const changeActionsHex = listChangesOptionsToHex(
      ListChangesOption.MESSAGES,
      ListChangesOption.ALL_REVISIONS
    );
    assert.equal(changeActionsHex, '204');
    const dashboardHex = listChangesOptionsToHex(
      ListChangesOption.LABELS,
      ListChangesOption.DETAILED_ACCOUNTS,
      ListChangesOption.SUBMIT_REQUIREMENTS
    );
    assert.equal(dashboardHex, '1000081');
  });
});
