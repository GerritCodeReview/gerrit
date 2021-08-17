/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
import './gr-included-in-dialog';
import {GrIncludedInDialog} from './gr-included-in-dialog';
import {BranchName, IncludedInInfo, TagName} from '../../../types/common';
import {IronInputElement} from '@polymer/iron-input';
import {queryAndAssert} from '../../../test/test-utils';

const basicFixture = fixtureFromElement('gr-included-in-dialog');

suite('gr-included-in-dialog', () => {
  let element: GrIncludedInDialog;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('_computeGroups', () => {
    const includedIn = {branches: [], tags: []} as IncludedInInfo;
    let filterText = '';
    assert.deepEqual(element._computeGroups(includedIn, filterText), []);

    includedIn.branches.push(
      'master' as BranchName,
      'development' as BranchName,
      'stable-2.0' as BranchName
    );
    includedIn.tags.push(
      'v1.9' as TagName,
      'v2.0' as TagName,
      'v2.1' as TagName
    );
    assert.deepEqual(element._computeGroups(includedIn, filterText), [
      {title: 'Branches', items: ['master', 'development', 'stable-2.0']},
      {title: 'Tags', items: ['v1.9', 'v2.0', 'v2.1']},
    ]);

    includedIn.external = {};
    assert.deepEqual(element._computeGroups(includedIn, filterText), [
      {title: 'Branches', items: ['master', 'development', 'stable-2.0']},
      {title: 'Tags', items: ['v1.9', 'v2.0', 'v2.1']},
    ]);

    includedIn.external.foo = ['abc', 'def', 'ghi'];
    assert.deepEqual(element._computeGroups(includedIn, filterText), [
      {title: 'Branches', items: ['master', 'development', 'stable-2.0']},
      {title: 'Tags', items: ['v1.9', 'v2.0', 'v2.1']},
      {title: 'foo', items: ['abc', 'def', 'ghi']},
    ]);

    filterText = 'v2';
    assert.deepEqual(element._computeGroups(includedIn, filterText), [
      {title: 'Tags', items: ['v2.0', 'v2.1']},
    ]);

    // Filtering is case-insensitive.
    filterText = 'V2';
    assert.deepEqual(element._computeGroups(includedIn, filterText), [
      {title: 'Tags', items: ['v2.0', 'v2.1']},
    ]);
  });

  test('_computeGroups with .bindValue', done => {
    queryAndAssert<IronInputElement>(element, '#filterInput')!.bindValue =
      'stable-3.2';
    const includedIn = {branches: [], tags: []} as IncludedInInfo;
    includedIn.branches.push(
      'master' as BranchName,
      'stable-3.2' as BranchName
    );

    setTimeout(() => {
      const filterText = element._filterText;
      assert.deepEqual(element._computeGroups(includedIn, filterText), [
        {title: 'Branches', items: ['stable-3.2']},
      ]);

      done();
    });
  });
});
