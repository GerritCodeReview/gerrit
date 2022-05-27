/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-router';
import {page} from '../../../utils/page-wrapper-utils';
import {
  GenerateUrlChangeViewParameters,
  GenerateUrlDashboardViewParameters,
  GenerateUrlDiffViewParameters,
  GenerateUrlEditViewParameters,
  GenerateUrlGroupViewParameters,
  GenerateUrlParameters,
  GenerateUrlSearchViewParameters,
  GerritNav,
  GroupDetailView,
  WeblinkType,
} from '../gr-navigation/gr-navigation';
import {
  stubBaseUrl,
  stubRestApi,
  addListenerForTest,
} from '../../../test/test-utils';
import {
  GrRouter,
  PageContextWithQueryMap,
  PatchRangeParams,
  _testOnly_RoutePattern,
} from './gr-router';
import {GerritView} from '../../../services/router/router-model';
import {
  BasePatchSetNum,
  BranchName,
  CommitId,
  DashboardId,
  GroupId,
  NumericChangeId,
  PARENT,
  RepoName,
  RevisionPatchSetNum,
  TopicName,
  UrlEncodedCommentId,
  WebLinkInfo,
} from '../../../types/common';
import {
  createGerritInfo,
  createServerInfo,
} from '../../../test/test-data-generators';
import {AppElementParams} from '../../gr-app-types';

