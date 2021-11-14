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

import '../../../test/common-test-setup-karma';
import './gr-create-change-dialog';
import {GrCreateChangeDialog} from './gr-create-change-dialog';
import {BranchName, GitRef, RepoName} from '../../../types/common';
import {InheritedBooleanInfoConfiguredValue} from '../../../constants/constants';
import {createChange} from '../../../test/test-data-generators';
import {queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {IronAutogrowTextareaElement} from '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';

const basicFixture = fixtureFromElement('gr-create-change-dialog');

suite('gr-create-change-dialog tests', () => {
  let element: GrCreateChangeDialog;

  setup(async () => {
    stubRestApi('getRepoBranches').callsFake((input: string) => {
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
    await element.updateComplete;
    element.repoName = 'test-repo' as RepoName;
  });

  test('new change created with default', async () => {
    const configInputObj = {
      branch: 'test-branch',
      subject: 'first change created with polygerrit ui',
      topic: 'test-topic',
      is_private: false,
      work_in_progress: true,
    };

    const saveStub = stubRestApi('createChange').returns(
      Promise.resolve(createChange())
    );

    element.branch = 'test-branch' as BranchName;
    element.topic = 'test-topic';
    element.subject = 'first change created with polygerrit ui';
    assert.isFalse(element.privateChangeCheckBox.checked);

    const messageInput = queryAndAssert<IronAutogrowTextareaElement>(
      element,
      '#messageInput'
    );
    messageInput.bindValue = configInputObj.subject;

    await element.handleCreateChange();
    // Private change
    assert.isFalse(saveStub.lastCall.args[4]);
    // WIP Change
    assert.isTrue(saveStub.lastCall.args[5]);
    assert.isTrue(saveStub.called);
  });

  test('new change created with private', async () => {
    element.privateByDefault = {
      configured_value: InheritedBooleanInfoConfiguredValue.TRUE,
      inherited_value: false,
      value: true,
    };
    sinon.stub(element, 'formatPrivateByDefaultBoolean').callsFake(() => true);
    await element.updateComplete;

    const configInputObj = {
      branch: 'test-branch',
      subject: 'first change created with polygerrit ui',
      topic: 'test-topic',
      is_private: true,
      work_in_progress: true,
    };

    const saveStub = stubRestApi('createChange').returns(
      Promise.resolve(createChange())
    );

    element.branch = 'test-branch' as BranchName;
    element.topic = 'test-topic';
    element.subject = 'first change created with polygerrit ui';
    assert.isTrue(element.privateChangeCheckBox.checked);

    const messageInput = queryAndAssert<IronAutogrowTextareaElement>(
      element,
      '#messageInput'
    );
    messageInput.bindValue = configInputObj.subject;

    await element.handleCreateChange();
    // Private change
    assert.isTrue(saveStub.lastCall.args[4]);
    // WIP Change
    assert.isTrue(saveStub.lastCall.args[5]);
    assert.isTrue(saveStub.called);
  });

  test('getRepoBranchesSuggestions empty', async () => {
    const branches = await element.getRepoBranchesSuggestions('nonexistent');
    assert.equal(branches.length, 0);
  });

  test('getRepoBranchesSuggestions non-empty', async () => {
    const branches = await element.getRepoBranchesSuggestions('test-branch');
    assert.equal(branches.length, 1);
    assert.equal(branches[0].name, 'test-branch');
  });
});
