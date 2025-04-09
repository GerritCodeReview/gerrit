/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-admin-view';
import {AdminSubsectionLink, GrAdminView} from './gr-admin-view';
import {stubElement, stubRestApi} from '../../../test/test-utils';
import {GerritView} from '../../../services/router/router-model';
import {query, queryAll, queryAndAssert} from '../../../test/test-utils';
import {GrRepoList} from '../gr-repo-list/gr-repo-list';
import {GroupId, GroupName, RepoName, Timestamp} from '../../../types/common';
import {GrDropdownList} from '../../shared/gr-dropdown-list/gr-dropdown-list';
import {GrGroup} from '../gr-group/gr-group';
import {fixture, html, assert} from '@open-wc/testing';
import {AdminChildView} from '../../../models/views/admin';
import {GroupDetailView} from '../../../models/views/group';
import {RepoDetailView} from '../../../models/views/repo';
import {testResolver} from '../../../test/common-test-setup';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {
  PluginLoader,
  pluginLoaderToken,
} from '../../shared/gr-js-api-interface/gr-plugin-loader';

function createAdminCapabilities() {
  return {
    createGroup: true,
    createProject: true,
    viewPlugins: true,
  };
}

suite('gr-admin-view tests', () => {
  let element: GrAdminView;
  let pluginLoader: PluginLoader;

  setup(async () => {
    element = await fixture(html`<gr-admin-view></gr-admin-view>`);
    stubRestApi('getProjectConfig').returns(Promise.resolve(undefined));
    const pluginsLoaded = Promise.resolve();
    pluginLoader = testResolver(pluginLoaderToken);
    sinon.stub(pluginLoader, 'awaitPluginsLoaded').returns(pluginsLoaded);
    await pluginsLoaded;
    await element.updateComplete;
  });

  test('link URLs', () => {
    assert.equal(element.computeLinkURL({name: '', url: '/test'}), '/test');
    assert.equal(element.computeLinkURL({name: '', url: ''}), '');
    assert.equal(element.computeLinkURL(undefined), '');
  });

  test('current page gets selected and is displayed', async () => {
    element.filteredLinks = [
      {
        name: 'Repositories',
        url: '/admin/repos',
        view: 'gr-repo-list' as GerritView,
      },
    ];

    element.view = GerritView.ADMIN;
    element.adminViewState = {
      view: GerritView.ADMIN,
      adminView: AdminChildView.REPOS,
    };

    await element.updateComplete;
    assert.equal(queryAll<HTMLLIElement>(element, '.selected').length, 1);
    assert.ok(queryAndAssert<GrRepoList>(element, 'gr-repo-list'));
    assert.isNotOk(query(element, 'gr-admin-create-repo'));
  });

  test('filteredLinks admin', async () => {
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
    assert.equal(element.filteredLinks!.length, 4);

    // Repos
    assert.isNotOk(element.filteredLinks![0].subsection);

    // Groups
    assert.isNotOk(element.filteredLinks![1].subsection);

    // Plugins
    assert.isNotOk(element.filteredLinks![2].subsection);
  });

  test('filteredLinks non admin authenticated', async () => {
    await element.reload();
    assert.equal(element.filteredLinks!.length, 3);
    // Repos
    assert.isNotOk(element.filteredLinks![0].subsection);
    // Groups
    assert.isNotOk(element.filteredLinks![1].subsection);
  });

  test('filteredLinks non admin unathenticated', async () => {
    stubRestApi('getAccount').returns(Promise.resolve(undefined));
    await element.reload();
    assert.equal(element.filteredLinks!.length, 1);
    // Repos
    assert.isNotOk(element.filteredLinks![0].subsection);
  });

  test('filteredLinks from plugin', () => {
    stubRestApi('getAccount').returns(Promise.resolve(undefined));
    sinon.stub(pluginLoader.jsApiService, 'getAdminMenuLinks').returns([
      {
        capability: null,
        text: 'internal link text',
        url: '/internal/link/url',
      },
      {
        capability: null,
        text: 'external link text',
        url: 'http://external/link/url',
      },
    ]);
    return element.reload().then(() => {
      assert.equal(element.filteredLinks!.length, 3);
      assert.deepEqual(element.filteredLinks![1], {
        capability: undefined,
        url: '/internal/link/url',
        name: 'internal link text',
        view: undefined,
        viewableToAll: true,
        target: null,
      });
      assert.deepEqual(element.filteredLinks![2], {
        capability: undefined,
        url: 'http://external/link/url',
        name: 'external link text',
        view: undefined,
        viewableToAll: true,
        target: '_blank',
      });
    });
  });

  test('Repo shows up in nav', async () => {
    element.view = GerritView.REPO;
    element.repoName = 'Test Repo' as RepoName;
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
    await element.updateComplete;
    assert.equal(queryAll<HTMLLIElement>(element, '.sectionTitle').length, 4);
    assert.equal(
      queryAndAssert<HTMLSpanElement>(element, '.breadcrumbText').innerText,
      'Test Repo'
    );
    assert.equal(
      queryAndAssert<GrDropdownList>(element, '#pageSelect').items!.length,
      8
    );
  });

  test('Group shows up in nav', async () => {
    element.groupId = 'a15262' as GroupId;
    element.groupName = 'my-group' as GroupName;
    element.groupIsInternal = true;
    stubRestApi('getIsAdmin').returns(Promise.resolve(true));
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
    await element.updateComplete;
    assert.equal(element.filteredLinks!.length, 4);
    // Repos
    assert.isNotOk(element.filteredLinks![0].subsection);
    // Groups
    assert.equal(element.filteredLinks![1].subsection!.children!.length, 2);
    assert.equal(element.filteredLinks![1].subsection!.name, 'my-group');
    // Plugins
    assert.isNotOk(element.filteredLinks![2].subsection);
  });

  test('Needs reload when repo changes', async () => {
    stubRestApi('getAccountCapabilities').returns(
      Promise.resolve(createAdminCapabilities())
    );
    stubRestApi('getAccount').returns(
      Promise.resolve({
        _id: 1,
        registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
      })
    );

    element.view = GerritView.REPO;
    element.repoViewState = {repo: 'Repo 1' as RepoName, view: GerritView.REPO};
    assert.isTrue(element.needsReload());
    await element.reload();
    await element.updateComplete;

    element.repoViewState = {repo: 'Repo 2' as RepoName, view: GerritView.REPO};
    assert.isTrue(element.needsReload());
    await element.updateComplete;
  });

  test('Needs reload when group changes', async () => {
    sinon.stub(element, 'computeGroupName');
    stubRestApi('getAccountCapabilities').returns(
      Promise.resolve(createAdminCapabilities())
    );
    stubRestApi('getAccount').returns(
      Promise.resolve({
        _id: 1,
        registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
      })
    );
    element.view = GerritView.GROUP;
    element.groupViewState = {groupId: '1' as GroupId, view: GerritView.GROUP};
    assert.isTrue(element.needsReload());
  });

  test('Needs reload when changing from repo to group', async () => {
    element.repoName = 'Test Repo' as RepoName;
    element.view = GerritView.REPO;
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
    await element.updateComplete;

    sinon.stub(element, 'computeGroupName');
    const groupId = '1' as GroupId;
    element.view = GerritView.GROUP;
    element.groupViewState = {groupId, view: GerritView.GROUP};
    // Check for reload before update. This would normally be done as part of
    // subscribe method that updates the view/viewState.
    assert.isTrue(element.needsReload());
    element.reload();
    await element.updateComplete;

    assert.equal(element.groupId, groupId);
  });

  test('Needs reload when group name changes', async () => {
    const newName = 'newName' as GroupName;
    sinon.stub(element, 'computeGroupName');
    element.view = GerritView.GROUP;
    element.groupViewState = {groupId: '1' as GroupId, view: GerritView.GROUP};
    element.groupName = 'oldName' as GroupName;
    assert.isTrue(element.needsReload());
    await element.reload();
    await element.updateComplete;

    queryAndAssert<GrGroup>(element, 'gr-group').dispatchEvent(
      new CustomEvent('name-changed', {
        detail: {name: newName},
        composed: true,
        bubbles: true,
      })
    );
    assert.equal(element.groupName, newName);
  });

  test('dropdown displays if there is a subsection', async () => {
    element.view = GerritView.REPO;
    assert.isNotOk(query(element, '.mainHeader'));
    element.subsectionLinks = [
      {
        text: 'Home',
        value: 'repo',
        view: GerritView.REPO,
        parent: 'my-repo' as RepoName,
        detailType: undefined,
      },
    ];
    await element.updateComplete;
    assert.isOk(query(element, '.mainHeader'));
    element.subsectionLinks = undefined;
    await element.updateComplete;
    assert.isNotOk(query(element, '.mainHeader'));
  });

  test('Dropdown only triggers navigation on explicit select', async () => {
    element.repoName = 'my-repo' as RepoName;
    element.view = GerritView.REPO;
    element.repoViewState = {
      repo: 'my-repo' as RepoName,
      view: GerritView.REPO,
      detail: RepoDetailView.ACCESS,
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
    await element.updateComplete;

    const expectedFilteredLinks = [
      {
        name: 'Repositories',
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
              url: '/admin/repos/my-repo,general',
              detailType: RepoDetailView.GENERAL,
            },
            {
              name: 'Access',
              view: GerritView.REPO,
              detailType: RepoDetailView.ACCESS,
              url: '/admin/repos/my-repo,access',
            },
            {
              name: 'Commands',
              view: GerritView.REPO,
              detailType: RepoDetailView.COMMANDS,
              url: '/admin/repos/my-repo,commands',
            },
            {
              name: 'Branches',
              view: GerritView.REPO,
              detailType: RepoDetailView.BRANCHES,
              url: '/admin/repos/my-repo,branches',
            },
            {
              name: 'Tags',
              view: GerritView.REPO,
              detailType: RepoDetailView.TAGS,
              url: '/admin/repos/my-repo,tags',
            },
            {
              name: 'Dashboards',
              view: GerritView.REPO,
              detailType: RepoDetailView.DASHBOARDS,
              url: '/admin/repos/my-repo,dashboards',
            },
            {
              name: 'Submit Requirements',
              view: GerritView.REPO,
              detailType: RepoDetailView.SUBMIT_REQUIREMENTS,
              url: '/admin/repos/my-repo,submit-requirements',
            },
          ],
        },
      },
      {
        name: 'Groups',
        section: 'Groups',
        url: '/admin/groups',
        view: 'gr-admin-group-list' as GerritView,
      },
      {
        name: 'Plugins',
        capability: 'viewPlugins',
        section: 'Plugins',
        url: '/admin/plugins',
        view: 'gr-plugin-list' as GerritView,
      },
      {
        name: 'Server Info',
        section: 'Server Info',
        url: '/admin/server-info',
        view: 'gr-server-info' as GerritView,
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
        url: '/admin/repos/my-repo,general',
        detailType: RepoDetailView.GENERAL,
        parent: 'my-repo' as RepoName,
      },
      {
        text: 'Access',
        value: 'repoaccess',
        view: GerritView.REPO,
        url: '/admin/repos/my-repo,access',
        detailType: RepoDetailView.ACCESS,
        parent: 'my-repo' as RepoName,
      },
      {
        text: 'Commands',
        value: 'repocommands',
        view: GerritView.REPO,
        url: '/admin/repos/my-repo,commands',
        detailType: RepoDetailView.COMMANDS,
        parent: 'my-repo' as RepoName,
      },
      {
        text: 'Branches',
        value: 'repobranches',
        view: GerritView.REPO,
        url: '/admin/repos/my-repo,branches',
        detailType: RepoDetailView.BRANCHES,
        parent: 'my-repo' as RepoName,
      },
      {
        text: 'Tags',
        value: 'repotags',
        view: GerritView.REPO,
        url: '/admin/repos/my-repo,tags',
        detailType: RepoDetailView.TAGS,
        parent: 'my-repo' as RepoName,
      },
      {
        text: 'Dashboards',
        value: 'repodashboards',
        view: GerritView.REPO,
        url: '/admin/repos/my-repo,dashboards',
        detailType: RepoDetailView.DASHBOARDS,
        parent: 'my-repo' as RepoName,
      },
      {
        text: 'Submit Requirements',
        value: 'reposubmit-requirements',
        view: GerritView.REPO,
        url: '/admin/repos/my-repo,submit-requirements',
        detailType: RepoDetailView.SUBMIT_REQUIREMENTS,
        parent: 'my-repo' as RepoName,
      },
    ];
    const setUrlStub = sinon.stub(testResolver(navigationToken), 'setUrl');
    const selectedIsCurrentPageSpy = sinon.spy(
      element,
      'selectedIsCurrentPage'
    );
    sinon.spy(element, 'handleSubsectionChange');
    await element.reload();
    await element.updateComplete;
    assert.deepEqual(element.filteredLinks, expectedFilteredLinks);
    assert.deepEqual(element.subsectionLinks, expectedSubsectionLinks);
    assert.equal(
      queryAndAssert<GrDropdownList>(element, '#pageSelect').value,
      'repoaccess'
    );
    assert.equal(selectedIsCurrentPageSpy.callCount, 1);
    // Doesn't trigger navigation from the page select menu.
    assert.isFalse(setUrlStub.called);

    // When explicitly changed, navigation is called
    queryAndAssert<GrDropdownList>(element, '#pageSelect').value =
      'repogeneral';
    await queryAndAssert<GrDropdownList>(element, '#pageSelect').updateComplete;
    assert.equal(selectedIsCurrentPageSpy.callCount, 2);
    assert.isTrue(setUrlStub.calledOnce);
  });

  test('selectedIsCurrentPage', () => {
    element.repoName = 'my-repo' as RepoName;
    element.view = GerritView.REPO;
    element.repoViewState = {
      view: GerritView.REPO,
      repo: 'my-repo' as RepoName,
    };
    const selected = {
      view: GerritView.REPO,
      parent: 'my-repo' as RepoName,
      value: '',
      text: '',
    } as AdminSubsectionLink;
    assert.isTrue(element.selectedIsCurrentPage(selected));
    selected.parent = 'my-second-repo' as RepoName;
    assert.isFalse(element.selectedIsCurrentPage(selected));
    selected.detailType = RepoDetailView.GENERAL;
    assert.isFalse(element.selectedIsCurrentPage(selected));
  });

  suite('computeSelectedClass', () => {
    setup(async () => {
      stubRestApi('getAccountCapabilities').returns(
        Promise.resolve(createAdminCapabilities())
      );
      stubRestApi('getAccount').returns(
        Promise.resolve({
          _id: 1,
          registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
        })
      );
      await element.reload();
    });

    test('render', async () => {
      element.view = GerritView.ADMIN;
      element.adminViewState = {
        view: GerritView.ADMIN,
        adminView: AdminChildView.REPOS,
        openCreateModal: false,
      };
      await element.updateComplete;
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-page-nav class="navStyles">
            <ul class="sectionContent">
              <li class="sectionTitle selected">
                <a class="title" href="/admin/repos" rel="noopener">
                  Repositories
                </a>
              </li>
              <li class="sectionTitle">
                <a class="title" href="/admin/groups" rel="noopener">
                  Groups
                </a>
              </li>
              <li class="sectionTitle">
                <a class="title" href="/admin/plugins" rel="noopener">
                  Plugins
                </a>
              </li>
              <li class="sectionTitle">
                <a class="title" href="/admin/server-info" rel="noopener">
                  Server Info
                </a>
              </li>
            </ul>
          </gr-page-nav>
          <div class="main table">
            <gr-repo-list class="table"></gr-repo-list>
          </div>
        `
      );
    });

    suite('repos', () => {
      setup(() => {
        stubElement('gr-repo-access', '_repoChanged').callsFake(() =>
          Promise.resolve()
        );
      });

      test('repo list', async () => {
        element.view = GerritView.ADMIN;
        element.adminViewState = {
          view: GerritView.ADMIN,
          adminView: AdminChildView.REPOS,
          openCreateModal: false,
        };
        await element.updateComplete;
        const selected = queryAndAssert(element, 'gr-page-nav .selected');
        assert.isOk(selected);
        assert.equal(selected.textContent!.trim(), 'Repositories');
      });

      test('repo', async () => {
        element.view = GerritView.REPO;
        element.repoViewState = {
          view: GerritView.REPO,
          repo: 'foo' as RepoName,
        };
        element.repoName = 'foo' as RepoName;
        await element.reload();
        await element.updateComplete;
        const selected = queryAndAssert(element, 'gr-page-nav .selected');
        assert.isOk(selected);
        assert.equal(selected.textContent!.trim(), 'foo');
      });

      test('repo access', async () => {
        element.view = GerritView.REPO;
        element.repoViewState = {
          view: GerritView.REPO,
          detail: RepoDetailView.ACCESS,
          repo: 'foo' as RepoName,
        };
        element.repoName = 'foo' as RepoName;
        await element.reload();
        await element.updateComplete;
        const selected = queryAndAssert(element, 'gr-page-nav .selected');
        assert.isOk(selected);
        assert.equal(selected.textContent!.trim(), 'Access');
      });

      test('repo dashboards', async () => {
        element.view = GerritView.REPO;
        element.repoViewState = {
          view: GerritView.REPO,
          detail: RepoDetailView.DASHBOARDS,
          repo: 'foo' as RepoName,
        };
        element.repoName = 'foo' as RepoName;
        await element.reload();
        await element.updateComplete;
        const selected = queryAndAssert(element, 'gr-page-nav .selected');
        assert.isOk(selected);
        assert.equal(selected.textContent!.trim(), 'Dashboards');
      });
    });

    suite('groups', () => {
      let getGroupConfigStub: sinon.SinonStub;

      setup(async () => {
        stubElement('gr-group', 'loadGroup').callsFake(() => Promise.resolve());
        stubElement('gr-group-members', 'loadGroupDetails').callsFake(() =>
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
        await element.reload();
      });

      test('group list', async () => {
        element.view = GerritView.ADMIN;
        element.adminViewState = {
          view: GerritView.ADMIN,
          adminView: AdminChildView.GROUPS,
          openCreateModal: false,
        };
        await element.updateComplete;
        const selected = queryAndAssert(element, 'gr-page-nav .selected');
        assert.isOk(selected);
        assert.equal(selected.textContent!.trim(), 'Groups');
      });

      test('internal group', async () => {
        element.view = GerritView.GROUP;
        element.groupViewState = {
          view: GerritView.GROUP,
          groupId: '1234' as GroupId,
        };
        element.groupName = 'foo' as GroupName;
        if (element.needsReload()) await element.reload();
        await element.updateComplete;
        const subsectionItems = queryAll<HTMLLIElement>(
          element,
          '.subsectionItem'
        );
        assert.equal(subsectionItems.length, 2);
        assert.isTrue(element.groupIsInternal);
        const selected = queryAndAssert(element, 'gr-page-nav .selected');
        assert.isOk(selected);
        assert.equal(selected.textContent!.trim(), 'foo');
      });

      test('external group', async () => {
        getGroupConfigStub.returns(
          Promise.resolve({
            name: 'foo',
            id: 'external-id',
          })
        );
        element.view = GerritView.GROUP;
        element.groupViewState = {
          view: GerritView.GROUP,
          groupId: '1234' as GroupId,
        };
        element.groupName = 'foo' as GroupName;
        if (element.needsReload()) await element.reload();
        await element.updateComplete;
        const subsectionItems = queryAll<HTMLLIElement>(
          element,
          '.subsectionItem'
        );
        assert.equal(subsectionItems.length, 0);
        assert.isFalse(element.groupIsInternal);
        const selected = queryAndAssert(element, 'gr-page-nav .selected');
        assert.isOk(selected);
        assert.equal(selected.textContent!.trim(), 'foo');
      });

      test('group members', async () => {
        element.view = GerritView.GROUP;
        element.groupViewState = {
          view: GerritView.GROUP,
          detail: GroupDetailView.MEMBERS,
          groupId: '1234' as GroupId,
        };
        element.groupName = 'foo' as GroupName;
        if (element.needsReload()) await element.reload();
        await element.updateComplete;
        const selected = queryAndAssert(element, 'gr-page-nav .selected');
        assert.isOk(selected);
        assert.equal(selected.textContent!.trim(), 'Members');
      });
    });
  });
});
