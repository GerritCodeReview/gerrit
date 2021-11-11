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

import {ChangeInfo, RepoName} from '../../api/rest-api';
import '../../test/common-test-setup-karma';
import {createChange} from '../../test/test-data-generators';
import {queryAll, stubRestApi} from '../../test/test-utils';
import './gr-topic-tree';
import {GrTopicTree} from './gr-topic-tree';
import {GrTopicTreeProject} from './gr-topic-tree-project';

const basicFixture = fixtureFromElement('gr-topic-tree');

function createChangeForProject(projectName: string): ChangeInfo {
  return {...createChange(), project: projectName as RepoName};
}

suite('gr-topic-tree tests', () => {
  let element: GrTopicTree;
  const project1Changes = [
    createChangeForProject('project1'),
    createChangeForProject('project1'),
  ];
  const project2Changes = [
    createChangeForProject('project2'),
    createChangeForProject('project2'),
  ];
  const project3Changes = [
    createChangeForProject('project3'),
    createChangeForProject('project3'),
  ];

  setup(async () => {
    stubRestApi('getChanges')
      .withArgs(undefined, 'topic:myTopic')
      .resolves([...project1Changes, ...project2Changes, ...project3Changes]);
    element = basicFixture.instantiate();
    element.topicName = 'myTopic';
    await element.updateComplete;
  });

  test('groups changes by project', () => {
    const projectSections = queryAll<GrTopicTreeProject>(
      element,
      'gr-topic-tree-project'
    );
    assert.lengthOf(projectSections, 3);
    assert.equal(projectSections[0].projectName, 'project1');
    assert.sameMembers(projectSections[0].changes!, project1Changes);
    assert.equal(projectSections[1].projectName, 'project2');
    assert.sameMembers(projectSections[1].changes!, project2Changes);
    assert.equal(projectSections[2].projectName, 'project3');
    assert.sameMembers(projectSections[2].changes!, project3Changes);
  });
});
