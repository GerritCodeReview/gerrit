/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-router';
import {Page, PageContext} from './gr-page';
import {
  stubBaseUrl,
  stubRestApi,
  addListenerForTest,
  waitUntilCalled,
  mockPromise,
  MockPromise,
} from '../../../test/test-utils';
import {GrRouter, routerToken} from './gr-router';
import {GerritView} from '../../../services/router/router-model';
import {
  BasePatchSetNum,
  NumericChangeId,
  PARENT,
  RepoName,
  RevisionPatchSetNum,
  UrlEncodedCommentId,
} from '../../../types/common';
import {AppElementJustRegisteredParams} from '../../gr-app-types';
import {assert} from '@open-wc/testing';
import {AdminChildView, AdminViewState} from '../../../models/views/admin';
import {RepoDetailView} from '../../../models/views/repo';
import {GroupDetailView} from '../../../models/views/group';
import {ChangeChildView} from '../../../models/views/change';
import {PatchRangeParams} from '../../../utils/url-util';
import {testResolver} from '../../../test/common-test-setup';
import {
  createAdminPluginsViewState,
  createAdminReposViewState,
  createChangeViewState,
  createComment,
  createDashboardViewState,
  createDiff,
  createDiffViewState,
  createEditViewState,
  createGroupViewState,
  createParsedChange,
  createRepoBranchesViewState,
  createRepoTagsViewState,
  createRepoViewState,
  createRevision,
  createSearchViewState,
} from '../../../test/test-data-generators';
import {ParsedChangeInfo} from '../../../types/types';
import {ViewState} from '../../../models/views/base';
import {DashboardType} from '../../../models/views/dashboard';

