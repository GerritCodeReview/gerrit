/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import {assert} from '@open-wc/testing';
import '../../test/common-test-setup';
import {assertRouteFalse, assertRouteState} from '../../test/test-utils';
import {GerritView} from '../../services/router/router-model';
import {
  AdminChildView,
  AdminNavLinksOption,
  AdminViewState,
  createAdminUrl,
  getAdminLinks,
  PLUGIN_LIST_ROUTE,
} from './admin';
import {
  AccountDetailInfo,
  GroupId,
  RepoName,
  Timestamp,
} from '../../api/rest-api';

suite('admin links', () => {
  let capabilityStub: sinon.SinonStub;
  let menuLinkStub: sinon.SinonStub;

  setup(() => {
    capabilityStub = sinon.stub();
    menuLinkStub = sinon.stub().returns([]);
  });

  const testAdminLinks = async (
    account: AccountDetailInfo | undefined,
    options: AdminNavLinksOption | undefined,
    expected: any
  ) => {
    const res = await getAdminLinks(
      account,
      capabilityStub,
      menuLinkStub,
      options
    );

    assert.equal(expected.totalLength, res.links.length);
    assert.equal(res.links[0].name, 'Repositories');
    // Repos
    if (expected.groupListShown) {
      assert.equal(res.links[1].name, 'Groups');
    }

    if (expected.pluginListShown) {
      assert.equal(res.links[2].name, 'Plugins');
      assert.isNotOk(res.links[2].subsection);
    }

    if (expected.serverInfoShown) {
      assert.equal(res.links[3].name, 'Server Info');
      assert.isNotOk(res.links[3].subsection);
    }

    if (expected.projectPageShown) {
      assert.isOk(res.links[0].subsection);
      assert.equal(res.links[0].subsection.children!.length, 8);
    } else {
      assert.isNotOk(res.links[0].subsection);
    }
    // Groups
    if (expected.groupPageShown) {
      assert.isOk(res.links[1].subsection);
      assert.equal(
        res.links[1].subsection.children!.length,
        expected.groupSubpageLength
      );
    } else if (expected.totalLength > 1) {
      assert.isNotOk(res.links[1].subsection);
    }

    if (expected.pluginGeneratedLinks) {
      for (const link of expected.pluginGeneratedLinks) {
        const linkMatch = res.links.find(
          l => l.url === link.url && l.name === link.text
        );
        assert.isOk(linkMatch);

        // External links should open in new tab.
        if (link.url[0] !== '/') {
          assert.equal(linkMatch.target, '_blank');
        } else {
          assert.isNotOk(linkMatch.target);
        }
      }
    }

    // Current section
    if (expected.projectPageShown || expected.groupPageShown) {
      assert.isOk(res.expandedSection);
      assert.isOk(res.expandedSection.children);
    } else {
      assert.isNotOk(res.expandedSection);
    }
    if (expected.projectPageShown) {
      assert.equal(res.expandedSection!.name, 'my-repo');
      assert.equal(res.expandedSection!.children!.length, 8);
    } else if (expected.groupPageShown) {
      assert.equal(res.expandedSection!.name, 'my-group');
      assert.equal(
        res.expandedSection!.children!.length,
        expected.groupSubpageLength
      );
    }
  };

  suite('logged out', () => {
    let expected: any;

    setup(() => {
      expected = {
        groupListShown: false,
        groupPageShown: false,
        pluginListShown: false,
        serverInfoShown: false,
      };
    });

    test('without a specific repo or group', async () => {
      let options;
      expected = Object.assign(expected, {
        totalLength: 1,
        projectPageShown: false,
      });
      await testAdminLinks(undefined, options, expected);
    });

    test('with a repo', async () => {
      const options = {repoName: 'my-repo' as RepoName};
      expected = Object.assign(expected, {
        totalLength: 1,
        projectPageShown: true,
      });
      await testAdminLinks(undefined, options, expected);
    });

    test('with plugin generated links', async () => {
      let options;
      const generatedLinks = [
        {text: 'internal link text', url: '/internal/link/url'},
        {text: 'external link text', url: 'http://external/link/url'},
      ];
      menuLinkStub.returns(generatedLinks);
      expected = Object.assign(expected, {
        totalLength: 3,
        projectPageShown: false,
        pluginGeneratedLinks: generatedLinks,
      });
      await testAdminLinks(undefined, options, expected);
    });
  });

  suite('no plugin capability logged in', () => {
    const account = {
      name: 'test-user',
      registered_on: '' as Timestamp,
    };
    let expected: any;

    setup(() => {
      expected = {
        totalLength: 3,
        pluginListShown: false,
      };
      capabilityStub.returns(Promise.resolve({}));
    });

    test('without a specific project or group', async () => {
      let options;
      expected = Object.assign(expected, {
        projectPageShown: false,
        groupListShown: true,
        groupPageShown: false,
      });
      await testAdminLinks(account, options, expected);
    });

    test('with a repo', async () => {
      const account = {
        name: 'test-user',
        registered_on: '' as Timestamp,
      };
      const options = {repoName: 'my-repo' as RepoName};
      expected = Object.assign(expected, {
        projectPageShown: true,
        groupListShown: true,
        groupPageShown: false,
      });
      await testAdminLinks(account, options, expected);
    });
  });

  suite('view plugin capability logged in', () => {
    const account = {
      name: 'test-user',
      registered_on: '' as Timestamp,
    };
    let expected: any;

    setup(() => {
      capabilityStub.returns(Promise.resolve({viewPlugins: true}));
      expected = {
        totalLength: 4,
        groupListShown: true,
        pluginListShown: true,
        serverInfoShown: true,
      };
    });

    test('without a specific repo or group', async () => {
      let options;
      expected = Object.assign(expected, {
        projectPageShown: false,
        groupPageShown: false,
      });
      await testAdminLinks(account, options, expected);
    });

    test('with a repo', async () => {
      const options = {repoName: 'my-repo' as RepoName};
      expected = Object.assign(expected, {
        projectPageShown: true,
        groupPageShown: false,
      });
      await testAdminLinks(account, options, expected);
    });

    test('admin with internal group', async () => {
      const options = {
        groupId: 'a15262' as GroupId,
        groupName: 'my-group',
        groupIsInternal: true,
        isAdmin: true,
        groupOwner: false,
      };
      expected = Object.assign(expected, {
        projectPageShown: false,
        groupPageShown: true,
        groupSubpageLength: 2,
      });
      await testAdminLinks(account, options, expected);
    });

    test('group owner with internal group', async () => {
      const options = {
        groupId: 'a15262' as GroupId,
        groupName: 'my-group',
        groupIsInternal: true,
        isAdmin: false,
        groupOwner: true,
      };
      expected = Object.assign(expected, {
        projectPageShown: false,
        groupPageShown: true,
        groupSubpageLength: 2,
      });
      await testAdminLinks(account, options, expected);
    });

    test('non owner or admin with internal group', async () => {
      const options = {
        groupId: 'a15262' as GroupId,
        groupName: 'my-group',
        groupIsInternal: true,
        isAdmin: false,
        groupOwner: false,
      };
      expected = Object.assign(expected, {
        projectPageShown: false,
        groupPageShown: true,
        groupSubpageLength: 1,
      });
      await testAdminLinks(account, options, expected);
    });

    test('admin with external group', async () => {
      const options = {
        groupId: 'a15262' as GroupId,
        groupName: 'my-group',
        groupIsInternal: false,
        isAdmin: true,
        groupOwner: true,
      };
      expected = Object.assign(expected, {
        projectPageShown: false,
        groupPageShown: true,
        groupSubpageLength: 0,
      });
      await testAdminLinks(account, options, expected);
    });
  });

  suite('view plugin screen with plugin capability', () => {
    const account = {
      name: 'test-user',
      registered_on: '' as Timestamp,
    };
    let expected: any;

    setup(() => {
      capabilityStub.returns(Promise.resolve({pluginCapability: true}));
      expected = {};
    });

    test('with plugin with capabilities', async () => {
      let options;
      const generatedLinks = [
        {text: 'without capability', url: '/without'},
        {text: 'with capability', url: '/with', capability: 'pluginCapability'},
      ];
      menuLinkStub.returns(generatedLinks);
      expected = Object.assign(expected, {
        totalLength: 5,
        pluginGeneratedLinks: generatedLinks,
      });
      await testAdminLinks(account, options, expected);
    });
  });

  suite('view plugin screen without plugin capability', () => {
    const account = {
      name: 'test-user',
      registered_on: '' as Timestamp,
    };
    let expected: any;

    setup(() => {
      capabilityStub.returns(Promise.resolve({}));
      expected = {};
    });

    test('with plugin with capabilities', async () => {
      let options;
      const generatedLinks = [
        {text: 'without capability', url: '/without'},
        {text: 'with capability', url: '/with', capability: 'pluginCapability'},
      ];
      menuLinkStub.returns(generatedLinks);
      expected = Object.assign(expected, {
        totalLength: 4,
        pluginGeneratedLinks: [generatedLinks[0]],
      });
      await testAdminLinks(account, options, expected);
    });
  });
});

suite('admin view model', () => {
  suite('routes', () => {
    test('PLUGIN_LIST', () => {
      assertRouteFalse(PLUGIN_LIST_ROUTE, 'admin/plugins');
      assertRouteFalse(PLUGIN_LIST_ROUTE, '//admin/plugins');
      assertRouteFalse(PLUGIN_LIST_ROUTE, '//admin/plugins?');
      assertRouteFalse(PLUGIN_LIST_ROUTE, '/admin/plugins//');

      const state: AdminViewState = {
        view: GerritView.ADMIN,
        adminView: AdminChildView.PLUGINS,
      };
      assertRouteState<AdminViewState>(
        PLUGIN_LIST_ROUTE,
        '/admin/plugins',
        state,
        createAdminUrl
      );
    });
  });
});
