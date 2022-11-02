/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
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
import {RepoState, SHOWN_ITEMS_COUNT} from '../../../constants/constants';
import {GerritView} from '../../../services/router/router-model';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {GrListView} from '../../shared/gr-list-view/gr-list-view';
import {fixture, html, assert} from '@open-wc/testing';
import {AdminChildView, AdminViewState} from '../../../models/views/admin';

function createRepo(name: string, counter: number) {
  return {
    id: `${name}${counter}` as UrlEncodedRepoName,
    name: `${name}` as RepoName,
    state: 'ACTIVE' as RepoState,
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
    element = await fixture(html`<gr-repo-list></gr-repo-list>`);
  });

  suite('list with repos', () => {
    setup(async () => {
      repos = createRepoList('test', 26);
      stubRestApi('getRepos').returns(Promise.resolve(repos));
      await element._paramsChanged();
      await element.updateComplete;
    });

    test('render', () => {
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-list-view>
            <table class="genericList" id="list">
              <tbody>
                <tr class="headerRow">
                  <th class="name topHeader">Repository Name</th>
                  <th class="repositoryBrowser topHeader">
                    Repository Browser
                  </th>
                  <th class="changesLink topHeader">Changes</th>
                  <th class="readOnly topHeader">Read only</th>
                  <th class="description topHeader">Repository Description</th>
                </tr>
                <tr class="loadingMsg" id="loading">
                  <td>Loading...</td>
                </tr>
              </tbody>
              <tbody>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test0"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test1"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test2"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test3"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test4"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test5"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test6"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test7"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test8"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test9"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test10"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test11"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test12"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test13"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test14"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test15"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test16"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test17"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test18"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test19"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test20"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test21"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test22"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test23"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
                <tr class="table">
                  <td class="name">
                    <a href="/admin/repos/test"> test </a>
                  </td>
                  <td class="repositoryBrowser">
                    <a
                      class="webLink"
                      href="https://phabricator.example.org/r/project/test24"
                      rel="noopener"
                      target="_blank"
                    >
                      diffusion
                    </a>
                  </td>
                  <td class="changesLink">
                    <a href="/q/project:test"> view all </a>
                  </td>
                  <td class="readOnly"></td>
                  <td class="description"></td>
                </tr>
              </tbody>
            </table>
          </gr-list-view>
          <dialog id="createModal" tabindex="-1">
            <gr-dialog
              class="confirmDialog"
              confirm-label="Create"
              disabled=""
              id="createDialog"
              role="dialog"
            >
              <div class="header" slot="header">Create Repository</div>
              <div class="main" slot="main">
                <gr-create-repo-dialog id="createNewModal">
                </gr-create-repo-dialog>
              </div>
            </gr-dialog>
          </dialog>
        `
      );
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

    test('maybeOpenCreateModal', () => {
      const modalOpen = sinon.stub(
        queryAndAssert<HTMLDialogElement>(element, '#createModal'),
        'showModal'
      );
      element.maybeOpenCreateModal();
      assert.isFalse(modalOpen.called);
      element.maybeOpenCreateModal(undefined);
      assert.isFalse(modalOpen.called);
      const params: AdminViewState = {
        view: GerritView.ADMIN,
        adminView: AdminChildView.REPOS,
        openCreateModal: true,
      };
      element.maybeOpenCreateModal(params);
      assert.isTrue(modalOpen.called);
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
        adminView: AdminChildView.REPOS,
        filter: 'test',
        offset: 25,
      } as AdminViewState;
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
      const openStub = sinon.stub(
        queryAndAssert<HTMLDialogElement>(element, '#createModal'),
        'showModal'
      );
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
