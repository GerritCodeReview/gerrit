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

import '../../../test/common-test-setup-karma.js';
import './gr-admin-view.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader.js';
import {mockPromise, stubBaseUrl, stubRestApi} from '../../../test/test-utils.js';
import {GerritView} from '../../../services/router/router-model.js';

const basicFixture = fixtureFromElement('gr-admin-view');

function createAdminCapabilities() {
  return {
    createGroup: true,
    createProject: true,
    viewPlugins: true,
  };
}

suite('gr-admin-view tests', () => {
  let element;

  setup(async () => {
    element = basicFixture.instantiate();
    stubRestApi('getProjectConfig').returns(Promise.resolve({}));
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
        element._computeLinkURL({url: '/test', noBaseUrl: true}),
        '//' + window.location.host + '/test');

    stubBaseUrl('/foo');
    assert.equal(
        element._computeLinkURL({url: '/test', noBaseUrl: true}),
        '//' + window.location.host + '/foo/test');
    assert.equal(element._computeLinkURL({url: '/test'}), '/test');
    assert.equal(
        element._computeLinkURL({url: '/test', target: '_blank'}),
        '/test');
  });

  test('current page gets selected and is displayed', () => {
    element._filteredLinks = [{
      name: 'Repositories',
      url: '/admin/repos',
      view: 'gr-repo-list',
    }];

    element.params = {
      view: 'admin',
      adminView: 'gr-repo-list',
    };

    flush();
    assert.equal(element.root.querySelectorAll(
        '.selected').length, 1);
    assert.ok(element.shadowRoot
        .querySelector('gr-repo-list'));
    assert.isNotOk(element.shadowRoot
        .querySelector('gr-admin-create-repo'));
  });

  test('_filteredLinks admin', async () => {
    stubRestApi('getAccount').returns(Promise.resolve({
      name: 'test-user',
    }));
    stubRestApi('getAccountCapabilities').returns(
        Promise.resolve(createAdminCapabilities()));
    await element.reload();
    assert.equal(element._filteredLinks.length, 3);

    // Repos
    assert.isNotOk(element._filteredLinks[0].subsection);

    // Groups
    assert.isNotOk(element._filteredLinks[0].subsection);

    // Plugins
    assert.isNotOk(element._filteredLinks[0].subsection);
  });

  test('_filteredLinks non admin authenticated', async () => {
    await element.reload();
    assert.equal(element._filteredLinks.length, 2);
    // Repos
    assert.isNotOk(element._filteredLinks[0].subsection);
    // Groups
    assert.isNotOk(element._filteredLinks[0].subsection);
  });

  test('_filteredLinks non admin unathenticated', async () => {
    stubRestApi('getAccount').returns(Promise.resolve(undefined));
    await element.reload();
    assert.equal(element._filteredLinks.length, 1);
    // Repos
    assert.isNotOk(element._filteredLinks[0].subsection);
  });

  test('_filteredLinks from plugin', () => {
    stubRestApi('getAccount').returns(Promise.resolve(undefined));
    sinon.stub(element.jsAPI, 'getAdminMenuLinks').returns([
      {text: 'internal link text', url: '/internal/link/url'},
      {text: 'external link text', url: 'http://external/link/url'},
    ]);
    return element.reload().then(() => {
      assert.equal(element._filteredLinks.length, 3);
      assert.deepEqual(element._filteredLinks[1], {
        capability: undefined,
        url: '/internal/link/url',
        name: 'internal link text',
        noBaseUrl: true,
        view: null,
        viewableToAll: true,
        target: null,
      });
      assert.deepEqual(element._filteredLinks[2], {
        capability: undefined,
        url: 'http://external/link/url',
        name: 'external link text',
        noBaseUrl: false,
        view: null,
        viewableToAll: true,
        target: '_blank',
      });
    });
  });

  test('Repo shows up in nav', async () => {
    element._repoName = 'Test Repo';
    stubRestApi('getAccount').returns(Promise.resolve({
      name: 'test-user',
    }));
    stubRestApi('getAccountCapabilities').returns(
        Promise.resolve(createAdminCapabilities()));
    await element.reload();
    await flush();
    assert.equal(dom(element.root)
        .querySelectorAll('.sectionTitle').length, 3);
    assert.equal(element.shadowRoot
        .querySelector('.breadcrumbText').innerText, 'Test Repo');
    assert.equal(
        element.shadowRoot.querySelector('#pageSelect').items.length,
        7
    );
  });

  test('Group shows up in nav', async () => {
    element._groupId = 'a15262';
    element._groupName = 'my-group';
    element._groupIsInternal = true;
    element._isAdmin = true;
    element._groupOwner = false;
    stubRestApi('getAccount').returns(Promise.resolve({name: 'test-user'}));
    stubRestApi('getAccountCapabilities').returns(
        Promise.resolve(createAdminCapabilities()));
    await element.reload();
    await flush();
    assert.equal(element._filteredLinks.length, 3);
    // Repos
    assert.isNotOk(element._filteredLinks[0].subsection);
    // Groups
    assert.equal(element._filteredLinks[1].subsection.children.length, 2);
    assert.equal(element._filteredLinks[1].subsection.name, 'my-group');
    // Plugins
    assert.isNotOk(element._filteredLinks[2].subsection);
  });

  test('Nav is reloaded when repo changes', () => {
    stubRestApi('getAccountCapabilities').returns(
        Promise.resolve(createAdminCapabilities()));
    stubRestApi('getAccount').returns(Promise.resolve({_id: 1}));
    sinon.stub(element, 'reload');
    element.params = {repo: 'Test Repo', view: GerritView.REPO};
    assert.equal(element.reload.callCount, 1);
    element.params = {repo: 'Test Repo 2',
      view: GerritView.REPO};
    assert.equal(element.reload.callCount, 2);
  });

  test('Nav is reloaded when group changes', () => {
    sinon.stub(element, '_computeGroupName');
    stubRestApi('getAccountCapabilities').returns(
        Promise.resolve(createAdminCapabilities()));
    stubRestApi('getAccount').returns(Promise.resolve({_id: 1}));
    sinon.stub(element, 'reload');
    element.params = {groupId: '1', view: GerritView.GROUP};
    assert.equal(element.reload.callCount, 1);
  });

  test('Nav is reloaded when group name changes', async () => {
    const newName = 'newName';
    const reloadCalled = mockPromise();
    sinon.stub(element, '_computeGroupName');
    sinon.stub(element, 'reload').callsFake(() => {
      assert.equal(element._groupName, newName);
      reloadCalled.resolve();
    });
    element.params = {group: 1, view: GerritNav.View.GROUP};
    element._groupName = 'oldName';
    await flush();
    element.shadowRoot
        .querySelector('gr-group').dispatchEvent(
            new CustomEvent('name-changed', {
              detail: {name: newName},
              composed: true, bubbles: true,
            }));
    await reloadCalled;
  });

  test('dropdown displays if there is a subsection', () => {
    assert.isNotOk(element.shadowRoot
        .querySelector('.mainHeader'));
    element._subsectionLinks = [
      {
        text: 'Home',
        value: 'repo',
        view: 'repo',
        parent: 'my-repo',
        detailType: undefined,
      },
    ];
    flush();
    assert.isOk(element.shadowRoot
        .querySelector('.mainHeader'));
    element._subsectionLinks = undefined;
    flush();
    assert.equal(
        getComputedStyle(element.shadowRoot
            .querySelector('.mainHeader')).display,
        'none');
  });

  test('Dropdown only triggers navigation on explicit select', async () => {
    element._repoName = 'my-repo';
    element.params = {
      repo: 'my-repo',
      view: GerritNav.View.REPO,
      detail: GerritNav.RepoDetailView.ACCESS,
    };
    stubRestApi('getAccountCapabilities').returns(
        Promise.resolve(createAdminCapabilities()));
    stubRestApi('getAccount').returns(Promise.resolve({_id: 1}));
    await flush();
    const expectedFilteredLinks = [
      {
        name: 'Repositories',
        noBaseUrl: true,
        url: '/admin/repos',
        view: 'gr-repo-list',
        viewableToAll: true,
        subsection: {
          name: 'my-repo',
          view: 'repo',
          children: [
            {
              name: 'General',
              view: 'repo',
              url: '',
              detailType: 'general',
            },
            {
              name: 'Access',
              view: 'repo',
              detailType: 'access',
              url: '',
            },
            {
              name: 'Commands',
              view: 'repo',
              detailType: 'commands',
              url: '',
            },
            {
              name: 'Branches',
              view: 'repo',
              detailType: 'branches',
              url: '',
            },
            {
              name: 'Tags',
              view: 'repo',
              detailType: 'tags',
              url: '',
            },
            {
              name: 'Dashboards',
              view: 'repo',
              detailType: 'dashboards',
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
        view: 'gr-admin-group-list',
      },
      {
        name: 'Plugins',
        capability: 'viewPlugins',
        section: 'Plugins',
        noBaseUrl: true,
        url: '/admin/plugins',
        view: 'gr-plugin-list',
      },
    ];
    const expectedSubsectionLinks = [
      {
        text: 'Home',
        value: 'repo',
        view: 'repo',
        url: undefined,
        parent: 'my-repo',
        detailType: undefined,
      },
      {
        text: 'General',
        value: 'repogeneral',
        view: 'repo',
        url: '',
        detailType: 'general',
        parent: 'my-repo',
      },
      {
        text: 'Access',
        value: 'repoaccess',
        view: 'repo',
        url: '',
        detailType: 'access',
        parent: 'my-repo',
      },
      {
        text: 'Commands',
        value: 'repocommands',
        view: 'repo',
        url: '',
        detailType: 'commands',
        parent: 'my-repo',
      },
      {
        text: 'Branches',
        value: 'repobranches',
        view: 'repo',
        url: '',
        detailType: 'branches',
        parent: 'my-repo',
      },
      {
        text: 'Tags',
        value: 'repotags',
        view: 'repo',
        url: '',
        detailType: 'tags',
        parent: 'my-repo',
      },
      {
        text: 'Dashboards',
        value: 'repodashboards',
        view: 'repo',
        url: '',
        detailType: 'dashboards',
        parent: 'my-repo',
      },
    ];
    sinon.stub(GerritNav, 'navigateToRelativeUrl');
    sinon.spy(element, '_selectedIsCurrentPage');
    sinon.spy(element, '_handleSubsectionChange');
    await element.reload();
    assert.deepEqual(element._filteredLinks, expectedFilteredLinks);
    assert.deepEqual(element._subsectionLinks, expectedSubsectionLinks);
    assert.equal(
        element.shadowRoot.querySelector('#pageSelect').value,
        'repoaccess'
    );
    assert.isTrue(element._selectedIsCurrentPage.calledOnce);
    // Doesn't trigger navigation from the page select menu.
    assert.isFalse(GerritNav.navigateToRelativeUrl.called);

    // When explicitly changed, navigation is called
    element.shadowRoot.querySelector('#pageSelect').value = 'repogeneral';
    assert.isTrue(element._selectedIsCurrentPage.calledTwice);
    assert.isTrue(GerritNav.navigateToRelativeUrl.calledOnce);
  });

  test('_selectedIsCurrentPage', () => {
    element._repoName = 'my-repo';
    element.params = {view: 'repo', repo: 'my-repo'};
    const selected = {
      view: 'repo',
      detailType: undefined,
      parent: 'my-repo',
    };
    assert.isTrue(element._selectedIsCurrentPage(selected));
    selected.parent = 'my-second-repo';
    assert.isFalse(element._selectedIsCurrentPage(selected));
    selected.detailType = 'detailType';
    assert.isFalse(element._selectedIsCurrentPage(selected));
  });

  suite('_computeSelectedClass', () => {
    setup(() => {
      stubRestApi('getAccountCapabilities').returns(
          Promise.resolve(createAdminCapabilities()));
      stubRestApi('getAccount').returns(Promise.resolve({_id: 1}));
      return element.reload();
    });

    suite('repos', () => {
      setup(() => {
        stub('gr-repo-access', '_repoChanged').callsFake(() => {});
      });

      test('repo list', () => {
        element.params = {
          view: GerritNav.View.ADMIN,
          adminView: 'gr-repo-list',
          openCreateModal: false,
        };
        flush();
        const selected = element.shadowRoot
            .querySelector('gr-page-nav .selected');
        assert.isOk(selected);
        assert.equal(selected.textContent.trim(), 'Repositories');
      });

      test('repo', () => {
        element.params = {
          view: GerritNav.View.REPO,
          repoName: 'foo',
        };
        element._repoName = 'foo';
        return element.reload().then(() => {
          flush();
          const selected = element.shadowRoot
              .querySelector('gr-page-nav .selected');
          assert.isOk(selected);
          assert.equal(selected.textContent.trim(), 'foo');
        });
      });

      test('repo access', () => {
        element.params = {
          view: GerritNav.View.REPO,
          detail: GerritNav.RepoDetailView.ACCESS,
          repoName: 'foo',
        };
        element._repoName = 'foo';
        return element.reload().then(() => {
          flush();
          const selected = element.shadowRoot
              .querySelector('gr-page-nav .selected');
          assert.isOk(selected);
          assert.equal(selected.textContent.trim(), 'Access');
        });
      });

      test('repo dashboards', () => {
        element.params = {
          view: GerritNav.View.REPO,
          detail: GerritNav.RepoDetailView.DASHBOARDS,
          repoName: 'foo',
        };
        element._repoName = 'foo';
        return element.reload().then(() => {
          flush();
          const selected = element.shadowRoot
              .querySelector('gr-page-nav .selected');
          assert.isOk(selected);
          assert.equal(selected.textContent.trim(), 'Dashboards');
        });
      });
    });

    suite('groups', () => {
      let getGroupConfigStub;
      setup(() => {
        stub('gr-group', 'loadGroup').callsFake(() => Promise.resolve({}));
        stub('gr-group-members', '_loadGroupDetails').callsFake(() => {});

        getGroupConfigStub = stubRestApi('getGroupConfig');
        getGroupConfigStub.returns(Promise.resolve({
          name: 'foo',
          id: 'c0f83e941ce90caea30e6ad88f0d4ea0e841a7a9',
        }));
        stubRestApi('getIsGroupOwner')
            .returns(Promise.resolve(true));
        return element.reload();
      });

      test('group list', () => {
        element.params = {
          view: GerritNav.View.ADMIN,
          adminView: 'gr-admin-group-list',
          openCreateModal: false,
        };
        flush();
        const selected = element.shadowRoot
            .querySelector('gr-page-nav .selected');
        assert.isOk(selected);
        assert.equal(selected.textContent.trim(), 'Groups');
      });

      test('internal group', () => {
        element.params = {
          view: GerritNav.View.GROUP,
          groupId: 1234,
        };
        element._groupName = 'foo';
        return element.reload().then(() => {
          flush();
          const subsectionItems = dom(element.root)
              .querySelectorAll('.subsectionItem');
          assert.equal(subsectionItems.length, 2);
          assert.isTrue(element._groupIsInternal);
          const selected = element.shadowRoot
              .querySelector('gr-page-nav .selected');
          assert.isOk(selected);
          assert.equal(selected.textContent.trim(), 'foo');
        });
      });

      test('external group', () => {
        getGroupConfigStub.returns(Promise.resolve({
          name: 'foo',
          id: 'external-id',
        }));
        element.params = {
          view: GerritNav.View.GROUP,
          groupId: 1234,
        };
        element._groupName = 'foo';
        return element.reload().then(() => {
          flush();
          const subsectionItems = dom(element.root)
              .querySelectorAll('.subsectionItem');
          assert.equal(subsectionItems.length, 0);
          assert.isFalse(element._groupIsInternal);
          const selected = element.shadowRoot
              .querySelector('gr-page-nav .selected');
          assert.isOk(selected);
          assert.equal(selected.textContent.trim(), 'foo');
        });
      });

      test('group members', () => {
        element.params = {
          view: GerritNav.View.GROUP,
          detail: GerritNav.GroupDetailView.MEMBERS,
          groupId: 1234,
        };
        element._groupName = 'foo';
        return element.reload().then(() => {
          flush();
          const selected = element.shadowRoot
              .querySelector('gr-page-nav .selected');
          assert.isOk(selected);
          assert.equal(selected.textContent.trim(), 'Members');
        });
      });
    });
  });
});

