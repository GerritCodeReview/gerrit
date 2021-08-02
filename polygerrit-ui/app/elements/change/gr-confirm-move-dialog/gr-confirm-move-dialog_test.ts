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
import './gr-confirm-move-dialog';
import {GrConfirmMoveDialog} from './gr-confirm-move-dialog';
import {stubRestApi} from '../../../test/test-utils';
import {GitRef, RepoName} from '../../../types/common';

const basicFixture = fixtureFromElement('gr-confirm-move-dialog');

suite('gr-confirm-move-dialog tests', () => {
  let element: GrConfirmMoveDialog;

  setup(() => {
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
    element.project = 'test-repo' as RepoName;
  });

  test('with updated commit message', () => {
    element.branch = 'master';
    const myNewMessage = 'updated commit message';
    element.message = myNewMessage;
    flush();
    assert.equal(element.message, myNewMessage);
  });

  test('_getProjectBranchesSuggestions empty', async () => {
    const branches = await element._getProjectBranchesSuggestions(
        'nonexistent');
    assert.equal(branches.length, 0);
  });

  test('_getProjectBranchesSuggestions non-empty', async () => {
    const branches = await element._getProjectBranchesSuggestions(
        'test-branch');
    assert.equal(branches.length, 1);
    assert.equal(branches[0].name, 'test-branch');
  });

  test('_getProjectBranchesSuggestions input empty string', async () => {
    const branches = await element._getProjectBranchesSuggestions('');
    assert.equal(branches.length, 0);
  });
});
