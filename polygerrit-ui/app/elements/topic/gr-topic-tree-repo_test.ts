/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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

import {RepoName} from '../../api/rest-api';
import '../../test/common-test-setup-karma';
import {createChange} from '../../test/test-data-generators';
import {queryAndAssert} from '../../test/test-utils';
import './gr-topic-tree-repo';
import {GrTopicTreeRepo} from './gr-topic-tree-repo';

const basicFixture = fixtureFromElement('gr-topic-tree-repo');
const repoName = 'myRepo' as RepoName;

suite('gr-topic-tree-repo tests', () => {
  let element: GrTopicTreeRepo;

  setup(async () => {
    element = basicFixture.instantiate();
    element.repoName = repoName;
    element.changes = [createChange()];
    await element.updateComplete;
  });

  test('shows repository name', () => {
    const heading = queryAndAssert<HTMLHeadingElement>(element, 'h2');
    assert.equal(heading.textContent, `Repo ${repoName}`);
  });
});
