/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {ChangeStatus} from '../constants/constants';
import {ChangeStates} from '../elements/shared/gr-change-status/gr-change-status';
import '../test/common-test-setup-karma';
import {createChange, createRevisions} from '../test/test-data-generators';
import {
  AccountId,
  CommitId,
  NumericChangeId,
  PatchSetNum,
} from '../types/common';
import {
  changeBaseURL,
  changePath,
  changeStatuses,
  isRemovableReviewer,
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
    const change = {
      ...createChange(),
      revisions: createRevisions(1),
      current_revision: 'rev1' as CommitId,
      mergeable: true,
    };
    let statuses = changeStatuses(change);
    assert.deepEqual(statuses, []);

    change.submittable = false;
    statuses = changeStatuses(change, {mergeable: true, submitEnabled: false});
    assert.deepEqual(statuses, [ChangeStates.ACTIVE]);

    // With no missing labels but no submitEnabled option.
    change.submittable = true;
    statuses = changeStatuses(change, {mergeable: true, submitEnabled: false});
    assert.deepEqual(statuses, [ChangeStates.ACTIVE]);

    // Without missing labels and enabled submit
    statuses = changeStatuses(change, {mergeable: true, submitEnabled: true});
    assert.deepEqual(statuses, [ChangeStates.READY_TO_SUBMIT]);

    change.mergeable = false;
    change.submittable = true;
    statuses = changeStatuses(change, {mergeable: false, submitEnabled: false});
    assert.deepEqual(statuses, [ChangeStates.MERGE_CONFLICT]);

    change.mergeable = true;
    statuses = changeStatuses(change, {mergeable: true, submitEnabled: true});
    assert.deepEqual(statuses, [ChangeStates.READY_TO_SUBMIT]);

    change.submittable = true;
    statuses = changeStatuses(change, {mergeable: false, submitEnabled: false});
    assert.deepEqual(statuses, [ChangeStates.MERGE_CONFLICT]);
  });

  test('Merge conflict', () => {
    const change = {
      ...createChange(),
      revisions: createRevisions(1),
      current_revision: 'rev1' as CommitId,
      status: ChangeStatus.NEW,
      mergeable: false,
    };
    const statuses = changeStatuses(change);
    assert.deepEqual(statuses, [ChangeStates.MERGE_CONFLICT]);
  });

  test('mergeable prop undefined', () => {
    const change = {
      ...createChange(),
      revisions: createRevisions(1),
      current_revision: 'rev1' as CommitId,
      status: ChangeStatus.NEW,
    };
    const statuses = changeStatuses(change);
    assert.deepEqual(statuses, []);
  });

  test('Merged status', () => {
    const change = {
      ...createChange(),
      revisions: createRevisions(1),
      current_revision: 'rev1' as CommitId,
      status: ChangeStatus.MERGED,
    };
    const statuses = changeStatuses(change);
    assert.deepEqual(statuses, [ChangeStates.MERGED]);
  });

  test('Abandoned status', () => {
    const change = {
      ...createChange(),
      revisions: createRevisions(1),
      current_revision: 'rev1' as CommitId,
      status: ChangeStatus.ABANDONED,
      mergeable: false,
    };
    const statuses = changeStatuses(change);
    assert.deepEqual(statuses, [ChangeStates.ABANDONED]);
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
    const change = {
      status: ChangeStatus.OPEN,
    };
    assert.isTrue(changeIsOpen(change));
    change.status = ChangeStatus.MERGED;
    assert.isFalse(changeIsOpen(change));
  });

  test('changeIsMerged', () => {
    const change = {
      status: ChangeStatus.MERGED,
    };
    assert.isTrue(changeIsMerged(change));
    change.status = ChangeStatus.OPEN;
    assert.isFalse(changeIsMerged(change));
  });

  test('changeIsAbandoned', () => {
    const change = {
      status: ChangeStatus.ABANDONED,
    };
    assert.isTrue(changeIsAbandoned(change));
    change.status = ChangeStatus.OPEN;
    assert.isFalse(changeIsAbandoned(change));
  });
});
