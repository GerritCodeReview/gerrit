/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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

import '../../../test/common-test-setup-karma';
import './gr-confirm-cherrypick-dialog';
import {queryAll, queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {GrConfirmCherrypickDialog} from './gr-confirm-cherrypick-dialog';
import {
  BranchName,
  ChangeId,
  ChangeInfo,
  ChangeInfoId,
  ChangeStatus,
  CommitId,
  GitRef,
  HttpMethod,
  NumericChangeId,
  RepoName,
  TopicName,
} from '../../../api/rest-api';
import {createChange, createRevision} from '../../../test/test-data-generators';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog.js';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {ProgressStatus} from '../../../constants/constants';

const basicFixture = fixtureFromElement('gr-confirm-cherrypick-dialog');

const CHERRY_PICK_TYPES = {
  SINGLE_CHANGE: 1,
  TOPIC: 2,
};
suite('gr-confirm-cherrypick-dialog tests', () => {
  let element: GrConfirmCherrypickDialog;

  setup(() => {
    stubRestApi('getRepoBranches').callsFake(input => {
      if (input.startsWith('test')) {
        return Promise.resolve([
          {
            ref: 'refs/heads/test-branch' as GitRef,
            revision: '67ebf73496383c6777035e374d2d664009e2aa5c',
            can_delete: true,
          },
        ]);
      } else {
        return Promise.resolve([]);
      }
    });
    element = basicFixture.instantiate();
    element.project = 'test-project' as RepoName;
  });

  test('with message missing newline', () => {
    element.changeStatus = ChangeStatus.MERGED;
    element.commitMessage = 'message';
    element.commitNum = '123' as CommitId;
    element.branch = 'master' as BranchName;
    flush();
    const expectedMessage = 'message\n(cherry picked from commit 123)';
    assert.equal(element.message, expectedMessage);
  });

  test('with merged change', () => {
    element.changeStatus = ChangeStatus.MERGED;
    element.commitMessage = 'message\n';
    element.commitNum = '123' as CommitId;
    element.branch = 'master' as BranchName;
    flush();
    const expectedMessage = 'message\n(cherry picked from commit 123)';
    assert.equal(element.message, expectedMessage);
  });

  test('with unmerged change', () => {
    element.changeStatus = ChangeStatus.NEW;
    element.commitMessage = 'message\n';
    element.commitNum = '123' as CommitId;
    element.branch = 'master' as BranchName;
    flush();
    const expectedMessage = 'message\n';
    assert.equal(element.message, expectedMessage);
  });

  test('with updated commit message', () => {
    element.changeStatus = ChangeStatus.NEW;
    element.commitMessage = 'message\n';
    element.commitNum = '123' as CommitId;
    element.branch = 'master' as BranchName;
    const myNewMessage = 'updated commit message';
    element.message = myNewMessage;
    flush();
    assert.equal(element.message, myNewMessage);
  });

  test('_getProjectBranchesSuggestions empty', async () => {
    const branches = await element._getProjectBranchesSuggestions('asdf');
    assert.isEmpty(branches);
  });

  suite('cherry pick topic', () => {
    const changes: ChangeInfo[] = [
      {
        ...createChange(),
        id: '1234' as ChangeInfoId,
        change_id: '12345678901234' as ChangeId,
        topic: 'T' as TopicName,
        subject: 'random',
        project: 'A' as RepoName,
        _number: 1 as NumericChangeId,
        revisions: {
          a: createRevision(),
        },
        current_revision: 'a' as CommitId,
      },
      {
        ...createChange(),
        id: '5678' as ChangeInfoId,
        change_id: '23456' as ChangeId,
        topic: 'T' as TopicName,
        subject: 'a'.repeat(100),
        project: 'B' as RepoName,
        _number: 2 as NumericChangeId,
        revisions: {
          a: createRevision(),
        },
        current_revision: 'a' as CommitId,
      },
    ];
    setup(async () => {
      element.updateChanges(changes);
      element._cherryPickType = CHERRY_PICK_TYPES.TOPIC;
      await flush();
    });

    test('cherry pick topic submit', async () => {
      element.branch = 'master' as BranchName;
      await flush();
      const executeChangeActionStub = stubRestApi(
        'executeChangeAction'
      ).returns(Promise.resolve(new Response()));
      MockInteractions.tap(
        queryAndAssert<GrDialog>(element, 'gr-dialog').confirmButton!
      );
      await flush();
      const args = executeChangeActionStub.args[0];
      assert.equal(args[0], 1);
      assert.equal(args[1], 'POST' as HttpMethod);
      assert.equal(args[2], '/cherrypick');
      assert.equal((args[4] as any).destination, 'master');
      assert.isTrue((args[4] as any).allow_conflicts);
      assert.isTrue((args[4] as any).allow_empty);
    });

    test('deselecting a change removes it from being cherry picked', async () => {
      const duplicateChangesStub = sinon.stub(
        element,
        'containsDuplicateProject'
      );
      element.branch = 'master' as BranchName;
      await flush();
      const executeChangeActionStub = stubRestApi(
        'executeChangeAction'
      ).returns(Promise.resolve(new Response()));
      const checkboxes = queryAll<HTMLInputElement>(
        element,
        'input[type="checkbox"]'
      );
      assert.equal(checkboxes.length, 2);
      assert.isTrue(checkboxes[0].checked);
      MockInteractions.tap(checkboxes[0]);
      MockInteractions.tap(
        queryAndAssert<GrDialog>(element, 'gr-dialog').confirmButton!
      );
      await flush();
      assert.equal(executeChangeActionStub.callCount, 1);
      assert.isTrue(duplicateChangesStub.called);
    });

    test('deselecting all change shows error message', async () => {
      element.branch = 'master' as BranchName;
      await flush();
      const executeChangeActionStub = stubRestApi(
        'executeChangeAction'
      ).returns(Promise.resolve(new Response()));
      const checkboxes = queryAll<HTMLInputElement>(
        element,
        'input[type="checkbox"]'
      );
      assert.equal(checkboxes.length, 2);
      MockInteractions.tap(checkboxes[0]);
      MockInteractions.tap(checkboxes[1]);
      MockInteractions.tap(
        queryAndAssert<GrDialog>(element, 'gr-dialog').confirmButton!
      );
      await flush();
      assert.equal(executeChangeActionStub.callCount, 0);
      assert.equal(
        queryAndAssert<HTMLElement>(element, '.error-message').innerText,
        'No change selected'
      );
    });

    test('_computeStatusClass', () => {
      assert.equal(
        element._computeStatusClass(
          {...createChange(), id: '1' as ChangeInfoId},
          {1: {status: ProgressStatus.RUNNING}}
        ),
        ''
      );
      assert.equal(
        element._computeStatusClass(
          {...createChange(), id: '1' as ChangeInfoId},
          {1: {status: ProgressStatus.FAILED}}
        ),
        'error'
      );
    });

    test('submit button is blocked while cherry picks is running', async () => {
      const confirmButton = queryAndAssert<GrDialog>(
        element,
        'gr-dialog'
      ).confirmButton;
      assert.isTrue(confirmButton!.hasAttribute('disabled'));
      element.branch = 'b' as BranchName;
      await flush();
      assert.isFalse(confirmButton!.hasAttribute('disabled'));
      element.updateStatus(changes[0], {status: ProgressStatus.RUNNING});
      await flush();
      assert.isTrue(confirmButton!.hasAttribute('disabled'));
    });
  });

  test('resetFocus', () => {
    const focusStub = sinon.stub(element.$.branchInput, 'focus');
    element.resetFocus();
    assert.isTrue(focusStub.called);
  });

  test('_getProjectBranchesSuggestions non-empty', async () => {
    const branches = await element._getProjectBranchesSuggestions(
      'test-branch'
    );
    assert.equal(branches.length, 1);
    assert.equal(branches[0].name, 'test-branch');
  });
});
