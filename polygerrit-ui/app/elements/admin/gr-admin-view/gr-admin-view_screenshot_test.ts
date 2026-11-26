/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-admin-view';
import {fixture, html, waitUntil} from '@open-wc/testing';
import {setViewport} from '@web/test-runner-commands';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrAdminView} from './gr-admin-view';
import {
  queryAndAssert,
  stubRestApi,
  visualDiffDarkTheme,
} from '../../../test/test-utils';
import {testResolver} from '../../../test/common-test-setup';
import {
  PluginLoader,
  pluginLoaderToken,
} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {GerritView} from '../../../services/router/router-model';
import {AdminChildView} from '../../../models/views/admin';
import {RepoDetailView} from '../../../models/views/repo';
import {
  GitRef,
  GroupId,
  GroupName,
  InheritedBooleanInfo,
  ProjectAccessInfo,
  RepoName,
  Timestamp,
} from '../../../types/common';
import {
  InheritedBooleanInfoConfiguredValue,
  PermissionAction,
  RefStorage,
} from '../../../constants/constants';
import {
  createConfig,
  createInheritedBoolean,
  createServerInfo,
} from '../../../test/test-data-generators';
import {GrRepo} from '../gr-repo/gr-repo';

function createInheritedOption(value = false): InheritedBooleanInfo {
  return {
    ...createInheritedBoolean(value),
    configured_value: InheritedBooleanInfoConfiguredValue.INHERIT,
    inherited_value: value,
  };
}

