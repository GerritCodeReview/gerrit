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
import './gr-repo-list';
import {GrRepoList} from './gr-repo-list';
import {page} from '../../../utils/page-wrapper-utils';
import {queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {
  UrlEncodedRepoName,
  ProjectInfoWithName,
  RepoName,
} from '../../../types/common';
import {AppElementAdminParams} from '../../gr-app-types';
import {ProjectState} from '../../../constants/constants';
import {GerritView} from '../../../services/router/router-model';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {GrListView} from '../../shared/gr-list-view/gr-list-view';

const basicFixture = fixtureFromElement('gr-repo-list');

function createRepo(name: string, counter: number) {
  return {
    id: `${name}${counter}` as UrlEncodedRepoName,
    name: `${name}` as RepoName,
    state: 'ACTIVE' as ProjectState,
    web_links: [
      {
        name: 'diffusion',
        url: `https://phabricator.example.org/r/project/${name}${counter}`,
      },
    ],
  };
}

function createRepoList(name: string, n: number) {
  const repos = [];
  for (let i = 0; i < n; ++i) {
    repos.push(createRepo(name, i));
  }
  return repos;
}

suite('gr-repo-list tests', () => {
  let element: GrRepoList;
  let repos: ProjectInfoWithName[];

  const value: AppElementAdminParams = {view: GerritView.ADMIN, adminView: ''};

  setup(() => {
    sinon.stub(page, 'show');
    element = basicFixture.instantiate();
  });

  suite('list with repos', () => {
    setup(async () => {
      repos = createRepoList('test', 26);
      stubRestApi('getRepos').returns(Promise.resolve(repos));
      await element._paramsChanged(value);
      await flush();
    });

    test('test for test repo in the list', async () => {
      await flush();
      assert.equal(element._repos[0].id, 'test0');
      assert.equal(element._repos[1].id, 'test1');
      assert.equal(element._repos[2].id, 'test2');
    });

    test('_shownRepos', () => {
      assert.equal(element._shownRepos.length, 25);
    });

    test('_maybeOpenCreateOverlay', () => {
      const overlayOpen = sinon.stub(
        queryAndAssert<GrOverlay>(element, '#createOverlay'),
        'open'
      );
      element._maybeOpenCreateOverlay();
      assert.isFalse(overlayOpen.called);
      element._maybeOpenCreateOverlay(undefined);
      assert.isFalse(overlayOpen.called);
      const params: AppElementAdminParams = {
        view: GerritView.ADMIN,
        adminView: '',
        openCreateModal: true,
      };
      element._maybeOpenCreateOverlay(params);
      assert.isTrue(overlayOpen.called);
    });
  });

  suite('list with less then 25 repos', () => {
    setup(async () => {
      repos = createRepoList('test', 25);
      stubRestApi('getRepos').returns(Promise.resolve(repos));
      await element._paramsChanged(value);
      await flush();
    });

    test('_shownRepos', () => {
      assert.equal(element._shownRepos.length, 25);
    });
  });

  suite('filter', () => {
    let reposFiltered: ProjectInfoWithName[];

    setup(() => {
      repos = createRepoList('test', 25);
      reposFiltered = createRepoList('filter', 1);
    });

    test('_paramsChanged', async () => {
      const repoStub = stubRestApi('getRepos');
      repoStub.returns(Promise.resolve(repos));
      const value: AppElementAdminParams = {
        view: GerritView.ADMIN,
        adminView: '',
        filter: 'test',
        offset: 25,
      };
      await element._paramsChanged(value);
      assert.isTrue(repoStub.lastCall.calledWithExactly('test', 25, 25));
    });

    test('latest repos requested are always set', async () => {
      const repoStub = stubRestApi('getRepos');
      repoStub.withArgs('test', 25).returns(Promise.resolve(repos));
      repoStub.withArgs('filter', 25).returns(Promise.resolve(reposFiltered));
      element._filter = 'test';

      // Repos are not set because the element._filter differs.
      await element._getRepos('filter', 25, 0);
      assert.deepEqual(element._repos, []);
    });

    test('filter is case insensitive', async () => {
      const repoStub = stubRestApi('getRepos');
      const repos = [createRepo('aSDf', 0)];
      repoStub.withArgs('asdf', 25).returns(Promise.resolve(repos));
      element._filter = 'asdf';
      await element._getRepos('asdf', 25, 0);
      assert.equal(element._repos.length, 1);
    });
  });

  suite('loading', () => {
    test('correct contents are displayed', () => {
      assert.isTrue(element._loading);
      assert.equal(element.computeLoadingClass(element._loading), 'loading');
      assert.equal(
        getComputedStyle(
          queryAndAssert<HTMLTableRowElement>(element, '#loading')
        ).display,
        'block'
      );

      element._loading = false;
      element._repos = createRepoList('test', 25);

      flush();
      assert.equal(element.computeLoadingClass(element._loading), '');
      assert.equal(
        getComputedStyle(
          queryAndAssert<HTMLTableRowElement>(element, '#loading')
        ).display,
        'none'
      );
    });
  });

  suite('create new', () => {
    test('_handleCreateClicked called when create-click fired', () => {
      const handleCreateClickedStub = sinon.stub(
        element,
        '_handleCreateClicked'
      );
      queryAndAssert<GrListView>(element, 'gr-list-view').dispatchEvent(
        new CustomEvent('create-clicked', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(handleCreateClickedStub.called);
    });

    test('_handleCreateClicked opens modal', () => {
      const openStub = sinon
        .stub(queryAndAssert<GrOverlay>(element, '#createOverlay'), 'open')
        .returns(Promise.resolve());
      element._handleCreateClicked();
      assert.isTrue(openStub.called);
    });

    test('_handleCreateRepo called when confirm fired', () => {
      const handleCreateRepoStub = sinon.stub(element, '_handleCreateRepo');
      queryAndAssert<GrDialog>(element, '#createDialog').dispatchEvent(
        new CustomEvent('confirm', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(handleCreateRepoStub.called);
    });

    test('_handleCloseCreate called when cancel fired', () => {
      const handleCloseCreateStub = sinon.stub(element, '_handleCloseCreate');
      queryAndAssert<GrDialog>(element, '#createDialog').dispatchEvent(
        new CustomEvent('cancel', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(handleCloseCreateStub.called);
    });
  });
});
