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
import {queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {BranchName, GitRef, RepoName} from '../../../types/common';
import {fixture, html} from '@open-wc/testing-helpers';
import {GrAutocomplete} from '../../shared/gr-autocomplete/gr-autocomplete';

suite('gr-confirm-move-dialog tests', () => {
  let element: GrConfirmMoveDialog;

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
    element = await fixture(
      html`<gr-confirm-move-dialog
        .project=${'test-repo' as RepoName}
      ></gr-confirm-move-dialog>`
    );
  });

  test('with updated commit message', async () => {
    element.branch = 'master' as BranchName;
    const myNewMessage = 'updated commit message';
    element.message = myNewMessage;
    await element.updateComplete;

    assert.equal(element.message, myNewMessage);
  });

  test('suggestions empty', async () => {
    const autoComplete = queryAndAssert<GrAutocomplete>(
      element,
      'gr-autocomplete'
    );
    const branches = await autoComplete.query!('nonexistent');
    assert.equal(branches.length, 0);
  });

  test('suggestions non-empty', async () => {
    const autoComplete = queryAndAssert<GrAutocomplete>(
      element,
      'gr-autocomplete'
    );
    const branches = await autoComplete.query!('test-branch');
    assert.equal(branches.length, 1);
    assert.equal(branches[0].name, 'test-branch');
  });

  test('suggestions input empty string', async () => {
    const autoComplete = queryAndAssert<GrAutocomplete>(
      element,
      'gr-autocomplete'
    );
    const branches = await autoComplete.query!('');
    assert.equal(branches.length, 0);
  });
});