suite('gr-admin-view screenshot tests', () => {
  let element: GrAdminView;
  let pluginLoader: PluginLoader;
  let getProjectConfigStub: sinon.SinonStub;

  setup(async () => {
    await setViewport({width: 1200, height: 100});
    element = await fixture(html`<gr-admin-view></gr-admin-view>`);

    getProjectConfigStub = stubRestApi('getProjectConfig');
    getProjectConfigStub.returns(Promise.resolve(undefined));

    const pluginsLoaded = Promise.resolve();
    pluginLoader = testResolver(pluginLoaderToken);
    sinon.stub(pluginLoader, 'awaitPluginsLoaded').returns(pluginsLoaded);
    await pluginsLoaded;
    await element.updateComplete;
  });

  test('view screenshot', async () => {
    stubRestApi('getAccount').returns(
      Promise.resolve({
        _id: 1,
        name: 'test-user',
        registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
      })
    );
    stubRestApi('getAccountCapabilities').returns(
      Promise.resolve({
        createGroup: true,
        createProject: true,
        viewPlugins: true,
      })
    );

    element.view = GerritView.ADMIN;
    element.adminViewState = {
      view: GerritView.ADMIN,
      adminView: AdminChildView.REPOS,
      openCreateModal: false,
    };

    await element.reload();
    await element.updateComplete;

    await visualDiff(element, 'gr-admin-view');
    await visualDiffDarkTheme(element, 'gr-admin-view');
  });

  test('repo view screenshot', async () => {
    const repoName = 'test-repo' as RepoName;

    stubRestApi('getAccount').returns(
      Promise.resolve({
        _id: 1,
        name: 'test-user',
        registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
      })
    );
    stubRestApi('getAccountCapabilities').returns(
      Promise.resolve({
        createGroup: true,
        createProject: true,
        viewPlugins: true,
      })
    );

    stubRestApi('getLoggedIn').returns(Promise.resolve(true));
    stubRestApi('getConfig').returns(Promise.resolve(createServerInfo()));

    const repoAccess: ProjectAccessInfo = {
      revision: 'xxxx',
      local: {
        'refs/*': {
          permissions: {
            owner: {
              rules: {
                xxx: {
                  action: PermissionAction.ALLOW,
                  force: false,
                },
              },
            },
          },
        },
      },
      is_owner: true,
      owner_of: ['refs/*'] as GitRef[],
      groups: {
        xxxx: {
          id: 'xxxx' as GroupId,
          url: 'test',
          name: 'test' as GroupName,
        },
      },
      config_web_links: [{name: 'gitiles', url: 'test'}],
    };

    stubRestApi('getRepoAccess').returns(
      Promise.resolve({
        [repoName]: repoAccess,
      })
    );

    getProjectConfigStub.returns(
      Promise.resolve({
        ...createConfig(),
        description: 'Example repository for code review.',
        ref_storage: RefStorage.FILES,
        use_content_merge: createInheritedOption(true),
        create_new_change_for_all_not_in_target: createInheritedOption(),
        require_change_id: createInheritedOption(true),
        enable_signed_push: createInheritedOption(),
        require_signed_push: createInheritedOption(),
        reject_implicit_merges: createInheritedOption(true),
        enable_reviewer_by_email: createInheritedOption(),
        private_by_default: createInheritedOption(),
        work_in_progress_by_default: createInheritedOption(),
        match_author_to_committer_date: createInheritedOption(),
        reject_empty_commit: createInheritedOption(),
        use_contributor_agreements: createInheritedOption(),
        use_signed_off_by: createInheritedOption(),
      })
    );

    element.view = GerritView.REPO;
    element.repoName = repoName;
    element.repoViewState = {
      view: GerritView.REPO,
      repo: repoName,
      detail: RepoDetailView.GENERAL,
    };

    await element.reload();
    await element.updateComplete;

    const repo = queryAndAssert<GrRepo>(element, 'gr-repo');
    await waitUntil(() => !repo.loading && !!repo.repoConfig && !repo.readOnly);
    await repo.updateComplete;

    await visualDiff(element, 'gr-admin-view-repo');
    await visualDiffDarkTheme(element, 'gr-admin-view-repo');
  });

  test('mobile view screenshot', async () => {
    await setViewport({width: 390, height: 900});
    stubRestApi('getAccount').returns(
      Promise.resolve({
        _id: 1,
        name: 'test-user',
        registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
      })
    );
    stubRestApi('getAccountCapabilities').returns(
      Promise.resolve({
        createGroup: true,
        createProject: true,
        viewPlugins: true,
      })
    );

    element.view = GerritView.ADMIN;
    element.adminViewState = {
      view: GerritView.ADMIN,
      adminView: AdminChildView.REPOS,
      openCreateModal: false,
    };

    await element.reload();
    await element.updateComplete;

    await visualDiff(element, 'gr-admin-view-mobile');
    await visualDiffDarkTheme(element, 'gr-admin-view-mobile');
  });

  test('mobile repo view screenshot', async () => {
    await setViewport({width: 390, height: 900});

    const repoName = 'test-repo' as RepoName;

    stubRestApi('getAccount').returns(
      Promise.resolve({
        _id: 1,
        name: 'test-user',
        registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
      })
    );
    stubRestApi('getAccountCapabilities').returns(
      Promise.resolve({
        createGroup: true,
        createProject: true,
        viewPlugins: true,
      })
    );

    stubRestApi('getLoggedIn').returns(Promise.resolve(true));
    stubRestApi('getConfig').returns(Promise.resolve(createServerInfo()));

    const repoAccess: ProjectAccessInfo = {
      revision: 'xxxx',
      local: {
        'refs/*': {
          permissions: {
            owner: {
              rules: {
                xxx: {
                  action: PermissionAction.ALLOW,
                  force: false,
                },
              },
            },
          },
        },
      },
      is_owner: true,
      owner_of: ['refs/*'] as GitRef[],
      groups: {
        xxxx: {
          id: 'xxxx' as GroupId,
          url: 'test',
          name: 'test' as GroupName,
        },
      },
      config_web_links: [{name: 'gitiles', url: 'test'}],
    };

    stubRestApi('getRepoAccess').returns(
      Promise.resolve({
        [repoName]: repoAccess,
      })
    );

    getProjectConfigStub.returns(
      Promise.resolve({
        ...createConfig(),
        description: 'Example repository for code review.',
        ref_storage: RefStorage.FILES,
        use_content_merge: createInheritedOption(true),
        create_new_change_for_all_not_in_target: createInheritedOption(),
        require_change_id: createInheritedOption(true),
        enable_signed_push: createInheritedOption(),
        require_signed_push: createInheritedOption(),
        reject_implicit_merges: createInheritedOption(true),
        enable_reviewer_by_email: createInheritedOption(),
        private_by_default: createInheritedOption(),
        work_in_progress_by_default: createInheritedOption(),
        match_author_to_committer_date: createInheritedOption(),
        reject_empty_commit: createInheritedOption(),
        use_contributor_agreements: createInheritedOption(),
        use_signed_off_by: createInheritedOption(),
      })
    );

    element.view = GerritView.REPO;
    element.repoName = repoName;
    element.repoViewState = {
      view: GerritView.REPO,
      repo: repoName,
      detail: RepoDetailView.GENERAL,
    };

    await element.reload();
    await element.updateComplete;

    const repo = queryAndAssert<GrRepo>(element, 'gr-repo');
    await waitUntil(() => !repo.loading && !!repo.repoConfig && !repo.readOnly);
    await repo.updateComplete;

    await visualDiff(element, 'gr-admin-view-mobile-repo');
    await visualDiffDarkTheme(element, 'gr-admin-view-mobile-repo');
  });
});