suite('gr-router tests', () => {
  let router: GrRouter;

  setup(() => {
    router = new GrRouter();
  });

  test('firstCodeBrowserWeblink', () => {
    assert.deepEqual(
      router.firstCodeBrowserWeblink([
        {name: 'gitweb'},
        {name: 'gitiles'},
        {name: 'browse'},
        {name: 'test'},
      ]),
      {name: 'gitiles'}
    );

    assert.deepEqual(
      router.firstCodeBrowserWeblink([{name: 'gitweb'}, {name: 'test'}]),
      {name: 'gitweb'}
    );
  });

  test('getBrowseCommitWeblink', () => {
    const browserLink = {name: 'browser', url: 'browser/url'};
    const link = {name: 'test', url: 'test/url'};
    const weblinks = [browserLink, link];
    const config = {
      ...createServerInfo(),
      gerrit: {...createGerritInfo(), primary_weblink_name: browserLink.name},
    };
    sinon.stub(router, 'firstCodeBrowserWeblink').returns(link);

    assert.deepEqual(
      router.getBrowseCommitWeblink(weblinks, config),
      browserLink
    );

    assert.deepEqual(router.getBrowseCommitWeblink(weblinks), link);
  });

  test('getChangeWeblinks', () => {
    const link = {name: 'test', url: 'test/url'};
    const browserLink = {name: 'browser', url: 'browser/url'};
    const mapLinksToConfig = (weblinks: WebLinkInfo[]) => {
      return {
        type: 'change' as WeblinkType.CHANGE,
        repo: 'test' as RepoName,
        commit: '111' as CommitId,
        options: {weblinks},
      };
    };
    sinon.stub(router, 'getBrowseCommitWeblink').returns(browserLink);

    assert.deepEqual(
      router.getChangeWeblinks(mapLinksToConfig([link, browserLink]))[0],
      {name: 'test', url: 'test/url'}
    );

    assert.deepEqual(router.getChangeWeblinks(mapLinksToConfig([link]))[0], {
      name: 'test',
      url: 'test/url',
    });

    link.url = `https://${link.url}`;
    assert.deepEqual(router.getChangeWeblinks(mapLinksToConfig([link]))[0], {
      name: 'test',
      url: 'https://test/url',
    });
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

  test('startRouter requires auth for the right handlers', () => {
    // This test encodes the lists of route handler methods that gr-router
    // automatically checks for authentication before triggering.

    const requiresAuth: any = {};
    const doesNotRequireAuth: any = {};
    sinon.stub(GerritNav, 'setup');
    sinon.stub(page, 'start');
    sinon.stub(page, 'base');
    sinon
      .stub(router, 'mapRoute')
      .callsFake((_pattern, methodName, _method, usesAuth) => {
        if (usesAuth) {
          requiresAuth[methodName] = true;
        } else {
          doesNotRequireAuth[methodName] = true;
        }
      });
    router.startRouter();

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
      'handleGroupListFilterOffsetRoute',
      'handleGroupListFilterRoute',
      'handleGroupListOffsetRoute',
      'handleGroupMembersRoute',
      'handleGroupRoute',
      'handleGroupSelfRedirectRoute',
      'handleNewAgreementsRoute',
      'handlePluginListFilterOffsetRoute',
      'handlePluginListFilterRoute',
      'handlePluginListOffsetRoute',
      'handlePluginListRoute',
      'handleRepoCommandsRoute',
      'handleSettingsLegacyRoute',
      'handleSettingsRoute',
    ];
    assert.deepEqual(actualRequiresAuth, shouldRequireAutoAuth);

    const unauthenticatedHandlers = [
      'handleBranchListFilterOffsetRoute',
      'handleBranchListFilterRoute',
      'handleBranchListOffsetRoute',
      'handleChangeIdQueryRoute',
      'handleChangeNumberLegacyRoute',
      'handleChangeRoute',
      'handleCommentRoute',
      'handleCommentsRoute',
      'handleDiffRoute',
      'handleDefaultRoute',
      'handleChangeLegacyRoute',
      'handleDocumentationRedirectRoute',
      'handleDocumentationSearchRoute',
      'handleDocumentationSearchRedirectRoute',
      'handleLegacyLinenum',
      'handleImproperlyEncodedPlusRoute',
      'handlePassThroughRoute',
      'handleProjectDashboardRoute',
      'handleLegacyProjectDashboardRoute',
      'handleProjectsOldRoute',
      'handleRepoAccessRoute',
      'handleRepoDashboardsRoute',
      'handleRepoGeneralRoute',
      'handleRepoListFilterOffsetRoute',
      'handleRepoListFilterRoute',
      'handleRepoListOffsetRoute',
      'handleRepoRoute',
      'handleQueryLegacySuffixRoute',
      'handleQueryRoute',
      'handleRegisterRoute',
      'handleTagListFilterOffsetRoute',
      'handleTagListFilterRoute',
      'handleTagListOffsetRoute',
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
    const data = {
      save() {},
      handled: true,
      canonicalPath: '',
      path: '',
      querystring: '',
      pathname: '',
      state: '',
      title: '',
      hash: '',
      params: {test: 'test'},
    };
    const redirectStub = sinon.stub(router, 'redirectToLogin');
    return router.redirectIfNotLoggedIn(data).then(() => {
      assert.isFalse(redirectStub.called);
    });
  });

  test('redirectIfNotLoggedIn while logged out', () => {
    stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    const redirectStub = sinon.stub(router, 'redirectToLogin');
    const data = {
      save() {},
      handled: true,
      canonicalPath: '',
      path: '',
      querystring: '',
      pathname: '',
      state: '',
      title: '',
      hash: '',
      params: {test: 'test'},
    };
    return new Promise(resolve => {
      router
        .redirectIfNotLoggedIn(data)
        .then(() => {
          assert.isTrue(false, 'Should never execute');
        })
        .catch(() => {
          assert.isTrue(redirectStub.calledOnce);
          resolve(Promise.resolve());
        });
    });
  });

  suite('generateUrl', () => {
    test('search', () => {
      let params: GenerateUrlSearchViewParameters = {
        view: GerritView.SEARCH,
        owner: 'a%b',
        project: 'c%d' as RepoName,
        branch: 'e%f' as BranchName,
        topic: 'g%h' as TopicName,
        statuses: ['op%en'],
      };
      assert.equal(
        router.generateUrl(params),
        '/q/owner:a%2525b+project:c%2525d+branch:e%2525f+' +
          'topic:g%2525h+status:op%2525en'
      );

      params.offset = 100;
      assert.equal(
        router.generateUrl(params),
        '/q/owner:a%2525b+project:c%2525d+branch:e%2525f+' +
          'topic:g%2525h+status:op%2525en,100'
      );
      delete params.offset;

      // The presence of the query param overrides other params.
      params.query = 'foo$bar';
      assert.equal(router.generateUrl(params), '/q/foo%2524bar');

      params.offset = 100;
      assert.equal(router.generateUrl(params), '/q/foo%2524bar,100');

      params = {
        view: GerritNav.View.SEARCH,
        statuses: ['a', 'b', 'c'],
      };
      assert.equal(
        router.generateUrl(params),
        '/q/(status:a OR status:b OR status:c)'
      );

      params = {
        view: GerritNav.View.SEARCH,
        topic: 'test' as TopicName,
      };
      assert.equal(router.generateUrl(params), '/q/topic:test');
      params = {
        view: GerritNav.View.SEARCH,
        topic: 'test test' as TopicName,
      };
      assert.equal(router.generateUrl(params), '/q/topic:"test+test"');
      params = {
        view: GerritNav.View.SEARCH,
        topic: 'test:test' as TopicName,
      };
      assert.equal(router.generateUrl(params), '/q/topic:"test:test"');
    });

    test('change', () => {
      const params: GenerateUrlChangeViewParameters = {
        view: GerritView.CHANGE,
        changeNum: 1234 as NumericChangeId,
        project: 'test' as RepoName,
      };

      assert.equal(router.generateUrl(params), '/c/test/+/1234');

      params.patchNum = 10 as RevisionPatchSetNum;
      assert.equal(router.generateUrl(params), '/c/test/+/1234/10');

      params.basePatchNum = 5 as BasePatchSetNum;
      assert.equal(router.generateUrl(params), '/c/test/+/1234/5..10');

      params.messageHash = '#123';
      assert.equal(router.generateUrl(params), '/c/test/+/1234/5..10#123');
    });

    test('change with repo name encoding', () => {
      const params: GenerateUrlChangeViewParameters = {
        view: GerritView.CHANGE,
        changeNum: 1234 as NumericChangeId,
        project: 'x+/y+/z+/w' as RepoName,
      };
      assert.equal(
        router.generateUrl(params),
        '/c/x%252B/y%252B/z%252B/w/+/1234'
      );
    });

    test('diff', () => {
      const params: GenerateUrlDiffViewParameters = {
        view: GerritView.DIFF,
        changeNum: 42 as NumericChangeId,
        path: 'x+y/path.cpp' as RepoName,
        patchNum: 12 as RevisionPatchSetNum,
        project: '' as RepoName,
      };
      assert.equal(router.generateUrl(params), '/c/42/12/x%252By/path.cpp');

      params.project = 'test' as RepoName;
      assert.equal(
        router.generateUrl(params),
        '/c/test/+/42/12/x%252By/path.cpp'
      );

      params.basePatchNum = 6 as BasePatchSetNum;
      assert.equal(
        router.generateUrl(params),
        '/c/test/+/42/6..12/x%252By/path.cpp'
      );

      params.path = 'foo bar/my+file.txt%';
      params.patchNum = 2 as RevisionPatchSetNum;
      delete params.basePatchNum;
      assert.equal(
        router.generateUrl(params),
        '/c/test/+/42/2/foo+bar/my%252Bfile.txt%2525'
      );

      params.path = 'file.cpp';
      params.lineNum = 123;
      assert.equal(router.generateUrl(params), '/c/test/+/42/2/file.cpp#123');

      params.leftSide = true;
      assert.equal(router.generateUrl(params), '/c/test/+/42/2/file.cpp#b123');
    });

    test('diff with repo name encoding', () => {
      const params: GenerateUrlDiffViewParameters = {
        view: GerritView.DIFF,
        changeNum: 42 as NumericChangeId,
        path: 'x+y/path.cpp',
        patchNum: 12 as RevisionPatchSetNum,
        project: 'x+/y' as RepoName,
      };
      assert.equal(
        router.generateUrl(params),
        '/c/x%252B/y/+/42/12/x%252By/path.cpp'
      );
    });

    test('edit', () => {
      const params: GenerateUrlEditViewParameters = {
        view: GerritView.EDIT,
        changeNum: 42 as NumericChangeId,
        project: 'test' as RepoName,
        path: 'x+y/path.cpp',
        patchNum: 'edit' as RevisionPatchSetNum,
      };
      assert.equal(
        router.generateUrl(params),
        '/c/test/+/42/edit/x%252By/path.cpp,edit'
      );
    });

    test('getPatchRangeExpression', () => {
      const params: PatchRangeParams = {};
      let actual = router.getPatchRangeExpression(params);
      assert.equal(actual, '');

      params.patchNum = 4 as RevisionPatchSetNum;
      actual = router.getPatchRangeExpression(params);
      assert.equal(actual, '4');

      params.basePatchNum = 2 as BasePatchSetNum;
      actual = router.getPatchRangeExpression(params);
      assert.equal(actual, '2..4');

      delete params.patchNum;
      actual = router.getPatchRangeExpression(params);
      assert.equal(actual, '2..');
    });

    suite('dashboard', () => {
      test('self dashboard', () => {
        const params: GenerateUrlDashboardViewParameters = {
          view: GerritView.DASHBOARD,
        };
        assert.equal(router.generateUrl(params), '/dashboard/self');
      });

      test('user dashboard', () => {
        const params: GenerateUrlDashboardViewParameters = {
          view: GerritView.DASHBOARD,
          user: 'user',
        };
        assert.equal(router.generateUrl(params), '/dashboard/user');
      });

      test('custom self dashboard, no title', () => {
        const params: GenerateUrlDashboardViewParameters = {
          view: GerritView.DASHBOARD,
          sections: [
            {name: 'section 1', query: 'query 1'},
            {name: 'section 2', query: 'query 2'},
          ],
        };
        assert.equal(
          router.generateUrl(params),
          '/dashboard/?section%201=query%201&section%202=query%202'
        );
      });

      test('custom repo dashboard', () => {
        const params: GenerateUrlDashboardViewParameters = {
          view: GerritView.DASHBOARD,
          sections: [
            {name: 'section 1', query: 'query 1 ${project}'},
            {name: 'section 2', query: 'query 2 ${repo}'},
          ],
          repo: 'repo-name' as RepoName,
        };
        assert.equal(
          router.generateUrl(params),
          '/dashboard/?section%201=query%201%20repo-name&' +
            'section%202=query%202%20repo-name'
        );
      });

      test('custom user dashboard, with title', () => {
        const params: GenerateUrlDashboardViewParameters = {
          view: GerritView.DASHBOARD,
          user: 'user',
          sections: [{name: 'name', query: 'query'}],
          title: 'custom dashboard',
        };
        assert.equal(
          router.generateUrl(params),
          '/dashboard/user?name=query&title=custom%20dashboard'
        );
      });

      test('repo dashboard', () => {
        const params: GenerateUrlDashboardViewParameters = {
          view: GerritView.DASHBOARD,
          repo: 'gerrit/repo' as RepoName,
          dashboard: 'default:main' as DashboardId,
        };
        assert.equal(
          router.generateUrl(params),
          '/p/gerrit/repo/+/dashboard/default:main'
        );
      });

      test('project dashboard (legacy)', () => {
        const params: GenerateUrlDashboardViewParameters = {
          view: GerritView.DASHBOARD,
          project: 'gerrit/project' as RepoName,
          dashboard: 'default:main' as DashboardId,
        };
        assert.equal(
          router.generateUrl(params),
          '/p/gerrit/project/+/dashboard/default:main'
        );
      });
    });

    suite('groups', () => {
      test('group info', () => {
        const params: GenerateUrlGroupViewParameters = {
          view: GerritView.GROUP,
          groupId: '1234' as GroupId,
        };
        assert.equal(router.generateUrl(params), '/admin/groups/1234');
      });

      test('group members', () => {
        const params: GenerateUrlGroupViewParameters = {
          view: GerritView.GROUP,
          groupId: '1234' as GroupId,
          detail: 'members' as GroupDetailView,
        };
        assert.equal(router.generateUrl(params), '/admin/groups/1234,members');
      });

      test('group audit log', () => {
        const params: GenerateUrlGroupViewParameters = {
          view: GerritView.GROUP,
          groupId: '1234' as GroupId,
          detail: 'log' as GroupDetailView,
        };
        assert.equal(
          router.generateUrl(params),
          '/admin/groups/1234,audit-log'
        );
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
        const needsRedirect = router.normalizePatchRangeParams(params);
        assert.isTrue(needsRedirect);
        assert.equal(params.basePatchNum, PARENT);
        assert.equal(params.patchNum, 4 as RevisionPatchSetNum);
      });

      test('range n.. normalizes to n', () => {
        const params: PatchRangeParams = {basePatchNum: 4 as BasePatchSetNum};
        const needsRedirect = router.normalizePatchRangeParams(params);
        assert.isFalse(needsRedirect);
        assert.equal(params.basePatchNum, PARENT);
        assert.equal(params.patchNum, 4 as RevisionPatchSetNum);
      });
    });
  });

  suite('route handlers', () => {
    let redirectStub: sinon.SinonStub;
    let setParamsStub: sinon.SinonStub;
    let handlePassThroughRoute: sinon.SinonStub;

    // Simple route handlers are direct mappings from parsed route data to a
    // new set of app.params. This test helper asserts that passing `data`
    // into `methodName` results in setting the params specified in `params`.
    function assertDataToParams(
      data: PageContextWithQueryMap,
      methodName: string,
      params: AppElementParams | GenerateUrlParameters
    ) {
      (router as any)[methodName](data);
      assert.deepEqual(setParamsStub.lastCall.args[0], params);
    }

    function createPageContext(): PageContextWithQueryMap {
      return {
        queryMap: new Map(),
        save() {},
        handled: true,
        canonicalPath: '',
        path: '',
        querystring: '',
        pathname: '',
        state: '',
        title: '',
        hash: '',
        params: {},
      };
    }

    setup(() => {
      redirectStub = sinon.stub(router, 'redirect');
      setParamsStub = sinon.stub(router, 'setParams');
      handlePassThroughRoute = sinon.stub(router, 'handlePassThroughRoute');
    });

    test('handleLegacyProjectDashboardRoute', () => {
      const params = {
        ...createPageContext(),
        params: {0: 'gerrit/project', 1: 'dashboard:main'},
      };
      router.handleLegacyProjectDashboardRoute(params);
      assert.isTrue(redirectStub.calledOnce);
      assert.equal(
        redirectStub.lastCall.args[0],
        '/p/gerrit/project/+/dashboard/dashboard:main'
      );
    });

    test('handleAgreementsRoute', () => {
      router.handleAgreementsRoute();
      assert.isTrue(redirectStub.calledOnce);
      assert.equal(redirectStub.lastCall.args[0], '/settings/#Agreements');
    });

    test('handleNewAgreementsRoute', () => {
      const params = createPageContext();
      router.handleNewAgreementsRoute(params);
      assert.isTrue(setParamsStub.calledOnce);
      assert.equal(
        setParamsStub.lastCall.args[0].view,
        GerritNav.View.AGREEMENTS
      );
    });

    test('handleSettingsLegacyRoute', () => {
      const data = {...createPageContext(), params: {0: 'my-token'}};
      assertDataToParams(data, 'handleSettingsLegacyRoute', {
        view: GerritNav.View.SETTINGS,
        emailToken: 'my-token',
      });
    });

    test('handleSettingsLegacyRoute with +', () => {
      const data = {...createPageContext(), params: {0: 'my-token test'}};
      assertDataToParams(data, 'handleSettingsLegacyRoute', {
        view: GerritNav.View.SETTINGS,
        emailToken: 'my-token+test',
      });
    });

    test('handleSettingsRoute', () => {
      const data = createPageContext();
      assertDataToParams(data, 'handleSettingsRoute', {
        view: GerritNav.View.SETTINGS,
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
      sinon.stub(page, 'exit').callsFake(onRegisteringExit);
      sinon.stub(GerritNav, 'setup');
      sinon.stub(page, 'start');
      sinon.stub(page, 'base');
      router.startRouter();

      router.handleDefaultRoute();

      onExit!('', () => {}); // we left page;

      router.handleDefaultRoute();
      assert.isTrue(handlePassThroughRoute.calledOnce);
    });

    test('handleImproperlyEncodedPlusRoute', () => {
      const params = {
        ...createPageContext(),
        canonicalPath: '/c/test/%20/42',
        params: {0: 'test', 1: '42'},
      };
      // Regression test for Issue 7100.
      router.handleImproperlyEncodedPlusRoute(params);
      assert.isTrue(redirectStub.calledOnce);
      assert.equal(redirectStub.lastCall.args[0], '/c/test/+/42');

      sinon.stub(router, 'getHashFromCanonicalPath').returns('foo');
      router.handleImproperlyEncodedPlusRoute(params);
      assert.equal(redirectStub.lastCall.args[0], '/c/test/+/42#foo');
    });

    test('handleQueryRoute', () => {
      const data: PageContextWithQueryMap = {
        ...createPageContext(),
        params: {0: 'project:foo/bar/baz'},
      };
      assertDataToParams(data, 'handleQueryRoute', {
        view: GerritNav.View.SEARCH,
        query: 'project:foo/bar/baz',
        offset: undefined,
      });

      data.params[1] = '123';
      data.params[2] = '123';
      assertDataToParams(data, 'handleQueryRoute', {
        view: GerritNav.View.SEARCH,
        query: 'project:foo/bar/baz',
        offset: '123',
      });
    });

    test('handleQueryLegacySuffixRoute', () => {
      const params = {...createPageContext(), path: '/q/foo+bar,n,z'};
      router.handleQueryLegacySuffixRoute(params);
      assert.isTrue(redirectStub.calledOnce);
      assert.equal(redirectStub.lastCall.args[0], '/q/foo+bar');
    });

    test('handleChangeIdQueryRoute', () => {
      const data = {
        ...createPageContext(),
        params: {0: 'I0123456789abcdef0123456789abcdef01234567'},
      };
      assertDataToParams(data, 'handleChangeIdQueryRoute', {
        view: GerritNav.View.SEARCH,
        query: 'I0123456789abcdef0123456789abcdef01234567',
      });
    });

    suite('handleRegisterRoute', () => {
      test('happy path', () => {
        const ctx = {...createPageContext(), params: {0: '/foo/bar'}};
        router.handleRegisterRoute(ctx);
        assert.isTrue(redirectStub.calledWithExactly('/foo/bar'));
        assert.isTrue(setParamsStub.calledOnce);
        assert.isTrue(setParamsStub.lastCall.args[0].justRegistered);
      });

      test('no param', () => {
        const ctx = createPageContext();
        router.handleRegisterRoute(ctx);
        assert.isTrue(redirectStub.calledWithExactly('/'));
        assert.isTrue(setParamsStub.calledOnce);
        assert.isTrue(setParamsStub.lastCall.args[0].justRegistered);
      });

      test('prevent redirect', () => {
        const ctx = {...createPageContext(), params: {0: '/register'}};
        router.handleRegisterRoute(ctx);
        assert.isTrue(redirectStub.calledWithExactly('/'));
        assert.isTrue(setParamsStub.calledOnce);
        assert.isTrue(setParamsStub.lastCall.args[0].justRegistered);
      });
    });

    suite('handleRootRoute', () => {
      test('closes for closeAfterLogin', () => {
        const data = {...createPageContext(), querystring: 'closeAfterLogin'};
        const closeStub = sinon.stub(window, 'close');
        const result = router.handleRootRoute(data);
        assert.isNotOk(result);
        assert.isTrue(closeStub.called);
        assert.isFalse(redirectStub.called);
      });

      test('redirects to dashboard if logged in', () => {
        const data = {...createPageContext(), canonicalPath: '/', path: '/'};
        const result = router.handleRootRoute(data);
        assert.isOk(result);
        return result!.then(() => {
          assert.isTrue(redirectStub.calledWithExactly('/dashboard/self'));
        });
      });

      test('redirects to open changes if not logged in', () => {
        stubRestApi('getLoggedIn').returns(Promise.resolve(false));
        const data = {...createPageContext(), canonicalPath: '/', path: '/'};
        const result = router.handleRootRoute(data);
        assert.isOk(result);
        return result!.then(() => {
          assert.isTrue(
            redirectStub.calledWithExactly('/q/status:open+-is:wip')
          );
        });
      });

      suite('GWT hash-path URLs', () => {
        test('redirects hash-path URLs', () => {
          const data = {
            ...createPageContext(),
            canonicalPath: '/#/foo/bar/baz',
            hash: '/foo/bar/baz',
          };
          const result = router.handleRootRoute(data);
          assert.isNotOk(result);
          assert.isTrue(redirectStub.called);
          assert.isTrue(redirectStub.calledWithExactly('/foo/bar/baz'));
        });

        test('redirects hash-path URLs w/o leading slash', () => {
          const data = {
            ...createPageContext(),
            canonicalPath: '/#foo/bar/baz',
            hash: 'foo/bar/baz',
          };
          const result = router.handleRootRoute(data);
          assert.isNotOk(result);
          assert.isTrue(redirectStub.called);
          assert.isTrue(redirectStub.calledWithExactly('/foo/bar/baz'));
        });

        test('normalizes "/ /" in hash to "/+/"', () => {
          const data = {
            ...createPageContext(),
            canonicalPath: '/#/foo/bar/+/123/4',
            hash: '/foo/bar/ /123/4',
          };
          const result = router.handleRootRoute(data);
          assert.isNotOk(result);
          assert.isTrue(redirectStub.called);
          assert.isTrue(redirectStub.calledWithExactly('/foo/bar/+/123/4'));
        });

        test('prepends baseurl to hash-path', () => {
          const data = {
            ...createPageContext(),
            canonicalPath: '/#/foo/bar',
            hash: '/foo/bar',
          };
          stubBaseUrl('/baz');
          const result = router.handleRootRoute(data);
          assert.isNotOk(result);
          assert.isTrue(redirectStub.called);
          assert.isTrue(redirectStub.calledWithExactly('/baz/foo/bar'));
        });

        test('normalizes /VE/ settings hash-paths', () => {
          const data = {
            ...createPageContext(),
            canonicalPath: '/#/VE/foo/bar',
            hash: '/VE/foo/bar',
          };
          const result = router.handleRootRoute(data);
          assert.isNotOk(result);
          assert.isTrue(redirectStub.called);
          assert.isTrue(redirectStub.calledWithExactly('/settings/VE/foo/bar'));
        });

        test('does not drop "inner hashes"', () => {
          const data = {
            ...createPageContext(),
            canonicalPath: '/#/foo/bar#baz',
            hash: '/foo/bar',
          };
          const result = router.handleRootRoute(data);
          assert.isNotOk(result);
          assert.isTrue(redirectStub.called);
          assert.isTrue(redirectStub.calledWithExactly('/foo/bar#baz'));
        });
      });
    });

    suite('handleDashboardRoute', () => {
      let redirectToLoginStub: sinon.SinonStub;

      setup(() => {
        redirectToLoginStub = sinon.stub(router, 'redirectToLogin');
      });

      test('own dashboard but signed out redirects to login', () => {
        stubRestApi('getLoggedIn').returns(Promise.resolve(false));
        const data = {
          ...createPageContext(),
          canonicalPath: '/dashboard/',
          params: {0: 'seLF'},
        };
        return router.handleDashboardRoute(data).then(() => {
          assert.isTrue(redirectToLoginStub.calledOnce);
          assert.isFalse(redirectStub.called);
          assert.isFalse(setParamsStub.called);
        });
      });

      test('non-self dashboard but signed out does not redirect', () => {
        stubRestApi('getLoggedIn').returns(Promise.resolve(false));
        const data = {
          ...createPageContext(),
          canonicalPath: '/dashboard/',
          params: {0: 'foo'},
        };
        return router.handleDashboardRoute(data).then(() => {
          assert.isFalse(redirectToLoginStub.called);
          assert.isFalse(setParamsStub.called);
          assert.isTrue(redirectStub.calledOnce);
          assert.equal(redirectStub.lastCall.args[0], '/q/owner:foo');
        });
      });

      test('dashboard while signed in sets params', () => {
        const data = {
          ...createPageContext(),
          canonicalPath: '/dashboard/',
          params: {0: 'foo'},
        };
        return router.handleDashboardRoute(data).then(() => {
          assert.isFalse(redirectToLoginStub.called);
          assert.isFalse(redirectStub.called);
          assert.isTrue(setParamsStub.calledOnce);
          assert.deepEqual(setParamsStub.lastCall.args[0], {
            view: GerritNav.View.DASHBOARD,
            user: 'foo',
          });
        });
      });
    });

    suite('handleCustomDashboardRoute', () => {
      let redirectToLoginStub: sinon.SinonStub;

      setup(() => {
        redirectToLoginStub = sinon.stub(router, 'redirectToLogin');
      });

      test('no user specified', () => {
        const data = {
          ...createPageContext(),
          canonicalPath: '/dashboard/',
          params: {0: ''},
        };
        return router.handleCustomDashboardRoute(data, '').then(() => {
          assert.isFalse(setParamsStub.called);
          assert.isTrue(redirectStub.called);
          assert.equal(redirectStub.lastCall.args[0], '/dashboard/self');
        });
      });

      test('custom dashboard without title', () => {
        const data = {
          ...createPageContext(),
          canonicalPath: '/dashboard/',
          params: {0: ''},
        };
        return router
          .handleCustomDashboardRoute(data, '?a=b&c&d=e')
          .then(() => {
            assert.isFalse(redirectStub.called);
            assert.isTrue(setParamsStub.calledOnce);
            assert.deepEqual(setParamsStub.lastCall.args[0], {
              view: GerritNav.View.DASHBOARD,
              user: 'self',
              sections: [
                {name: 'a', query: 'b'},
                {name: 'd', query: 'e'},
              ],
              title: 'Custom Dashboard',
            });
          });
      });

      test('custom dashboard with title', () => {
        const data = {
          ...createPageContext(),
          canonicalPath: '/dashboard/',
          params: {0: ''},
        };
        return router
          .handleCustomDashboardRoute(data, '?a=b&c&d=&=e&title=t')
          .then(() => {
            assert.isFalse(redirectToLoginStub.called);
            assert.isFalse(redirectStub.called);
            assert.isTrue(setParamsStub.calledOnce);
            assert.deepEqual(setParamsStub.lastCall.args[0], {
              view: GerritNav.View.DASHBOARD,
              user: 'self',
              sections: [{name: 'a', query: 'b'}],
              title: 't',
            });
          });
      });

      test('custom dashboard with foreach', () => {
        const data = {
          ...createPageContext(),
          canonicalPath: '/dashboard/',
          params: {0: ''},
        };
        return router
          .handleCustomDashboardRoute(data, '?a=b&c&d=&=e&foreach=is:open')
          .then(() => {
            assert.isFalse(redirectToLoginStub.called);
            assert.isFalse(redirectStub.called);
            assert.isTrue(setParamsStub.calledOnce);
            assert.deepEqual(setParamsStub.lastCall.args[0], {
              view: GerritNav.View.DASHBOARD,
              user: 'self',
              sections: [{name: 'a', query: 'is:open b'}],
              title: 'Custom Dashboard',
            });
          });
      });
    });

    suite('group routes', () => {
      test('handleGroupInfoRoute', () => {
        const data = {...createPageContext(), params: {0: '1234'}};
        router.handleGroupInfoRoute(data);
        assert.isTrue(redirectStub.calledOnce);
        assert.equal(redirectStub.lastCall.args[0], '/admin/groups/1234');
      });

      test('handleGroupAuditLogRoute', () => {
        const data = {...createPageContext(), params: {0: '1234'}};
        assertDataToParams(data, 'handleGroupAuditLogRoute', {
          view: GerritView.GROUP,
          detail: GroupDetailView.LOG,
          groupId: '1234' as GroupId,
        });
      });

      test('handleGroupMembersRoute', () => {
        const data = {...createPageContext(), params: {0: '1234'}};
        assertDataToParams(data, 'handleGroupMembersRoute', {
          view: GerritView.GROUP,
          detail: GroupDetailView.MEMBERS,
          groupId: '1234' as GroupId,
        });
      });

      test('handleGroupListOffsetRoute', () => {
        const data = createPageContext();
        assertDataToParams(data, 'handleGroupListOffsetRoute', {
          view: GerritView.ADMIN,
          adminView: 'gr-admin-group-list',
          offset: 0,
          filter: null,
          openCreateModal: false,
        });

        data.params[1] = '42';
        assertDataToParams(data, 'handleGroupListOffsetRoute', {
          view: GerritView.ADMIN,
          adminView: 'gr-admin-group-list',
          offset: '42',
          filter: null,
          openCreateModal: false,
        });

        data.hash = 'create';
        assertDataToParams(data, 'handleGroupListOffsetRoute', {
          view: GerritNav.View.ADMIN,
          adminView: 'gr-admin-group-list',
          offset: '42',
          filter: null,
          openCreateModal: true,
        });
      });

      test('handleGroupListFilterOffsetRoute', () => {
        const data = {
          ...createPageContext(),
          params: {filter: 'foo', offset: '42'},
        };
        assertDataToParams(data, 'handleGroupListFilterOffsetRoute', {
          view: GerritView.ADMIN,
          adminView: 'gr-admin-group-list',
          offset: '42',
          filter: 'foo',
        });
      });

      test('handleGroupListFilterRoute', () => {
        const data = {...createPageContext(), params: {filter: 'foo'}};
        assertDataToParams(data, 'handleGroupListFilterRoute', {
          view: GerritView.ADMIN,
          adminView: 'gr-admin-group-list',
          filter: 'foo',
        });
      });

      test('handleGroupRoute', () => {
        const data = {...createPageContext(), params: {0: '4321'}};
        assertDataToParams(data, 'handleGroupRoute', {
          view: GerritView.GROUP,
          groupId: '4321' as GroupId,
        });
      });
    });

    suite('repo routes', () => {
      test('handleProjectsOldRoute', () => {
        const data = {...createPageContext(), params: {}};
        router.handleProjectsOldRoute(data);
        assert.isTrue(redirectStub.calledOnce);
        assert.equal(redirectStub.lastCall.args[0], '/admin/repos/');
      });

      test('handleProjectsOldRoute test', () => {
        const data = {...createPageContext(), params: {1: 'test'}};
        router.handleProjectsOldRoute(data);
        assert.isTrue(redirectStub.calledOnce);
        assert.equal(redirectStub.lastCall.args[0], '/admin/repos/test');
      });

      test('handleProjectsOldRoute test,branches', () => {
        const data = {...createPageContext(), params: {1: 'test,branches'}};
        router.handleProjectsOldRoute(data);
        assert.isTrue(redirectStub.calledOnce);
        assert.equal(
          redirectStub.lastCall.args[0],
          '/admin/repos/test,branches'
        );
      });

      test('handleRepoRoute', () => {
        const data = {...createPageContext(), path: '/admin/repos/test'};
        router.handleRepoRoute(data);
        assert.isTrue(redirectStub.calledOnce);
        assert.equal(
          redirectStub.lastCall.args[0],
          '/admin/repos/test,general'
        );
      });

      test('handleRepoGeneralRoute', () => {
        const data = {...createPageContext(), params: {0: '4321'}};
        assertDataToParams(data, 'handleRepoGeneralRoute', {
          view: GerritView.REPO,
          detail: GerritNav.RepoDetailView.GENERAL,
          repo: '4321' as RepoName,
        });
      });

      test('handleRepoCommandsRoute', () => {
        const data = {...createPageContext(), params: {0: '4321'}};
        assertDataToParams(data, 'handleRepoCommandsRoute', {
          view: GerritView.REPO,
          detail: GerritNav.RepoDetailView.COMMANDS,
          repo: '4321' as RepoName,
        });
      });

      test('handleRepoAccessRoute', () => {
        const data = {...createPageContext(), params: {0: '4321'}};
        assertDataToParams(data, 'handleRepoAccessRoute', {
          view: GerritView.REPO,
          detail: GerritNav.RepoDetailView.ACCESS,
          repo: '4321' as RepoName,
        });
      });

      suite('branch list routes', () => {
        test('handleBranchListOffsetRoute', () => {
          const data: PageContextWithQueryMap = {
            ...createPageContext(),
            params: {0: '4321'},
          };
          assertDataToParams(data, 'handleBranchListOffsetRoute', {
            view: GerritView.REPO,
            detail: GerritNav.RepoDetailView.BRANCHES,
            repo: '4321' as RepoName,
            offset: 0,
            filter: null,
          });

          data.params[2] = '42';
          assertDataToParams(data, 'handleBranchListOffsetRoute', {
            view: GerritView.REPO,
            detail: GerritNav.RepoDetailView.BRANCHES,
            repo: '4321' as RepoName,
            offset: '42',
            filter: null,
          });
        });

        test('handleBranchListFilterOffsetRoute', () => {
          const data = {
            ...createPageContext(),
            params: {repo: '4321', filter: 'foo', offset: '42'},
          };
          assertDataToParams(data, 'handleBranchListFilterOffsetRoute', {
            view: GerritView.REPO,
            detail: GerritNav.RepoDetailView.BRANCHES,
            repo: '4321' as RepoName,
            offset: '42',
            filter: 'foo',
          });
        });

        test('handleBranchListFilterRoute', () => {
          const data = {
            ...createPageContext(),
            params: {repo: '4321', filter: 'foo'},
          };
          assertDataToParams(data, 'handleBranchListFilterRoute', {
            view: GerritView.REPO,
            detail: GerritNav.RepoDetailView.BRANCHES,
            repo: '4321' as RepoName,
            filter: 'foo',
          });
        });
      });

      suite('tag list routes', () => {
        test('handleTagListOffsetRoute', () => {
          const data = {...createPageContext(), params: {0: '4321'}};
          assertDataToParams(data, 'handleTagListOffsetRoute', {
            view: GerritView.REPO,
            detail: GerritNav.RepoDetailView.TAGS,
            repo: '4321' as RepoName,
            offset: 0,
            filter: null,
          });
        });

        test('handleTagListFilterOffsetRoute', () => {
          const data = {
            ...createPageContext(),
            params: {repo: '4321', filter: 'foo', offset: '42'},
          };
          assertDataToParams(data, 'handleTagListFilterOffsetRoute', {
            view: GerritView.REPO,
            detail: GerritNav.RepoDetailView.TAGS,
            repo: '4321' as RepoName,
            offset: '42',
            filter: 'foo',
          });
        });

        test('handleTagListFilterRoute', () => {
          const data: PageContextWithQueryMap = {
            ...createPageContext(),
            params: {repo: '4321'},
          };
          assertDataToParams(data, 'handleTagListFilterRoute', {
            view: GerritView.REPO,
            detail: GerritNav.RepoDetailView.TAGS,
            repo: '4321' as RepoName,
            filter: null,
          });

          data.params.filter = 'foo';
          assertDataToParams(data, 'handleTagListFilterRoute', {
            view: GerritView.REPO,
            detail: GerritNav.RepoDetailView.TAGS,
            repo: '4321' as RepoName,
            filter: 'foo',
          });
        });
      });

      suite('repo list routes', () => {
        test('handleRepoListOffsetRoute', () => {
          const data = createPageContext();
          assertDataToParams(data, 'handleRepoListOffsetRoute', {
            view: GerritView.ADMIN,
            adminView: 'gr-repo-list',
            offset: 0,
            filter: null,
            openCreateModal: false,
          });

          data.params[1] = '42';
          assertDataToParams(data, 'handleRepoListOffsetRoute', {
            view: GerritView.ADMIN,
            adminView: 'gr-repo-list',
            offset: '42',
            filter: null,
            openCreateModal: false,
          });

          data.hash = 'create';
          assertDataToParams(data, 'handleRepoListOffsetRoute', {
            view: GerritView.ADMIN,
            adminView: 'gr-repo-list',
            offset: '42',
            filter: null,
            openCreateModal: true,
          });
        });

        test('handleRepoListFilterOffsetRoute', () => {
          const data = {
            ...createPageContext(),
            params: {filter: 'foo', offset: '42'},
          };
          assertDataToParams(data, 'handleRepoListFilterOffsetRoute', {
            view: GerritView.ADMIN,
            adminView: 'gr-repo-list',
            offset: '42',
            filter: 'foo',
          });
        });

        test('handleRepoListFilterRoute', () => {
          const data = createPageContext();
          assertDataToParams(data, 'handleRepoListFilterRoute', {
            view: GerritView.ADMIN,
            adminView: 'gr-repo-list',
            filter: null,
          });

          data.params.filter = 'foo';
          assertDataToParams(data, 'handleRepoListFilterRoute', {
            view: GerritView.ADMIN,
            adminView: 'gr-repo-list',
            filter: 'foo',
          });
        });
      });
    });

    suite('plugin routes', () => {
      test('handlePluginListOffsetRoute', () => {
        const data = createPageContext();
        assertDataToParams(data, 'handlePluginListOffsetRoute', {
          view: GerritView.ADMIN,
          adminView: 'gr-plugin-list',
          offset: 0,
          filter: null,
        });

        data.params[1] = '42';
        assertDataToParams(data, 'handlePluginListOffsetRoute', {
          view: GerritView.ADMIN,
          adminView: 'gr-plugin-list',
          offset: '42',
          filter: null,
        });
      });

      test('handlePluginListFilterOffsetRoute', () => {
        const data = {
          ...createPageContext(),
          params: {filter: 'foo', offset: '42'},
        };
        assertDataToParams(data, 'handlePluginListFilterOffsetRoute', {
          view: GerritView.ADMIN,
          adminView: 'gr-plugin-list',
          offset: '42',
          filter: 'foo',
        });
      });

      test('handlePluginListFilterRoute', () => {
        const data = createPageContext();
        assertDataToParams(data, 'handlePluginListFilterRoute', {
          view: GerritView.ADMIN,
          adminView: 'gr-plugin-list',
          filter: null,
        });

        data.params.filter = 'foo';
        assertDataToParams(data, 'handlePluginListFilterRoute', {
          view: GerritView.ADMIN,
          adminView: 'gr-plugin-list',
          filter: 'foo',
        });
      });

      test('handlePluginListRoute', () => {
        const data = createPageContext();
        assertDataToParams(data, 'handlePluginListRoute', {
          view: GerritView.ADMIN,
          adminView: 'gr-plugin-list',
        });
      });
    });

    suite('change/diff routes', () => {
      test('handleChangeNumberLegacyRoute', () => {
        const data = {...createPageContext(), params: {0: '12345'}};
        router.handleChangeNumberLegacyRoute(data);
        assert.isTrue(redirectStub.calledOnce);
        assert.isTrue(redirectStub.calledWithExactly('/c/12345'));
      });

      test('handleChangeLegacyRoute', async () => {
        stubRestApi('getFromProjectLookup').returns(
          Promise.resolve('project' as RepoName)
        );
        const ctx = {
          ...createPageContext(),
          params: {0: '1234', 1: 'comment/6789'},
        };
        router.handleChangeLegacyRoute(ctx);
        await flush();
        assert.isTrue(
          redirectStub.calledWithExactly('/c/project/+/1234' + '/comment/6789')
        );
      });

      test('handleLegacyLinenum w/ @321', () => {
        const ctx = {...createPageContext(), path: '/c/1234/3..8/foo/bar@321'};
        router.handleLegacyLinenum(ctx);
        assert.isTrue(redirectStub.calledOnce);
        assert.isTrue(
          redirectStub.calledWithExactly('/c/1234/3..8/foo/bar#321')
        );
      });

      test('handleLegacyLinenum w/ @b123', () => {
        const ctx = {...createPageContext(), path: '/c/1234/3..8/foo/bar@b123'};
        router.handleLegacyLinenum(ctx);
        assert.isTrue(redirectStub.calledOnce);
        assert.isTrue(
          redirectStub.calledWithExactly('/c/1234/3..8/foo/bar#b123')
        );
      });

      suite('handleChangeRoute', () => {
        let normalizeRangeStub: sinon.SinonStub;

        function makeParams(
          _path: string,
          _hash: string
        ): PageContextWithQueryMap {
          return {
            ...createPageContext(),
            params: {
              0: 'foo/bar', // 0 Project
              1: '1234', // 1 Change number
              2: '', // 2 Unused
              3: '', // 3 Unused
              4: '4', // 4 Base patch number
              5: '', // 5 Unused
              6: '7', // 6 Patch number
            },
          };
        }

        setup(() => {
          normalizeRangeStub = sinon.stub(router, 'normalizePatchRangeParams');
          stubRestApi('setInProjectLookup');
        });

        test('needs redirect', () => {
          normalizeRangeStub.returns(true);
          sinon.stub(router, 'generateUrl').returns('foo');
          const ctx = makeParams('', '');
          router.handleChangeRoute(ctx);
          assert.isTrue(normalizeRangeStub.called);
          assert.isFalse(setParamsStub.called);
          assert.isTrue(redirectStub.calledOnce);
          assert.isTrue(redirectStub.calledWithExactly('foo'));
        });

        test('change view', () => {
          normalizeRangeStub.returns(false);
          sinon.stub(router, 'generateUrl').returns('foo');
          const ctx = makeParams('', '');
          assertDataToParams(ctx, 'handleChangeRoute', {
            view: GerritView.CHANGE,
            project: 'foo/bar' as RepoName,
            changeNum: 1234 as NumericChangeId,
            basePatchNum: 4 as BasePatchSetNum,
            patchNum: 7 as RevisionPatchSetNum,
          });
          assert.isFalse(redirectStub.called);
          assert.isTrue(normalizeRangeStub.called);
        });

        test('params', () => {
          normalizeRangeStub.returns(false);
          sinon.stub(router, 'generateUrl').returns('foo');
          const ctx = makeParams('', '');
          ctx.queryMap.set('tab', 'checks');
          ctx.queryMap.set('filter', 'fff');
          ctx.queryMap.set('select', 'sss');
          ctx.queryMap.set('attempt', '1');
          assertDataToParams(ctx, 'handleChangeRoute', {
            view: GerritView.CHANGE,
            project: 'foo/bar' as RepoName,
            changeNum: 1234 as NumericChangeId,
            basePatchNum: 4 as BasePatchSetNum,
            patchNum: 7 as RevisionPatchSetNum,
            attempt: 1,
            filter: 'fff',
            select: 'sss',
            tab: 'checks',
          });
        });
      });

      suite('handleDiffRoute', () => {
        let normalizeRangeStub: sinon.SinonStub;

        function makeParams(
          path: string,
          hash: string
        ): PageContextWithQueryMap {
          return {
            ...createPageContext(),
            hash,
            params: {
              0: 'foo/bar', // 0 Project
              1: '1234', // 1 Change number
              2: '', // 2 Unused
              3: '', // 3 Unused
              4: '4', // 4 Base patch number
              5: '', // 5 Unused
              6: '7', // 6 Patch number
              7: '', // 7 Unused,
              8: path, // 8 Diff path
            },
          };
        }

        setup(() => {
          normalizeRangeStub = sinon.stub(router, 'normalizePatchRangeParams');
          stubRestApi('setInProjectLookup');
        });

        test('needs redirect', () => {
          normalizeRangeStub.returns(true);
          sinon.stub(router, 'generateUrl').returns('foo');
          const ctx = makeParams('', '');
          router.handleDiffRoute(ctx);
          assert.isTrue(normalizeRangeStub.called);
          assert.isFalse(setParamsStub.called);
          assert.isTrue(redirectStub.calledOnce);
          assert.isTrue(redirectStub.calledWithExactly('foo'));
        });

        test('diff view', () => {
          normalizeRangeStub.returns(false);
          sinon.stub(router, 'generateUrl').returns('foo');
          const ctx = makeParams('foo/bar/baz', 'b44');
          assertDataToParams(ctx, 'handleDiffRoute', {
            view: GerritView.DIFF,
            project: 'foo/bar' as RepoName,
            changeNum: 1234 as NumericChangeId,
            basePatchNum: 4 as BasePatchSetNum,
            patchNum: 7 as RevisionPatchSetNum,
            path: 'foo/bar/baz',
            leftSide: true,
            lineNum: 44,
          });
          assert.isFalse(redirectStub.called);
          assert.isTrue(normalizeRangeStub.called);
        });

        test('comment route', () => {
          const url = '/c/gerrit/+/264833/comment/00049681_f34fd6a9/';
          const groups = url.match(_testOnly_RoutePattern.COMMENT);
          assert.deepEqual(groups!.slice(1), [
            'gerrit', // project
            '264833', // changeNum
            '00049681_f34fd6a9', // commentId
          ]);
          assertDataToParams(
            {params: groups!.slice(1)} as any,
            'handleCommentRoute',
            {
              project: 'gerrit' as RepoName,
              changeNum: 264833 as NumericChangeId,
              commentId: '00049681_f34fd6a9' as UrlEncodedCommentId,
              commentLink: true,
              view: GerritView.DIFF,
            }
          );
        });

        test('comments route', () => {
          const url = '/c/gerrit/+/264833/comments/00049681_f34fd6a9/';
          const groups = url.match(_testOnly_RoutePattern.COMMENTS_TAB);
          assert.deepEqual(groups!.slice(1), [
            'gerrit', // project
            '264833', // changeNum
            '00049681_f34fd6a9', // commentId
          ]);
          assertDataToParams(
            {params: groups!.slice(1)} as any,
            'handleCommentsRoute',
            {
              project: 'gerrit' as RepoName,
              changeNum: 264833 as NumericChangeId,
              commentId: '00049681_f34fd6a9' as UrlEncodedCommentId,
              view: GerritView.CHANGE,
            }
          );
        });
      });

      test('handleDiffEditRoute', () => {
        const normalizeRangeSpy = sinon.spy(
          router,
          'normalizePatchRangeParams'
        );
        stubRestApi('setInProjectLookup');
        const ctx = {
          ...createPageContext(),
          hash: '',
          params: {
            0: 'foo/bar', // 0 Project
            1: '1234', // 1 Change number
            2: '3', // 2 Patch num
            3: 'foo/bar/baz', // 3 File path
          },
        };
        const appParams: GenerateUrlEditViewParameters = {
          project: 'foo/bar' as RepoName,
          changeNum: 1234 as NumericChangeId,
          view: GerritNav.View.EDIT,
          path: 'foo/bar/baz',
          patchNum: 3 as RevisionPatchSetNum,
          lineNum: '',
        };

        router.handleDiffEditRoute(ctx);
        assert.isFalse(redirectStub.called);
        assert.isTrue(normalizeRangeSpy.calledOnce);
        assert.deepEqual(normalizeRangeSpy.lastCall.args[0], appParams);
        assert.isFalse(normalizeRangeSpy.lastCall.returnValue);
        assert.deepEqual(setParamsStub.lastCall.args[0], appParams);
      });

      test('handleDiffEditRoute with lineNum', () => {
        const normalizeRangeSpy = sinon.spy(
          router,
          'normalizePatchRangeParams'
        );
        stubRestApi('setInProjectLookup');
        const ctx = {
          ...createPageContext(),
          hash: '4',
          params: {
            0: 'foo/bar', // 0 Project
            1: '1234', // 1 Change number
            2: '3', // 2 Patch num
            3: 'foo/bar/baz', // 3 File path
          },
        };
        const appParams: GenerateUrlEditViewParameters = {
          project: 'foo/bar' as RepoName,
          changeNum: 1234 as NumericChangeId,
          view: GerritNav.View.EDIT,
          path: 'foo/bar/baz',
          patchNum: 3 as RevisionPatchSetNum,
          lineNum: '4',
        };

        router.handleDiffEditRoute(ctx);
        assert.isFalse(redirectStub.called);
        assert.isTrue(normalizeRangeSpy.calledOnce);
        assert.deepEqual(normalizeRangeSpy.lastCall.args[0], appParams);
        assert.isFalse(normalizeRangeSpy.lastCall.returnValue);
        assert.deepEqual(setParamsStub.lastCall.args[0], appParams);
      });

      test('handleChangeEditRoute', () => {
        const normalizeRangeSpy = sinon.spy(
          router,
          'normalizePatchRangeParams'
        );
        stubRestApi('setInProjectLookup');
        const ctx = {
          ...createPageContext(),
          params: {
            0: 'foo/bar', // 0 Project
            1: '1234', // 1 Change number
            2: '',
            3: '3', // 3 Patch num
          },
        };
        const appParams: GenerateUrlChangeViewParameters = {
          project: 'foo/bar' as RepoName,
          changeNum: 1234 as NumericChangeId,
          view: GerritView.CHANGE,
          patchNum: 3 as RevisionPatchSetNum,
          edit: true,
          tab: '',
        };

        router.handleChangeEditRoute(ctx);
        assert.isFalse(redirectStub.called);
        assert.isTrue(normalizeRangeSpy.calledOnce);
        assert.deepEqual(normalizeRangeSpy.lastCall.args[0], appParams);
        assert.isFalse(normalizeRangeSpy.lastCall.returnValue);
        assert.deepEqual(setParamsStub.lastCall.args[0], appParams);
      });
    });

    test('handlePluginScreen', () => {
      const ctx = {...createPageContext(), params: {0: 'foo', 1: 'bar'}};
      assertDataToParams(ctx, 'handlePluginScreen', {
        view: GerritNav.View.PLUGIN_SCREEN,
        plugin: 'foo',
        screen: 'bar',
      });
      assert.isFalse(redirectStub.called);
    });
  });

  suite('parseQueryString', () => {
    test('empty queries', () => {
      assert.deepEqual(router.parseQueryString(''), []);
      assert.deepEqual(router.parseQueryString('?'), []);
      assert.deepEqual(router.parseQueryString('??'), []);
      assert.deepEqual(router.parseQueryString('&&&'), []);
    });

    test('url decoding', () => {
      assert.deepEqual(router.parseQueryString('+'), [[' ', '']]);
      assert.deepEqual(router.parseQueryString('???+%3d+'), [[' = ', '']]);
      assert.deepEqual(
        router.parseQueryString('%6e%61%6d%65=%76%61%6c%75%65'),
        [['name', 'value']]
      );
    });

    test('multiple parameters', () => {
      assert.deepEqual(router.parseQueryString('a=b&c=d&e=f'), [
        ['a', 'b'],
        ['c', 'd'],
        ['e', 'f'],
      ]);
      assert.deepEqual(router.parseQueryString('&a=b&&&e=f&c'), [
        ['a', 'b'],
        ['e', 'f'],
        ['c', ''],
      ]);
    });
  });
});
