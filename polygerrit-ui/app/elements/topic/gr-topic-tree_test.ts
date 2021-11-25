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
import {mockPromise, queryAll, stubRestApi} from '../../test/test-utils';
import {SubmittedTogetherInfo} from '../../types/common';
import './gr-topic-tree';
import {GrTopicTree} from './gr-topic-tree';
import {GrTopicTreeRepo} from './gr-topic-tree-repo';

const basicFixture = fixtureFromElement('gr-topic-tree');

const repo1Name = 'repo1' as RepoName;
const repo2Name = 'repo2' as RepoName;
const repo3Name = 'repo3' as RepoName;

function createChangeForRepo(repoName: string): ChangeInfo {
  return {...createChange(), project: repoName as RepoName};
}

suite('gr-topic-tree tests', () => {
  let element: GrTopicTree;
  const repo1ChangeOutsideTopic = createChangeForRepo(repo1Name);
  const repo1ChangesInTopic = [
    createChangeForRepo(repo1Name),
    createChangeForRepo(repo1Name),
  ];
  const repo2ChangesInTopic = [
    createChangeForRepo(repo2Name),
    createChangeForRepo(repo2Name),
  ];
  const repo3ChangesInTopic = [
    createChangeForRepo(repo3Name),
    createChangeForRepo(repo3Name),
  ];

  setup(async () => {
    stubRestApi('getChanges')
      .withArgs(undefined, 'topic:myTopic')
      .resolves([
        ...repo1ChangesInTopic,
        ...repo2ChangesInTopic,
        ...repo3ChangesInTopic,
      ]);
    const changesSubmittedTogetherPromise =
      mockPromise<SubmittedTogetherInfo>();
    stubRestApi('getChangesSubmittedTogether').returns(
      changesSubmittedTogetherPromise
    );
    element = basicFixture.instantiate();
    element.topicName = 'myTopic';

    // The first update will trigger the data to be loaded. The second update
    // will be rendering the loaded data.
    await element.updateComplete;
    changesSubmittedTogetherPromise.resolve({
      changes: [
        ...repo1ChangesInTopic,
        repo1ChangeOutsideTopic,
        ...repo2ChangesInTopic,
        ...repo3ChangesInTopic,
      ],
      non_visible_changes: 0,
    });
    await element.updateComplete;
  });

  test('groups changes by repo', () => {
    const repoSections = queryAll<GrTopicTreeRepo>(
      element,
      'gr-topic-tree-repo'
    );
    assert.lengthOf(repoSections, 3);
    assert.equal(repoSections[0].repoName, repo1Name);
    assert.sameMembers(repoSections[0].changes!, [
      ...repo1ChangesInTopic,
      repo1ChangeOutsideTopic,
    ]);
    assert.equal(repoSections[1].repoName, repo2Name);
    assert.sameMembers(repoSections[1].changes!, repo2ChangesInTopic);
    assert.equal(repoSections[2].repoName, repo3Name);
    assert.sameMembers(repoSections[2].changes!, repo3ChangesInTopic);
  });
});
