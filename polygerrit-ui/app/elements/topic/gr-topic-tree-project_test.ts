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

import '../../test/common-test-setup-karma';
import {createChange} from '../../test/test-data-generators';
import {queryAndAssert} from '../../test/test-utils';
import './gr-topic-tree-project';
import {GrTopicTreeProject} from './gr-topic-tree-project';

const basicFixture = fixtureFromElement('gr-topic-tree-project');
const projectName = 'myProject';

suite('gr-topic-tree-project tests', () => {
  let element: GrTopicTreeProject;

  setup(async () => {
    element = basicFixture.instantiate();
    element.projectName = projectName;
    element.changes = [createChange()];
    await element.updateComplete;
  });

  test('shows project name', () => {
    const heading = queryAndAssert<HTMLHeadingElement>(element, 'h2');
    assert.equal(heading.textContent, `Project ${projectName}`);
  });
});
