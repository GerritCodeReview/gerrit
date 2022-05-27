/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import './gr-repo-list';
import {GrRepoList} from './gr-repo-list';
import {page} from '../../../utils/page-wrapper-utils';
import {
  mockPromise,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {
  UrlEncodedRepoName,
  ProjectInfoWithName,
  RepoName,
} from '../../../types/common';
import {AppElementAdminParams} from '../../gr-app-types';
import {ProjectState, SHOWN_ITEMS_COUNT} from '../../../constants/constants';
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

  setup(async () => {
    sinon.stub(page, 'show');
    element = basicFixture.instantiate();
    await element.updateComplete;
  });

  suite('list with repos', () => {
    setup(async () => {
      repos = createRepoList('test', 26);
      stubRestApi('getRepos').returns(Promise.resolve(repos));
      await element._paramsChanged();
      await element.updateComplete;
    });

    test('test for test repo in the list', async () => {
      await element.updateComplete;
      assert.equal(element.repos[0].id, 'test0');
      assert.equal(element.repos[1].id, 'test1');
      assert.equal(element.repos[2].id, 'test2');
    });

    test('shownRepos', () => {
      assert.equal(element.repos.slice(0, SHOWN_ITEMS_COUNT).length, 25);
    });

    test('maybeOpenCreateOverlay', () => {
      const overlayOpen = sinon.stub(
        queryAndAssert<GrOverlay>(element, '#createOverlay'),
        'open'
      );
      element.maybeOpenCreateOverlay();
      assert.isFalse(overlayOpen.called);
      element.maybeOpenCreateOverlay(undefined);
      assert.isFalse(overlayOpen.called);
      const params: AppElementAdminParams = {
        view: GerritView.ADMIN,
        adminView: '',
        openCreateModal: true,
      };
      element.maybeOpenCreateOverlay(params);
      assert.isTrue(overlayOpen.called);
    });
  });

  suite('list with less then 25 repos', () => {
    setup(async () => {
      repos = createRepoList('test', 25);
      stubRestApi('getRepos').returns(Promise.resolve(repos));
      await element._paramsChanged();
      await element.updateComplete;
    });

    test('shownRepos', () => {
      assert.equal(element.repos.slice(0, SHOWN_ITEMS_COUNT).length, 25);
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
      element.params = {
        view: GerritView.ADMIN,
        adminView: '',
        filter: 'test',
        offset: 25,
      } as AppElementAdminParams;
      await element._paramsChanged();
      assert.isTrue(repoStub.lastCall.calledWithExactly('test', 25, 25));
    });

    test('latest repos requested are always set', async () => {
      const repoStub = stubRestApi('getRepos');
      const promise = mockPromise<ProjectInfoWithName[]>();
      repoStub.withArgs('filter', 25).returns(promise);

      element.filter = 'test';
      element.reposPerPage = 25;
      element.offset = 0;

      // Repos are not set because the element.filter differs.
      const p = element.getRepos();
      element.filter = 'filter';
      promise.resolve(reposFiltered);
      await p;
      assert.deepEqual(element.repos, []);
    });

    test('filter is case insensitive', async () => {
      const repoStub = stubRestApi('getRepos');
      const repos = [createRepo('aSDf', 0)];
      repoStub.withArgs('asdf', 25).returns(Promise.resolve(repos));

      element.filter = 'asdf';
      element.reposPerPage = 25;
      element.offset = 0;

      await element.getRepos();
      assert.equal(element.repos.length, 1);
    });
  });

  suite('loading', () => {
    test('correct contents are displayed', async () => {
      assert.isTrue(element.loading);
      assert.equal(element.computeLoadingClass(element.loading), 'loading');
      assert.equal(
        getComputedStyle(
          queryAndAssert<HTMLTableRowElement>(element, '#loading')
        ).display,
        'block'
      );

      element.loading = false;
      element.repos = createRepoList('test', 25);

      await element.updateComplete;
      assert.equal(element.computeLoadingClass(element.loading), '');
      assert.equal(
        getComputedStyle(
          queryAndAssert<HTMLTableRowElement>(element, '#loading')
        ).display,
        'none'
      );
    });
  });

  suite('create new', () => {
    test('handleCreateClicked called when create-clicked fired', () => {
      const handleCreateClickedStub = sinon.stub(
        element,
        'handleCreateClicked'
      );
      queryAndAssert<GrListView>(element, 'gr-list-view').dispatchEvent(
        new CustomEvent('create-clicked', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(handleCreateClickedStub.called);
    });

    test('handleCreateClicked opens modal', () => {
      const openStub = sinon
        .stub(queryAndAssert<GrOverlay>(element, '#createOverlay'), 'open')
        .returns(Promise.resolve());
      element.handleCreateClicked();
      assert.isTrue(openStub.called);
    });

    test('handleCreateRepo called when confirm fired', () => {
      const handleCreateRepoStub = sinon.stub(element, 'handleCreateRepo');
      queryAndAssert<GrDialog>(element, '#createDialog').dispatchEvent(
        new CustomEvent('confirm', {
          composed: true,
          bubbles: false,
        })
      );
      assert.isTrue(handleCreateRepoStub.called);
    });

    test('handleCloseCreate called when cancel fired', () => {
      const handleCloseCreateStub = sinon.stub(element, 'handleCloseCreate');
      queryAndAssert<GrDialog>(element, '#createDialog').dispatchEvent(
        new CustomEvent('cancel', {
          composed: true,
          bubbles: false,
        })
      );
      assert.isTrue(handleCloseCreateStub.called);
    });
  });
});
