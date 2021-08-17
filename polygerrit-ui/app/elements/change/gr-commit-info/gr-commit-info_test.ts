/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import '../../core/gr-router/gr-router';
import './gr-commit-info';
import {GrCommitInfo} from './gr-commit-info';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {
  createChange,
  createCommit,
  createServerInfo,
} from '../../../test/test-data-generators';
import {CommitId, RepoName} from '../../../types/common';

const basicFixture = fixtureFromElement('gr-commit-info');

suite('gr-commit-info tests', () => {
  let element: GrCommitInfo;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('weblinks use GerritNav interface', () => {
    const weblinksStub = sinon
      .stub(GerritNav, '_generateWeblinks')
      .returns([{name: 'stubb', url: '#s'}]);
    element.change = createChange();
    element.commitInfo = createCommit();
    element.serverConfig = createServerInfo();
    assert.isTrue(weblinksStub.called);
  });

  test('no web link when unavailable', () => {
    element.commitInfo = createCommit();
    element.serverConfig = createServerInfo();
    element.change = {...createChange(), labels: {}, project: '' as RepoName};

    assert.isNotOk(element._showWebLink);
  });

  test('use web link when available', () => {
    const router = document.createElement('gr-router');
    sinon
      .stub(GerritNav, '_generateWeblinks')
      .callsFake(router._generateWeblinks.bind(router));

    element.change = {...createChange(), labels: {}, project: '' as RepoName};
    element.commitInfo = {
      ...createCommit(),
      commit: 'commitsha' as CommitId,
      web_links: [{name: 'gitweb', url: 'link-url'}],
    };
    element.serverConfig = createServerInfo();

    assert.isOk(element._showWebLink);
    assert.equal(element._webLink, 'link-url');
  });

  test('does not relativize web links that begin with scheme', () => {
    const router = document.createElement('gr-router');
    sinon
      .stub(GerritNav, '_generateWeblinks')
      .callsFake(router._generateWeblinks.bind(router));

    element.change = {...createChange(), labels: {}, project: '' as RepoName};
    element.commitInfo = {
      ...createCommit(),
      commit: 'commitsha' as CommitId,
      web_links: [{name: 'gitweb', url: 'https://link-url'}],
    };
    element.serverConfig = createServerInfo();

    assert.isOk(element._showWebLink);
    assert.equal(element._webLink, 'https://link-url');
  });

  test('ignore web links that are neither gitweb nor gitiles', () => {
    const router = document.createElement('gr-router');
    sinon
      .stub(GerritNav, '_generateWeblinks')
      .callsFake(router._generateWeblinks.bind(router));

    element.change = {...createChange(), project: 'project-name' as RepoName};
    element.commitInfo = {
      ...createCommit(),
      commit: 'commit-sha' as CommitId,
      web_links: [
        {
          name: 'ignore',
          url: 'ignore',
        },
        {
          name: 'gitiles',
          url: 'https://link-url',
        },
      ],
    };
    element.serverConfig = createServerInfo();

    assert.isOk(element._showWebLink);
    assert.equal(element._webLink, 'https://link-url');

    // Remove gitiles link.
    element.commitInfo = {
      ...createCommit(),
      commit: 'commit-sha' as CommitId,
      web_links: [
        {
          name: 'ignore',
          url: 'ignore',
        },
      ],
    };
    assert.isNotOk(element._showWebLink);
    assert.isNotOk(element._webLink);
  });
});
