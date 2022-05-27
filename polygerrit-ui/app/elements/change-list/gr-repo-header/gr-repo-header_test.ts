/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import './gr-repo-header';
import {GrRepoHeader} from './gr-repo-header';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {stubRestApi} from '../../../test/test-utils';
import {RepoName, UrlEncodedRepoName} from '../../../types/common';

const basicFixture = fixtureFromElement('gr-repo-header');

suite('gr-repo-header tests', () => {
  let element: GrRepoHeader;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('repoUrl reset once repo changed', async () => {
    sinon
      .stub(GerritNav, 'getUrlForRepo')
      .callsFake(repoName => `http://test.com/${repoName},general`);
    assert.equal(element._repoUrl, undefined);
    element.repo = 'test' as RepoName;
    await flush();
    assert.equal(element._repoUrl, 'http://test.com/test,general');
  });

  test('webLinks set', async () => {
    const repoRes = {
      id: 'test' as UrlEncodedRepoName,
      web_links: [
        {
          name: 'gitiles',
          url: 'https://gerrit.test/g',
        },
      ],
    };

    stubRestApi('getRepo').returns(Promise.resolve(repoRes));

    assert.deepEqual(element._webLinks, []);

    element.repo = 'test' as RepoName;
    await flush();
    assert.deepEqual(element._webLinks, repoRes.web_links);
  });
});
