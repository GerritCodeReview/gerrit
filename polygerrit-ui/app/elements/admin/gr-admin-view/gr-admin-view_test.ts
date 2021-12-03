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
import './gr-admin-view';
import {AdminSubsectionLink, GrAdminView} from './gr-admin-view';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {mockPromise, stubBaseUrl, stubRestApi} from '../../../test/test-utils';
import {GerritView} from '../../../services/router/router-model';
import {query, queryAll, queryAndAssert} from '../../../test/test-utils';
import {GrRepoList} from '../gr-repo-list/gr-repo-list';
import {GroupId, GroupName, RepoName, Timestamp} from '../../../types/common';
import {GrDropdownList} from '../../shared/gr-dropdown-list/gr-dropdown-list';
import {GrGroup} from '../gr-group/gr-group';

const basicFixture = fixtureFromElement('gr-admin-view');

function createAdminCapabilities() {
  return {
    createGroup: true,
    createProject: true,
    viewPlugins: true,
  };
}

suite('gr-admin-view tests', () => {
  let element: GrAdminView;

  setup(async () => {
    element = basicFixture.instantiate();
    stubRestApi('getProjectConfig').returns(Promise.resolve(undefined));
    const pluginsLoaded = Promise.resolve();
    sinon.stub(getPluginLoader(), 'awaitPluginsLoaded').returns(pluginsLoaded);
    await pluginsLoaded;
    await flush();
  });

  test('_computeURLHelper', () => {
    const path = '/test';
    const host = 'http://www.testsite.com';
    const computedPath = element._computeURLHelper(host, path);
    assert.equal(computedPath, '//http://www.testsite.com/test');
  });

  test('link URLs', () => {
    assert.equal(
      element._computeLinkURL({name: '', url: '/test', noBaseUrl: true}),
      '//' + window.location.host + '/test'
    );

    stubBaseUrl('/foo');
    assert.equal(
      element._computeLinkURL({name: '', url: '/test', noBaseUrl: true}),
      '//' + window.location.host + '/foo/test'
    );
    assert.equal(
      element._computeLinkURL({name: '', url: '/test', noBaseUrl: false}),
      '/test'
    );
    assert.equal(
      element._computeLinkURL({
        name: '',
        url: '/test',
        target: '_blank',
        noBaseUrl: false,
      }),
      '/test'
    );
  });

  test('current page gets selected and is displayed', () => {
    element._filteredLinks = [
      {
        name: 'Repositories',
        url: '/admin/repos',
        view: 'gr-repo-list' as GerritView,
        noBaseUrl: false,
      },
    ];

    element.params = {
      view: GerritView.ADMIN,
      adminView: 'gr-repo-list',
    };

    flush();
    assert.equal(queryAll<HTMLLIElement>(element, '.selected').length, 1);
    assert.ok(queryAndAssert<GrRepoList>(element, 'gr-repo-list'));
    assert.isNotOk(query(element, 'gr-admin-create-repo'));
  });

  test('_filteredLinks admin', async () => {
    stubRestApi('getAccount').returns(
      Promise.resolve({
        name: 'test-user',
        registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
      })
    );
    stubRestApi('getAccountCapabilities').returns(
      Promise.resolve(createAdminCapabilities())
    );
    await element.reload();
    assert.equal(element._filteredLinks!.length, 3);

    // Repos
    assert.isNotOk(element._filteredLinks![0].subsection);

    // Groups
    assert.isNotOk(element._filteredLinks![0].subsection);

    // Plugins
    assert.isNotOk(element._filteredLinks![0].subsection);
  });

  test('_filteredLinks non admin authenticated', async () => {
    await element.reload();
    assert.equal(element._filteredLinks!.length, 2);
    // Repos
    assert.isNotOk(element._filteredLinks![0].subsection);
    // Groups
    assert.isNotOk(element._filteredLinks![0].subsection);
  });

  test('_filteredLinks non admin unathenticated', async () => {
    stubRestApi('getAccount').returns(Promise.resolve(undefined));
    await element.reload();
    assert.equal(element._filteredLinks!.length, 1);
    // Repos
    assert.isNotOk(element._filteredLinks![0].subsection);
  });

  test('_filteredLinks from plugin', () => {
    stubRestApi('getAccount').returns(Promise.resolve(undefined));
    sinon.stub(element.jsAPI, 'getAdminMenuLinks').returns([
      {capability: null, text: 'internal link text', url: '/internal/link/url'},
      {
        capability: null,
        text: 'external link text',
        url: 'http://external/link/url',
      },
    ]);
    return element.reload().then(() => {
      assert.equal(element._filteredLinks!.length, 3);
      assert.deepEqual(element._filteredLinks![1], {
        capability: undefined,
        url: '/internal/link/url',
        name: 'internal link text',
        noBaseUrl: true,
        view: undefined,
        viewableToAll: true,
        target: null,
      });
      assert.deepEqual(element._filteredLinks![2], {
        capability: undefined,
        url: 'http://external/link/url',
        name: 'external link text',
        noBaseUrl: false,
        view: undefined,
        viewableToAll: true,
        target: '_blank',
      });
    });
  });

  test('Repo shows up in nav', async () => {
    element._repoName = 'Test Repo' as RepoName;
    stubRestApi('getAccount').returns(
      Promise.resolve({
        name: 'test-user',
        registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
      })
    );
    stubRestApi('getAccountCapabilities').returns(
      Promise.resolve(createAdminCapabilities())
    );
    await element.reload();
    await flush();
    assert.equal(queryAll<HTMLLIElement>(element, '.sectionTitle').length, 3);
    assert.equal(
      queryAndAssert<HTMLSpanElement>(element, '.breadcrumbText').innerText,
      'Test Repo'
    );
    assert.equal(
      queryAndAssert<GrDropdownList>(element, '#pageSelect').items!.length,
      7
    );
  });

  test('Group shows up in nav', async () => {
    element._groupId = 'a15262' as GroupId;
    element._groupName = 'my-group' as GroupName;
    element._groupIsInternal = true;
    element._isAdmin = true;
    element._groupOwner = false;
    stubRestApi('getAccount').returns(
      Promise.resolve({
        name: 'test-user',
        registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
      })
    );
    stubRestApi('getAccountCapabilities').returns(
      Promise.resolve(createAdminCapabilities())
    );
    await element.reload();
    await flush();
    assert.equal(element._filteredLinks!.length, 3);
    // Repos
    assert.isNotOk(element._filteredLinks![0].subsection);
    // Groups
    assert.equal(element._filteredLinks![1].subsection!.children!.length, 2);
    assert.equal(element._filteredLinks![1].subsection!.name, 'my-group');
    // Plugins
    assert.isNotOk(element._filteredLinks![2].subsection);
  });

  test('Nav is reloaded when repo changes', () => {
    stubRestApi('getAccountCapabilities').returns(
      Promise.resolve(createAdminCapabilities())
    );
    stubRestApi('getAccount').returns(
      Promise.resolve({
        _id: 1,
        registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
      })
    );
    const reloadStub = sinon.stub(element, 'reload');
    element.params = {repo: 'Test Repo' as RepoName, view: GerritView.REPO};
    assert.equal(reloadStub.callCount, 1);
    element.params = {repo: 'Test Repo 2' as RepoName, view: GerritView.REPO};
    assert.equal(reloadStub.callCount, 2);
  });

  test('Nav is reloaded when group changes', () => {
    sinon.stub(element, '_computeGroupName');
    stubRestApi('getAccountCapabilities').returns(
      Promise.resolve(createAdminCapabilities())
    );
    stubRestApi('getAccount').returns(
      Promise.resolve({
        _id: 1,
        registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
      })
    );
    const reloadStub = sinon.stub(element, 'reload');
    element.params = {groupId: '1' as GroupId, view: GerritView.GROUP};
    assert.equal(reloadStub.callCount, 1);
  });

  test('Nav is reloaded when group name changes', async () => {
    const newName = 'newName' as GroupName;
    const reloadCalled = mockPromise();
    sinon.stub(element, '_computeGroupName');
    sinon.stub(element, 'reload').callsFake(() => {
      reloadCalled.resolve();
      return Promise.resolve();
    });
    element.params = {groupId: '1' as GroupId, view: GerritView.GROUP};
    element._groupName = 'oldName' as GroupName;
    await flush();
    queryAndAssert<GrGroup>(element, 'gr-group').dispatchEvent(
      new CustomEvent('name-changed', {
        detail: {name: newName},
        composed: true,
        bubbles: true,
      })
    );
    await reloadCalled;
    assert.equal(element._groupName, newName);
  });

  test('dropdown displays if there is a subsection', () => {
    assert.isNotOk(query(element, '.mainHeader'));
    element._subsectionLinks = [
      {
        text: 'Home',
        value: 'repo',
        view: GerritView.REPO,
        parent: 'my-repo' as RepoName,
        detailType: undefined,
      },
    ];
    flush();
    assert.isOk(query(element, '.mainHeader'));
    element._subsectionLinks = undefined;
    flush();
    assert.equal(
      getComputedStyle(queryAndAssert(element, '.mainHeader')).display,
      'none'
    );
  });

  test('Dropdown only triggers navigation on explicit select', async () => {
    element._repoName = 'my-repo' as RepoName;
    element.params = {
      repo: 'my-repo' as RepoName,
      view: GerritNav.View.REPO,
      detail: GerritNav.RepoDetailView.ACCESS,
    };
    stubRestApi('getAccountCapabilities').returns(
      Promise.resolve(createAdminCapabilities())
    );
    stubRestApi('getAccount').returns(
      Promise.resolve({
        _id: 1,
        registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
      })
    );
    await flush();
    const expectedFilteredLinks = [
      {
        name: 'Repositories',
        noBaseUrl: true,
        url: '/admin/repos',
        view: 'gr-repo-list' as GerritView,
        viewableToAll: true,
        subsection: {
          name: 'my-repo',
          view: GerritView.REPO,
          children: [
            {
              name: 'General',
              view: GerritView.REPO,
              url: '',
              detailType: GerritNav.RepoDetailView.GENERAL,
            },
            {
              name: 'Access',
              view: GerritView.REPO,
              detailType: GerritNav.RepoDetailView.ACCESS,
              url: '',
            },
            {
              name: 'Commands',
              view: GerritView.REPO,
              detailType: GerritNav.RepoDetailView.COMMANDS,
              url: '',
            },
            {
              name: 'Branches',
              view: GerritView.REPO,
              detailType: GerritNav.RepoDetailView.BRANCHES,
              url: '',
            },
            {
              name: 'Tags',
              view: GerritView.REPO,
              detailType: GerritNav.RepoDetailView.TAGS,
              url: '',
            },
            {
              name: 'Dashboards',
              view: GerritView.REPO,
              detailType: GerritNav.RepoDetailView.DASHBOARDS,
              url: '',
            },
          ],
        },
      },
      {
        name: 'Groups',
        section: 'Groups',
        noBaseUrl: true,
        url: '/admin/groups',
        view: 'gr-admin-group-list' as GerritView,
      },
      {
        name: 'Plugins',
        capability: 'viewPlugins',
        section: 'Plugins',
        noBaseUrl: true,
        url: '/admin/plugins',
        view: 'gr-plugin-list' as GerritView,
      },
    ];
    const expectedSubsectionLinks = [
      {
        text: 'Home',
        value: 'repo',
        view: GerritView.REPO,
        url: undefined,
        parent: 'my-repo' as RepoName,
        detailType: undefined,
      },
      {
        text: 'General',
        value: 'repogeneral',
        view: GerritView.REPO,
        url: '',
        detailType: GerritNav.RepoDetailView.GENERAL,
        parent: 'my-repo' as RepoName,
      },
      {
        text: 'Access',
        value: 'repoaccess',
        view: GerritView.REPO,
        url: '',
        detailType: GerritNav.RepoDetailView.ACCESS,
        parent: 'my-repo' as RepoName,
      },
      {
        text: 'Commands',
        value: 'repocommands',
        view: GerritView.REPO,
        url: '',
        detailType: GerritNav.RepoDetailView.COMMANDS,
        parent: 'my-repo' as RepoName,
      },
      {
        text: 'Branches',
        value: 'repobranches',
        view: GerritView.REPO,
        url: '',
        detailType: GerritNav.RepoDetailView.BRANCHES,
        parent: 'my-repo' as RepoName,
      },
      {
        text: 'Tags',
        value: 'repotags',
        view: GerritView.REPO,
        url: '',
        detailType: GerritNav.RepoDetailView.TAGS,
        parent: 'my-repo' as RepoName,
      },
      {
        text: 'Dashboards',
        value: 'repodashboards',
        view: GerritView.REPO,
        url: '',
        detailType: GerritNav.RepoDetailView.DASHBOARDS,
        parent: 'my-repo' as RepoName,
      },
    ];
    const navigateToRelativeUrlStub = sinon.stub(
      GerritNav,
      'navigateToRelativeUrl'
    );
    const selectedIsCurrentPageSpy = sinon.spy(
      element,
      '_selectedIsCurrentPage'
    );
    sinon.spy(element, '_handleSubsectionChange');
    await element.reload();
    assert.deepEqual(element._filteredLinks, expectedFilteredLinks);
    assert.deepEqual(element._subsectionLinks, expectedSubsectionLinks);
    assert.equal(
      queryAndAssert<GrDropdownList>(element, '#pageSelect').value,
      'repoaccess'
    );
    assert.isTrue(selectedIsCurrentPageSpy.calledOnce);
    // Doesn't trigger navigation from the page select menu.
    assert.isFalse(navigateToRelativeUrlStub.called);

    // When explicitly changed, navigation is called
    queryAndAssert<GrDropdownList>(element, '#pageSelect').value =
      'repogeneral';
    assert.isTrue(selectedIsCurrentPageSpy.calledTwice);
    assert.isTrue(navigateToRelativeUrlStub.calledOnce);
  });

  test('_selectedIsCurrentPage', () => {
    element._repoName = 'my-repo' as RepoName;
    element.params = {view: GerritView.REPO, repo: 'my-repo' as RepoName};
    const selected = {
      view: GerritView.REPO,
      parent: 'my-repo' as RepoName,
      value: '',
      text: '',
    } as AdminSubsectionLink;
    assert.isTrue(element._selectedIsCurrentPage(selected));
    selected.parent = 'my-second-repo' as RepoName;
    assert.isFalse(element._selectedIsCurrentPage(selected));
    selected.detailType = GerritNav.RepoDetailView.GENERAL;
    assert.isFalse(element._selectedIsCurrentPage(selected));
  });

  suite('_computeSelectedClass', () => {
    setup(() => {
      stubRestApi('getAccountCapabilities').returns(
        Promise.resolve(createAdminCapabilities())
      );
      stubRestApi('getAccount').returns(
        Promise.resolve({
          _id: 1,
          registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
        })
      );
      return element.reload();
    });

    suite('repos', () => {
      setup(() => {
        stub('gr-repo-access', '_repoChanged').callsFake(() =>
          Promise.resolve()
        );
      });

      test('repo list', () => {
        element.params = {
          view: GerritNav.View.ADMIN,
          adminView: 'gr-repo-list',
          openCreateModal: false,
        };
        flush();
        const selected = queryAndAssert(element, 'gr-page-nav .selected');
        assert.isOk(selected);
        assert.equal(selected.textContent!.trim(), 'Repositories');
      });

      test('repo', () => {
        element.params = {
          view: GerritNav.View.REPO,
          repo: 'foo' as RepoName,
        };
        element._repoName = 'foo' as RepoName;
        return element.reload().then(() => {
          flush();
          const selected = queryAndAssert(element, 'gr-page-nav .selected');
          assert.isOk(selected);
          assert.equal(selected.textContent!.trim(), 'foo');
        });
      });

      test('repo access', () => {
        element.params = {
          view: GerritNav.View.REPO,
          detail: GerritNav.RepoDetailView.ACCESS,
          repo: 'foo' as RepoName,
        };
        element._repoName = 'foo' as RepoName;
        return element.reload().then(() => {
          flush();
          const selected = queryAndAssert(element, 'gr-page-nav .selected');
          assert.isOk(selected);
          assert.equal(selected.textContent!.trim(), 'Access');
        });
      });

      test('repo dashboards', () => {
        element.params = {
          view: GerritNav.View.REPO,
          detail: GerritNav.RepoDetailView.DASHBOARDS,
          repo: 'foo' as RepoName,
        };
        element._repoName = 'foo' as RepoName;
        return element.reload().then(() => {
          flush();
          const selected = queryAndAssert(element, 'gr-page-nav .selected');
          assert.isOk(selected);
          assert.equal(selected.textContent!.trim(), 'Dashboards');
        });
      });
    });

    suite('groups', () => {
      let getGroupConfigStub: sinon.SinonStub;

      setup(() => {
        stub('gr-group', 'loadGroup').callsFake(() => Promise.resolve());
        stub('gr-group-members', 'loadGroupDetails').callsFake(() =>
          Promise.resolve()
        );

        getGroupConfigStub = stubRestApi('getGroupConfig');
        getGroupConfigStub.returns(
          Promise.resolve({
            name: 'foo',
            id: 'c0f83e941ce90caea30e6ad88f0d4ea0e841a7a9',
          })
        );
        stubRestApi('getIsGroupOwner').returns(Promise.resolve(true));
        return element.reload();
      });

      test('group list', () => {
        element.params = {
          view: GerritNav.View.ADMIN,
          adminView: 'gr-admin-group-list',
          openCreateModal: false,
        };
        flush();
        const selected = queryAndAssert(element, 'gr-page-nav .selected');
        assert.isOk(selected);
        assert.equal(selected.textContent!.trim(), 'Groups');
      });

      test('internal group', () => {
        element.params = {
          view: GerritNav.View.GROUP,
          groupId: '1234' as GroupId,
        };
        element._groupName = 'foo' as GroupName;
        return element.reload().then(() => {
          flush();
          const subsectionItems = queryAll<HTMLLIElement>(
            element,
            '.subsectionItem'
          );
          assert.equal(subsectionItems.length, 2);
          assert.isTrue(element._groupIsInternal);
          const selected = queryAndAssert(element, 'gr-page-nav .selected');
          assert.isOk(selected);
          assert.equal(selected.textContent!.trim(), 'foo');
        });
      });

      test('external group', () => {
        getGroupConfigStub.returns(
          Promise.resolve({
            name: 'foo',
            id: 'external-id',
          })
        );
        element.params = {
          view: GerritNav.View.GROUP,
          groupId: '1234' as GroupId,
        };
        element._groupName = 'foo' as GroupName;
        return element.reload().then(() => {
          flush();
          const subsectionItems = queryAll<HTMLLIElement>(
            element,
            '.subsectionItem'
          );
          assert.equal(subsectionItems.length, 0);
          assert.isFalse(element._groupIsInternal);
          const selected = queryAndAssert(element, 'gr-page-nav .selected');
          assert.isOk(selected);
          assert.equal(selected.textContent!.trim(), 'foo');
        });
      });

      test('group members', () => {
        element.params = {
          view: GerritNav.View.GROUP,
          detail: GerritNav.GroupDetailView.MEMBERS,
          groupId: '1234' as GroupId,
        };
        element._groupName = 'foo' as GroupName;
        return element.reload().then(() => {
          flush();
          const selected = queryAndAssert(element, 'gr-page-nav .selected');
          assert.isOk(selected);
          assert.equal(selected.textContent!.trim(), 'Members');
        });
      });
    });
  });
});