suite('gr-router tests', () => {
  let router: GrRouter;
  let page: Page;

  setup(() => {
    router = testResolver(routerToken);
    page = router.page;
  });

  teardown(async () => {
    router.finalize();
  });

  test('getHashFromCanonicalPath', () => {
    let url = '/foo/bar';
    let hash = router.getHashFromCanonicalPath(url);
    assert.equal(hash, '');

    url = '';
    hash = router.getHashFromCanonicalPath(url);
    assert.equal(hash, '');

    url = '/foo#bar';
    hash = router.getHashFromCanonicalPath(url);
    assert.equal(hash, 'bar');

    url = '/foo#bar#baz';
    hash = router.getHashFromCanonicalPath(url);
    assert.equal(hash, 'bar#baz');

    url = '#foo#bar#baz';
    hash = router.getHashFromCanonicalPath(url);
    assert.equal(hash, 'foo#bar#baz');
  });

  suite('parseLineAddress', () => {
    test('returns null for empty and invalid hashes', () => {
      let actual = router.parseLineAddress('');
      assert.isNull(actual);

      actual = router.parseLineAddress('foobar');
      assert.isNull(actual);

      actual = router.parseLineAddress('foo123');
      assert.isNull(actual);

      actual = router.parseLineAddress('123bar');
      assert.isNull(actual);
    });

    test('parses correctly', () => {
      let actual = router.parseLineAddress('1234');
      assert.isOk(actual);
      assert.equal(actual!.lineNum, 1234);
      assert.isFalse(actual!.leftSide);

      actual = router.parseLineAddress('a4');
      assert.isOk(actual);
      assert.equal(actual!.lineNum, 4);
      assert.isTrue(actual!.leftSide);

      actual = router.parseLineAddress('b77');
      assert.isOk(actual);
      assert.equal(actual!.lineNum, 77);
      assert.isTrue(actual!.leftSide);
    });
  });

  test('startRouterForTesting requires auth for the right handlers', () => {
    // This test encodes the lists of route handler methods that gr-router
    // automatically checks for authentication before triggering.

    const requiresAuth: any = {};
    const doesNotRequireAuth: any = {};
    sinon.stub(page, 'start');
    sinon
      .stub(router, 'mapRoute')
      .callsFake((_pattern, methodName, _method, usesAuth) => {
        if (usesAuth) {
          requiresAuth[methodName] = true;
        } else {
          doesNotRequireAuth[methodName] = true;
        }
      });
    router._testOnly_startRouter();

    const actualRequiresAuth = Object.keys(requiresAuth);
    actualRequiresAuth.sort();
    const actualDoesNotRequireAuth = Object.keys(doesNotRequireAuth);
    actualDoesNotRequireAuth.sort();

    const shouldRequireAutoAuth = [
      'handleAgreementsRoute',
      'handleChangeEditRoute',
      'handleCreateGroupRoute',
      'handleCreateProjectRoute',
      'handleDiffEditRoute',
      'handleGroupAuditLogRoute',
      'handleGroupInfoRoute',
      'handleGroupListRoute',
      'handleGroupMembersRoute',
      'handleGroupRoute',
      'handleGroupSelfRedirectRoute',
      'handleNewAgreementsRoute',
      'handlePluginListFilterRoute',
      'handlePluginListRoute',
      'handleRepoCommandsRoute',
      'handleRepoEditFileRoute',
      'handleSettingsLegacyRoute',
      'handleSettingsRoute',
    ];
    assert.deepEqual(actualRequiresAuth, shouldRequireAutoAuth);

    const unauthenticatedHandlers = [
      'handleBranchListRoute',
      'handleChangeIdQueryRoute',
      'handleChangeRoute',
      'handleCommentRoute',
      'handleCommentsRoute',
      'handleDiffRoute',
      'handleDefaultRoute',
      'handleDocumentationRedirectRoute',
      'handleDocumentationSearchRoute',
      'handleDocumentationSearchRedirectRoute',
      'handleLegacyLinenum',
      'handleImproperlyEncodedPlusRoute',
      'handleProjectDashboardRoute',
      'handleLegacyProjectDashboardRoute',
      'handleProjectsOldRoute',
      'handleRepoAccessRoute',
      'handleRepoDashboardsRoute',
      'handleRepoGeneralRoute',
      'handleRepoListRoute',
      'handleRepoRoute',
      'handleQueryLegacySuffixRoute',
      'handleQueryRoute',
      'handleRegisterRoute',
      'handleTagListRoute',
      'handlePluginScreen',
    ];

    // Handler names that check authentication themselves, and thus don't need
    // it performed for them.
    const selfAuthenticatingHandlers = [
      'handleDashboardRoute',
      'handleCustomDashboardRoute',
      'handleRootRoute',
    ];

    const shouldNotRequireAuth = unauthenticatedHandlers.concat(
      selfAuthenticatingHandlers
    );
    shouldNotRequireAuth.sort();
    assert.deepEqual(actualDoesNotRequireAuth, shouldNotRequireAuth);
  });

  test('redirectIfNotLoggedIn while logged in', () => {
    stubRestApi('getLoggedIn').returns(Promise.resolve(true));
    const redirectStub = sinon.stub(router, 'redirectToLogin');
    return router.redirectIfNotLoggedIn('somepath').then(() => {
      assert.isFalse(redirectStub.called);
    });
  });

  test('redirectIfNotLoggedIn while logged out', () => {
    stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    const redirectStub = sinon.stub(router, 'redirectToLogin');
    return new Promise(resolve => {
      router
        .redirectIfNotLoggedIn('somepath')
        .then(() => {
          assert.isTrue(false, 'Should never execute');
        })
        .catch(() => {
          assert.isTrue(redirectStub.calledOnce);
          resolve(Promise.resolve());
        });
    });
  });

  suite('param normalization', () => {
    suite('normalizePatchRangeParams', () => {
      test('range n..n normalizes to n', () => {
        const params: PatchRangeParams = {
          basePatchNum: 4 as BasePatchSetNum,
          patchNum: 4 as RevisionPatchSetNum,
        };
        router.normalizePatchRangeParams(params);
        assert.equal(params.basePatchNum, PARENT);
        assert.equal(params.patchNum, 4 as RevisionPatchSetNum);
      });

      test('range n.. normalizes to n', () => {
        const params: PatchRangeParams = {basePatchNum: 4 as BasePatchSetNum};
        router.normalizePatchRangeParams(params);
        assert.equal(params.basePatchNum, PARENT);
        assert.equal(params.patchNum, 4 as RevisionPatchSetNum);
      });
    });
  });

  suite('navigation blockers', () => {
    let clock: sinon.SinonFakeTimers;
    let redirectStub: sinon.SinonStub;
    let urlPromise: MockPromise<string>;

    setup(() => {
      stubRestApi('setInProjectLookup');
      urlPromise = mockPromise<string>();
      redirectStub = sinon
        .stub(router, 'redirect')
        .callsFake(urlPromise.resolve);
      router._testOnly_startRouter();
      clock = sinon.useFakeTimers();
    });

    test('no blockers: normal redirect', async () => {
      router.page.show('/settings/agreements');
      const url = await urlPromise;
      assert.isTrue(redirectStub.calledOnce);
      assert.equal(url, '/settings/#Agreements');
    });

    test('redirect blocked', async () => {
      const firstAlertPromise = mockPromise<Event>();
      addListenerForTest(document, 'show-alert', firstAlertPromise.resolve);

      router.blockNavigation('a good reason');
      router.page.show('/settings/agreements');

      const firstAlert = (await firstAlertPromise) as CustomEvent;
      assert.equal(
        firstAlert.detail.message,
        'Waiting 1 second for navigation blockers to resolve ...'
      );

      const secondAlertPromise = mockPromise<Event>();
      addListenerForTest(document, 'show-alert', secondAlertPromise.resolve);

      clock.tick(2000);

      const secondAlert = (await secondAlertPromise) as CustomEvent;
      assert.equal(
        secondAlert.detail.message,
        'Navigation is blocked by: a good reason'
      );

      assert.isFalse(redirectStub.called);
    });

    test('redirect blocked, but resolved within one second', async () => {
      const firstAlertPromise = mockPromise<Event>();
      addListenerForTest(document, 'show-alert', firstAlertPromise.resolve);

      router.blockNavigation('a good reason');
      router.page.show('/settings/agreements');

      const firstAlert = (await firstAlertPromise) as CustomEvent;
      assert.equal(
        firstAlert.detail.message,
        'Waiting 1 second for navigation blockers to resolve ...'
      );

      const secondAlertPromise = mockPromise<Event>();
      addListenerForTest(document, 'show-alert', secondAlertPromise.resolve);

      clock.tick(500);
      router.releaseNavigation('a good reason');
      clock.tick(2000);

      await urlPromise;
      assert.isTrue(redirectStub.calledOnce);
    });
  });

  suite('route handlers', () => {
    let redirectStub: sinon.SinonStub;
    let setStateStub: sinon.SinonStub;
    let windowReloadStub: sinon.SinonStub;
    let redirectToLoginStub: sinon.SinonStub;

    async function checkUrlToState<T extends ViewState>(
      url: string,
      state: T | AppElementJustRegisteredParams
    ) {
      setStateStub.reset();
      router.page.show(url);
      await waitUntilCalled(setStateStub, 'setState');
      assert.isTrue(setStateStub.calledOnce);
      assert.deepEqual(setStateStub.lastCall.firstArg, state);
    }

    async function checkRedirect(fromUrl: string, toUrl: string) {
      redirectStub.reset();
      router.page.show(fromUrl);
      await waitUntilCalled(redirectStub, 'redirect');
      assert.isTrue(redirectStub.calledOnce);
      assert.isFalse(setStateStub.called);
      assert.equal(redirectStub.lastCall.firstArg, toUrl);
    }

    async function checkRedirectToLogin(fromUrl: string, toUrl: string) {
      redirectToLoginStub.reset();
      router.page.show(fromUrl);
      await waitUntilCalled(redirectToLoginStub, 'redirectToLogin');
      assert.isTrue(redirectToLoginStub.calledOnce);
      assert.isFalse(redirectStub.called);
      assert.isFalse(setStateStub.called);
      assert.equal(redirectToLoginStub.lastCall.firstArg, toUrl);
    }

    setup(() => {
      stubRestApi('setInProjectLookup');
      redirectStub = sinon.stub(router, 'redirect');
      redirectToLoginStub = sinon.stub(router, 'redirectToLogin');
      setStateStub = sinon.stub(router, 'setState');
      windowReloadStub = sinon.stub(router, 'windowReload');
      router._testOnly_startRouter();
    });

    test('LEGACY_PROJECT_DASHBOARD', async () => {
      // LEGACY_PROJECT_DASHBOARD: /^\/projects\/(.+),dashboards\/(.+)/,
      await checkRedirect(
        '/projects/gerrit/project,dashboards/dashboard:main',
        '/p/gerrit/project/+/dashboard/dashboard:main'
      );
    });

    test('AGREEMENTS', async () => {
      // AGREEMENTS: /^\/settings\/agreements\/?/,
      await checkRedirect('/settings/agreements', '/settings/#Agreements');
    });

    test('NEW_AGREEMENTS', async () => {
      // NEW_AGREEMENTS: /^\/settings\/new-agreement\/?/,
      await checkUrlToState('/settings/new-agreement', {
        view: GerritView.AGREEMENTS,
      });
      await checkUrlToState('/settings/new-agreement/', {
        view: GerritView.AGREEMENTS,
      });
    });

    test('SETTINGS', async () => {
      // SETTINGS: /^\/settings\/?/,
      // SETTINGS_LEGACY: /^\/settings\/VE\/(\S+)/,
      await checkUrlToState('/settings', {view: GerritView.SETTINGS});
      await checkUrlToState('/settings/', {view: GerritView.SETTINGS});
      await checkUrlToState('/settings/VE/asdf', {
        view: GerritView.SETTINGS,
        emailToken: 'asdf',
      });
      await checkUrlToState('/settings/VE/asdf%40qwer', {
        view: GerritView.SETTINGS,
        emailToken: 'asdf@qwer',
      });
    });

    test('handleDefaultRoute on first load', () => {
      const spy = sinon.spy();
      addListenerForTest(document, 'page-error', spy);
      router.handleDefaultRoute();
      assert.isTrue(spy.calledOnce);
      assert.equal(spy.lastCall.args[0].detail.response.status, 404);
    });

    test('handleDefaultRoute after internal navigation', () => {
      let onExit: Function | null = null;
      const onRegisteringExit = (
        _match: string | RegExp,
        _onExit: Function
      ) => {
        onExit = _onExit;
      };
      sinon.stub(page, 'registerExitRoute').callsFake(onRegisteringExit);
      sinon.stub(page, 'start');
      router._testOnly_startRouter();

      router.handleDefaultRoute();

      onExit!('', () => {}); // we left page;

      router.handleDefaultRoute();
      assert.isTrue(windowReloadStub.calledOnce);
    });

    test('IMPROPERLY_ENCODED_PLUS', async () => {
      // IMPROPERLY_ENCODED_PLUS: /^\/c\/(.+)\/ \/(.+)$/,
      await checkRedirect('/c/repo/ /42', '/c/repo/+/42');
      await checkRedirect('/c/repo/%20/42', '/c/repo/+/42');
      await checkRedirect('/c/repo/ /42#foo', '/c/repo/+/42#foo');
    });

    test('QUERY', async () => {
      // QUERY: /^\/q\/(.+?)(,(\d+))?$/,
      await checkUrlToState('/q/asdf', {
        ...createSearchViewState(),
        query: 'asdf',
      });
      await checkUrlToState('/q/project:foo/bar/baz', {
        ...createSearchViewState(),
        query: 'project:foo/bar/baz',
      });
      await checkUrlToState('/q/asdf,123', {
        ...createSearchViewState(),
        query: 'asdf',
        offset: '123',
      });
      await checkUrlToState('/q/asdf,qwer', {
        ...createSearchViewState(),
        query: 'asdf,qwer',
      });
      await checkUrlToState('/q/asdf,qwer,123', {
        ...createSearchViewState(),
        query: 'asdf,qwer',
        offset: '123',
      });
    });

    test('QUERY_LEGACY_SUFFIX', async () => {
      // QUERY_LEGACY_SUFFIX: /^\/q\/.+,n,z$/,
      await checkRedirect('/q/foo+bar,n,z', '/q/foo+bar');
    });

    test('CHANGE_ID_QUERY', async () => {
      // CHANGE_ID_QUERY: /^\/id\/(I[0-9a-f]{40})$/,
      await checkUrlToState('/id/I0123456789abcdef0123456789abcdef01234567', {
        ...createSearchViewState(),
        query: 'I0123456789abcdef0123456789abcdef01234567',
      });
    });

    test('REGISTER', async () => {
      // REGISTER: /^\/register(\/.*)?$/,
      await checkUrlToState('/register/foo/bar', {
        justRegistered: true,
      });
      assert.isTrue(redirectStub.calledWithExactly('/foo/bar'));

      await checkUrlToState('/register', {
        justRegistered: true,
      });
      assert.isTrue(redirectStub.calledWithExactly('/'));

      await checkUrlToState('/register/register', {
        justRegistered: true,
      });
      assert.isTrue(redirectStub.calledWithExactly('/'));
    });

    suite('ROOT', () => {
      test('closes for closeAfterLogin', () => {
        const ctx = {
          querystring: 'closeAfterLogin',
          canonicalPath: '',
        } as PageContext;
        const closeStub = sinon.stub(window, 'close');
        const result = router.handleRootRoute(ctx);
        assert.isNotOk(result);
        assert.isTrue(closeStub.called);
        assert.isFalse(redirectStub.called);
      });

      test('ROOT logged in', async () => {
        stubRestApi('getLoggedIn').resolves(true);
        await checkRedirect('/', '/dashboard/self');
      });

      test('ROOT not logged in', async () => {
        stubRestApi('getLoggedIn').resolves(false);
        await checkRedirect('/', '/q/status:open+-is:wip');
      });

      suite('ROOT GWT hash-path URLs', () => {
        test('ROOT hash-path URLs', async () => {
          await checkRedirect('/#/foo/bar/baz', '/foo/bar/baz');
        });

        test('ROOT hash-path URLs w/o leading slash', async () => {
          await checkRedirect('/#foo/bar/baz', '/foo/bar/baz');
        });

        test('ROOT normalizes "/ /" in hash to "/+/"', async () => {
          await checkRedirect('/#/foo/bar/+/123/4', '/foo/bar/+/123/4');
        });

        test('ROOT prepends baseurl to hash-path', async () => {
          stubBaseUrl('/baz');
          await checkRedirect('/#/foo/bar', '/baz/foo/bar');
        });

        test('ROOT normalizes /VE/ settings hash-paths', async () => {
          await checkRedirect('/#/VE/foo/bar', '/settings/VE/foo/bar');
        });

        test('ROOT does not drop "inner hashes"', async () => {
          await checkRedirect('/#/foo/bar#baz', '/foo/bar#baz');
        });
      });
    });

    suite('DASHBOARD', () => {
      test('DASHBOARD own dashboard but signed out redirects to login', async () => {
        stubRestApi('getLoggedIn').resolves(false);
        await checkRedirectToLogin('/dashboard/seLF', '/dashboard/seLF');
      });

      test('DASHBOARD non-self dashboard but signed out redirects', async () => {
        stubRestApi('getLoggedIn').resolves(false);
        await checkRedirect('/dashboard/foo', '/q/owner:foo');
      });

      test('DASHBOARD', async () => {
        // DASHBOARD: /^\/dashboard\/(.+)$/,
        await checkUrlToState('/dashboard/foo', {
          ...createDashboardViewState(),
          user: 'foo',
        });
      });
    });

    suite('CUSTOM_DASHBOARD', () => {
      test('CUSTOM_DASHBOARD no user specified', async () => {
        await checkRedirect('/dashboard/', '/dashboard/self');
      });

      test('CUSTOM_DASHBOARD', async () => {
        // CUSTOM_DASHBOARD: /^\/dashboard\/?$/,
        await checkUrlToState('/dashboard?title=Custom Dashboard&a=b&d=e', {
          ...createDashboardViewState(),
          type: DashboardType.CUSTOM,
          sections: [
            {name: 'a', query: 'b'},
            {name: 'd', query: 'e'},
          ],
          title: 'Custom Dashboard',
        });
        await checkUrlToState('/dashboard?a=b&c&d=&=e&foreach=is:open', {
          ...createDashboardViewState(),
          type: DashboardType.CUSTOM,
          sections: [{name: 'a', query: 'is:open b'}],
          title: 'Custom Dashboard',
        });
      });
    });

    suite('group routes', () => {
      test('GROUP_INFO', async () => {
        // GROUP_INFO: /^\/admin\/groups\/(?:uuid-)?(.+),info$/,
        await checkRedirect('/admin/groups/1234,info', '/admin/groups/1234');
      });

      test('GROUP_AUDIT_LOG', async () => {
        // GROUP_AUDIT_LOG: /^\/admin\/groups\/(?:uuid-)?(.+),audit-log$/,
        await checkUrlToState('/admin/groups/1234,audit-log', {
          ...createGroupViewState(),
          detail: GroupDetailView.LOG,
          groupId: '1234',
        });
      });

      test('GROUP_MEMBERS', async () => {
        // GROUP_MEMBERS: /^\/admin\/groups\/(?:uuid-)?(.+),members$/,
        await checkUrlToState('/admin/groups/1234,members', {
          ...createGroupViewState(),
          detail: GroupDetailView.MEMBERS,
          groupId: '1234',
        });
      });

      test('GROUP_LIST', async () => {
        // GROUP_LIST: /^\/admin\/groups(\/q\/filter:(.*?))?(,(\d+))?(\/)?$/,

        const defaultState: AdminViewState = {
          view: GerritView.ADMIN,
          adminView: AdminChildView.GROUPS,
          offset: '0',
          openCreateModal: false,
          filter: '',
        };

        await checkUrlToState('/admin/groups', defaultState);
        await checkUrlToState('/admin/groups/', defaultState);
        await checkUrlToState('/admin/groups#create', {
          ...defaultState,
          openCreateModal: true,
        });
        await checkUrlToState('/admin/groups,42', {
          ...defaultState,
          offset: '42',
        });
        // #create is ignored when there is an offset
        await checkUrlToState('/admin/groups,42#create', {
          ...defaultState,
          offset: '42',
        });

        await checkUrlToState('/admin/groups/q/filter:foo', {
          ...defaultState,
          filter: 'foo',
        });
        await checkUrlToState('/admin/groups/q/filter:foo/%2F%20%2525%252F', {
          ...defaultState,
          filter: 'foo// %/',
        });
        await checkUrlToState('/admin/groups/q/filter:foo,42', {
          ...defaultState,
          filter: 'foo',
          offset: '42',
        });
        // #create is ignored when filtering
        await checkUrlToState('/admin/groups/q/filter:foo,42#create', {
          ...defaultState,
          filter: 'foo',
          offset: '42',
        });
      });

      test('GROUP', async () => {
        // GROUP: /^\/admin\/groups\/(?:uuid-)?([^,]+)$/,
        await checkUrlToState('/admin/groups/4321', {
          ...createGroupViewState(),
          groupId: '4321',
        });
      });
    });

    suite('REPO*', () => {
      test('PROJECT_OLD', async () => {
        // PROJECT_OLD: /^\/admin\/(projects)\/?(.+)?$/,
        await checkRedirect('/admin/projects/', '/admin/repos/');
        await checkRedirect('/admin/projects/test', '/admin/repos/test');
        await checkRedirect(
          '/admin/projects/test,branches',
          '/admin/repos/test,branches'
        );
      });

      test('REPO', async () => {
        // REPO: /^\/admin\/repos\/([^,]+)$/,
        await checkRedirect('/admin/repos/test', '/admin/repos/test,general');
      });

      test('REPO_GENERAL', async () => {
        // REPO_GENERAL: /^\/admin\/repos\/(.+),general$/,
        await checkUrlToState('/admin/repos/4321,general', {
          ...createRepoViewState(),
          detail: RepoDetailView.GENERAL,
          repo: '4321' as RepoName,
        });
      });

      test('REPO_COMMANDS', async () => {
        // REPO_COMMANDS: /^\/admin\/repos\/(.+),commands$/,
        await checkUrlToState('/admin/repos/4321,commands', {
          ...createRepoViewState(),
          detail: RepoDetailView.COMMANDS,
          repo: '4321' as RepoName,
        });
      });

      test('REPO_ACCESS', async () => {
        // REPO_ACCESS: /^\/admin\/repos\/(.+),access$/,
        await checkUrlToState('/admin/repos/4321,access', {
          ...createRepoViewState(),
          detail: RepoDetailView.ACCESS,
          repo: '4321' as RepoName,
        });
      });

      test('BRANCH_LIST', async () => {
        await checkUrlToState('/admin/repos/4321,branches', {
          ...createRepoBranchesViewState(),
          repo: '4321' as RepoName,
        });
        await checkUrlToState('/admin/repos/4321,branches,42', {
          ...createRepoBranchesViewState(),
          repo: '4321' as RepoName,
          offset: '42',
        });
        await checkUrlToState('/admin/repos/4321,branches/q/filter:foo,42', {
          ...createRepoBranchesViewState(),
          repo: '4321' as RepoName,
          offset: '42',
          filter: 'foo',
        });
        await checkUrlToState('/admin/repos/4321,branches/q/filter:foo', {
          ...createRepoBranchesViewState(),
          repo: '4321' as RepoName,
          filter: 'foo',
        });
        await checkUrlToState(
          '/admin/repos/asdf/%2F%20%2525%252Fqwer,branches/q/filter:foo/%2F%20%2525%252F',
          {
            ...createRepoBranchesViewState(),
            repo: 'asdf// %/qwer' as RepoName,
            filter: 'foo// %/',
          }
        );
      });

      test('TAG_LIST', async () => {
        await checkUrlToState('/admin/repos/4321,tags', {
          ...createRepoTagsViewState(),
          repo: '4321' as RepoName,
        });
        await checkUrlToState('/admin/repos/4321,tags,42', {
          ...createRepoTagsViewState(),
          repo: '4321' as RepoName,
          offset: '42',
        });
        await checkUrlToState('/admin/repos/4321,tags/q/filter:foo,42', {
          ...createRepoTagsViewState(),
          repo: '4321' as RepoName,
          offset: '42',
          filter: 'foo',
        });
        await checkUrlToState('/admin/repos/4321,tags/q/filter:foo', {
          ...createRepoTagsViewState(),
          repo: '4321' as RepoName,
          filter: 'foo',
        });
        await checkUrlToState(
          '/admin/repos/asdf/%2F%20%2525%252Fqwer,tags/q/filter:foo/%2F%20%2525%252F',
          {
            ...createRepoTagsViewState(),
            repo: 'asdf// %/qwer' as RepoName,
            filter: 'foo// %/',
          }
        );
      });

      test('REPO_LIST', async () => {
        await checkUrlToState('/admin/repos', {
          ...createAdminReposViewState(),
        });
        await checkUrlToState('/admin/repos/', {
          ...createAdminReposViewState(),
        });
        await checkUrlToState('/admin/repos,42', {
          ...createAdminReposViewState(),
          offset: '42',
        });
        await checkUrlToState('/admin/repos#create', {
          ...createAdminReposViewState(),
          openCreateModal: true,
        });
        await checkUrlToState('/admin/repos/q/filter:foo', {
          ...createAdminReposViewState(),
          filter: 'foo',
        });
        await checkUrlToState('/admin/repos/q/filter:foo/%2F%20%2525%252F', {
          ...createAdminReposViewState(),
          filter: 'foo// %/',
        });
        await checkUrlToState('/admin/repos/q/filter:foo,42', {
          ...createAdminReposViewState(),
          filter: 'foo',
          offset: '42',
        });
      });
    });

    test('PLUGIN_LIST', async () => {
      await checkUrlToState('/admin/plugins', {
        ...createAdminPluginsViewState(),
      });
      await checkUrlToState('/admin/plugins/', {
        ...createAdminPluginsViewState(),
      });
      await checkUrlToState('/admin/plugins,42', {
        ...createAdminPluginsViewState(),
        offset: '42',
      });
      await checkUrlToState('/admin/plugins/q/filter:foo', {
        ...createAdminPluginsViewState(),
        filter: 'foo',
      });
      await checkUrlToState('/admin/plugins/q/filter:foo%2F%20%2525%252F', {
        ...createAdminPluginsViewState(),
        filter: 'foo/ %/',
      });
      await checkUrlToState('/admin/plugins/q/filter:foo,42', {
        ...createAdminPluginsViewState(),
        offset: '42',
        filter: 'foo',
      });
      await checkUrlToState('/admin/plugins/q/filter:foo,asdf', {
        ...createAdminPluginsViewState(),
        filter: 'foo,asdf',
      });
    });

    suite('CHANGE* / DIFF*', () => {
      test('DIFF_LEGACY_LINENUM', async () => {
        await checkRedirect(
          '/c/1234/3..8/foo/bar@321',
          '/c/1234/3..8/foo/bar#321'
        );
        await checkRedirect(
          '/c/1234/3..8/foo/bar@b321',
          '/c/1234/3..8/foo/bar#b321'
        );
      });

      test('CHANGE', async () => {
        // CHANGE: /^\/c\/(.+)\/\+\/(\d+)(\/?((-?\d+|edit)(\.\.(\d+|edit))?))?\/?$/,
        await checkUrlToState('/c/test-project/+/42', {
          ...createChangeViewState(),
          basePatchNum: undefined,
          patchNum: undefined,
        });
        await checkUrlToState('/c/test-project/+/42/7', {
          ...createChangeViewState(),
          basePatchNum: PARENT,
          patchNum: 7,
        });
        await checkUrlToState('/c/test-project/+/42/4..7', {
          ...createChangeViewState(),
          basePatchNum: 4,
          patchNum: 7,
        });
        await checkUrlToState(
          '/c/test-project/+/42/4..7?tab=checks&filter=fff&attempt=1&checksRunsSelected=asdf,qwer&checksResultsFilter=asdf.*qwer',
          {
            ...createChangeViewState(),
            basePatchNum: 4,
            patchNum: 7,
            attempt: 1,
            filter: 'fff',
            tab: 'checks',
            checksRunsSelected: new Set(['asdf', 'qwer']),
            checksResultsFilter: 'asdf.*qwer',
          }
        );
      });

      test('COMMENTS_TAB', async () => {
        // COMMENTS_TAB: /^\/c\/(.+)\/\+\/(\d+)\/comments(?:\/)?(\w+)?\/?$/,
        await checkUrlToState(
          '/c/gerrit/+/264833/comments/00049681_f34fd6a9/',
          {
            ...createChangeViewState(),
            repo: 'gerrit' as RepoName,
            changeNum: 264833 as NumericChangeId,
            commentId: '00049681_f34fd6a9' as UrlEncodedCommentId,
            view: GerritView.CHANGE,
            childView: ChangeChildView.OVERVIEW,
          }
        );
      });

      suite('handleDiffRoute', () => {
        test('DIFF', async () => {
          // DIFF: /^\/c\/(.+)\/\+\/(\d+)(\/((-?\d+|edit)(\.\.(\d+|edit))?(\/(.+))))\/?$/,
          await checkUrlToState('/c/test-project/+/42/4..7/foo/bar/baz#b44', {
            ...createDiffViewState(),
            basePatchNum: 4 as BasePatchSetNum,
            patchNum: 7 as RevisionPatchSetNum,
            diffView: {
              path: 'foo/bar/baz',
              lineNum: 44,
              leftSide: true,
            },
          });
        });

        test('COMMENT base..1', async () => {
          const change: ParsedChangeInfo = createParsedChange();
          const repo = change.project;
          const changeNum = change._number;
          const ps = 1 as RevisionPatchSetNum;
          const line = 23;
          const id = '00049681_f34fd6a9' as UrlEncodedCommentId;
          stubRestApi('getChangeDetail').resolves(change);
          stubRestApi('getDiffComments').resolves({
            filepath: [{...createComment(), id, patch_set: ps, line}],
          });

          await checkRedirect(
            `/c/${repo}/+/${changeNum}/comment/${id}/`,
            `/c/${repo}/+/${changeNum}/${ps}/filepath#${line}`
          );
        });

        test('COMMENT 1..2', async () => {
          const change: ParsedChangeInfo = {
            ...createParsedChange(),
            revisions: {
              abc: createRevision(1),
              def: createRevision(2),
            },
          };
          const repo = change.project;
          const changeNum = change._number;
          const ps = 1 as RevisionPatchSetNum;
          const line = 23;
          const id = '00049681_f34fd6a9' as UrlEncodedCommentId;

          stubRestApi('getChangeDetail').resolves(change);
          stubRestApi('getDiffComments').resolves({
            filepath: [{...createComment(), id, patch_set: ps, line}],
          });
          const diffStub = stubRestApi('getDiff');

          // If getDiff() returns a diff with changes, then we will compare
          // the patchset of the comment (1) against latest (2).
          diffStub.onFirstCall().resolves(createDiff());
          await checkRedirect(
            `/c/${repo}/+/${changeNum}/comment/${id}/`,
            `/c/${repo}/+/${changeNum}/${ps}..2/filepath#b${line}`
          );

          // If getDiff() returns an unchanged diff, then we will compare
          // the patchset of the comment (1) against base.
          diffStub.onSecondCall().resolves({
            ...createDiff(),
            content: [],
          });
          await checkRedirect(
            `/c/${repo}/+/${changeNum}/comment/${id}/`,
            `/c/${repo}/+/${changeNum}/${ps}/filepath#${line}`
          );
        });
      });

      test('DIFF_EDIT', async () => {
        // DIFF_EDIT: /^\/c\/(.+)\/\+\/(\d+)\/(\d+|edit)\/(.+),edit(#\d+)?$/,
        await checkUrlToState('/c/foo/bar/+/1234/3/foo/bar/baz,edit', {
          ...createEditViewState(),
          repo: 'foo/bar' as RepoName,
          changeNum: 1234 as NumericChangeId,
          view: GerritView.CHANGE,
          childView: ChangeChildView.EDIT,
          patchNum: 3 as RevisionPatchSetNum,
          editView: {path: 'foo/bar/baz', lineNum: 0},
        });
        await checkUrlToState('/c/foo/bar/+/1234/3/foo/bar/baz,edit#4', {
          ...createEditViewState(),
          repo: 'foo/bar' as RepoName,
          changeNum: 1234 as NumericChangeId,
          view: GerritView.CHANGE,
          childView: ChangeChildView.EDIT,
          patchNum: 3 as RevisionPatchSetNum,
          editView: {path: 'foo/bar/baz', lineNum: 4},
        });
      });

      test('CHANGE_EDIT', async () => {
        // CHANGE_EDIT: /^\/c\/(.+)\/\+\/(\d+)(\/(\d+))?,edit\/?$/,
        await checkUrlToState('/c/foo/bar/+/1234/3,edit', {
          ...createChangeViewState(),
          repo: 'foo/bar' as RepoName,
          changeNum: 1234 as NumericChangeId,
          view: GerritView.CHANGE,
          childView: ChangeChildView.OVERVIEW,
          patchNum: 3 as RevisionPatchSetNum,
          edit: true,
        });
      });
    });

    test('PLUGIN_SCREEN', async () => {
      // PLUGIN_SCREEN: /^\/x\/([\w-]+)\/([\w-]+)\/?/,
      await checkUrlToState('/x/foo/bar', {
        view: GerritView.PLUGIN_SCREEN,
        plugin: 'foo',
        screen: 'bar',
      });
    });

    test('DOCUMENTATION_SEARCH*', async () => {
      // DOCUMENTATION_SEARCH_FILTER: '/Documentation/q/filter::filter',
      // DOCUMENTATION_SEARCH: /^\/Documentation\/q\/(.*)$/,
      await checkRedirect(
        '/Documentation/q/asdf',
        '/Documentation/q/filter:asdf'
      );
      await checkRedirect(
        '/Documentation/q/as%3Fdf',
        '/Documentation/q/filter:as%3Fdf'
      );

      await checkUrlToState('/Documentation/q/filter:', {
        view: GerritView.DOCUMENTATION_SEARCH,
        filter: '',
      });
      await checkUrlToState('/Documentation/q/filter:asdf', {
        view: GerritView.DOCUMENTATION_SEARCH,
        filter: 'asdf',
      });
      // Percent decoding works fine. gr-page decodes twice, so the only problem
      // is having `%25` in the URL, because the first decoding pass will yield
      // `%`, and then the second decoding pass will throw `URI malformed`.
      await checkUrlToState('/Documentation/q/filter:as%20%2fdf', {
        view: GerritView.DOCUMENTATION_SEARCH,
        filter: 'as /df',
      });
      // We accept and process double-encoded values, but only *require* it for
      // the percent symbol `%`.
      await checkUrlToState('/Documentation/q/filter:as%252f%2525df', {
        view: GerritView.DOCUMENTATION_SEARCH,
        filter: 'as/%df',
      });
    });
  });
});
