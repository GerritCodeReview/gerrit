/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-router';
import {page, PageContext} from '../../../utils/page-wrapper-utils';
import {
  stubBaseUrl,
  stubRestApi,
  addListenerForTest,
  waitEventLoop,
  waitUntilCalled,
} from '../../../test/test-utils';
import {GrRouter, routerToken, _testOnly_RoutePattern} from './gr-router';
import {GerritView} from '../../../services/router/router-model';
import {
  BasePatchSetNum,
  GroupId,
  NumericChangeId,
  PARENT,
  RepoName,
  RevisionPatchSetNum,
  UrlEncodedCommentId,
} from '../../../types/common';
import {AppElementParams} from '../../gr-app-types';
import {assert} from '@open-wc/testing';
import {AdminChildView, AdminViewState} from '../../../models/views/admin';
import {RepoDetailView} from '../../../models/views/repo';
import {GroupDetailView} from '../../../models/views/group';
import {ChangeChildView, ChangeViewState} from '../../../models/views/change';
import {PatchRangeParams} from '../../../utils/url-util';
import {testResolver} from '../../../test/common-test-setup';
import {
  createComment,
  createDiff,
  createParsedChange,
  createRevision,
} from '../../../test/test-data-generators';
import {ParsedChangeInfo} from '../../../types/types';

suite('gr-router tests', () => {
  let router: GrRouter;

  setup(() => {
    router = testResolver(routerToken);
  });

  teardown(() => {
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

  test('startRouter requires auth for the right handlers', () => {
    // This test encodes the lists of route handler methods that gr-router
    // automatically checks for authentication before triggering.

    const requiresAuth: any = {};
    const doesNotRequireAuth: any = {};
    const mapRouteStub = sinon
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
      'handleRepoEditFileRoute',
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

    mapRouteStub.restore();
  });

  test('redirectIfNotLoggedIn while logged in', () => {
    stubRestApi('getLoggedIn').returns(Promise.resolve(true));
    const ctx = {
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
    return router.redirectIfNotLoggedIn(ctx).then(() => {
      assert.isFalse(redirectStub.called);
    });
  });

  test('redirectIfNotLoggedIn while logged out', () => {
    stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    const redirectStub = sinon.stub(router, 'redirectToLogin');
    const ctx = {
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
        .redirectIfNotLoggedIn(ctx)
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

  suite('route handlers', () => {
    let redirectStub: sinon.SinonStub;
    let setStateStub: sinon.SinonStub;
    let handlePassThroughRoute: sinon.SinonStub;

    // Simple route handlers are direct mappings from parsed route ctx to a
    // new set of app.params. This test helper asserts that passing `ctx`
    // into `methodName` results in setting the params specified in `params`.
    function assertctxToParams(
      ctx: PageContext,
      methodName: string,
      params: AppElementParams
    ) {
      (router as any)[methodName](ctx);
      assert.deepEqual(setStateStub.lastCall.args[0], params);
    }

    function createPageContext(): PageContext {
      return {
        canonicalPath: '',
        path: '',
        querystring: '',
        pathname: '',
        hash: '',
        params: {},
      };
    }

    setup(() => {
      redirectStub = sinon.stub(router, 'redirect');
      setStateStub = sinon.stub(router, 'setState');
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
      router.handleNewAgreementsRoute();
      assert.isTrue(setStateStub.calledOnce);
      assert.equal(setStateStub.lastCall.args[0].view, GerritView.AGREEMENTS);
    });

    test('handleSettingsLegacyRoute', () => {
      const ctx = {...createPageContext(), params: {0: 'my-token'}};
      assertctxToParams(ctx, 'handleSettingsLegacyRoute', {
        view: GerritView.SETTINGS,
        emailToken: 'my-token',
      });
    });

    test('handleSettingsLegacyRoute with +', () => {
      const ctx = {...createPageContext(), params: {0: 'my-token test'}};
      assertctxToParams(ctx, 'handleSettingsLegacyRoute', {
        view: GerritView.SETTINGS,
        emailToken: 'my-token+test',
      });
    });

    test('handleSettingsRoute', () => {
      const ctx = createPageContext();
      assertctxToParams(ctx, 'handleSettingsRoute', {
        view: GerritView.SETTINGS,
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
      sinon.stub(router.page, 'exit').callsFake(onRegisteringExit);
      sinon.stub(router.page, 'start');
      sinon.stub(router.page, 'base');
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
      const ctx: PageContext = {
        ...createPageContext(),
        params: {0: 'project:foo/bar/baz'},
      };
      assertctxToParams(ctx, 'handleQueryRoute', {
        view: GerritView.SEARCH,
        query: 'project:foo/bar/baz',
        offset: undefined,
      } as AppElementParams);

      ctx.params[1] = '123';
      ctx.params[2] = '123';
      assertctxToParams(ctx, 'handleQueryRoute', {
        view: GerritView.SEARCH,
        query: 'project:foo/bar/baz',
        offset: '123',
      } as AppElementParams);
    });

    test('handleQueryLegacySuffixRoute', () => {
      const params = {...createPageContext(), path: '/q/foo+bar,n,z'};
      router.handleQueryLegacySuffixRoute(params);
      assert.isTrue(redirectStub.calledOnce);
      assert.equal(redirectStub.lastCall.args[0], '/q/foo+bar');
    });

    test('handleChangeIdQueryRoute', () => {
      const ctx = {
        ...createPageContext(),
        params: {0: 'I0123456789abcdef0123456789abcdef01234567'},
      };
      assertctxToParams(ctx, 'handleChangeIdQueryRoute', {
        view: GerritView.SEARCH,
        query: 'I0123456789abcdef0123456789abcdef01234567',
        offset: undefined,
      } as AppElementParams);
    });

    suite('handleRegisterRoute', () => {
      test('happy path', () => {
        const ctx = {...createPageContext(), params: {0: '/foo/bar'}};
        router.handleRegisterRoute(ctx);
        assert.isTrue(redirectStub.calledWithExactly('/foo/bar'));
        assert.isTrue(setStateStub.calledOnce);
        assert.isTrue(setStateStub.lastCall.args[0].justRegistered);
      });

      test('no param', () => {
        const ctx = createPageContext();
        router.handleRegisterRoute(ctx);
        assert.isTrue(redirectStub.calledWithExactly('/'));
        assert.isTrue(setStateStub.calledOnce);
        assert.isTrue(setStateStub.lastCall.args[0].justRegistered);
      });

      test('prevent redirect', () => {
        const ctx = {...createPageContext(), params: {0: '/register'}};
        router.handleRegisterRoute(ctx);
        assert.isTrue(redirectStub.calledWithExactly('/'));
        assert.isTrue(setStateStub.calledOnce);
        assert.isTrue(setStateStub.lastCall.args[0].justRegistered);
      });
    });

    suite('handleRootRoute', () => {
      test('closes for closeAfterLogin', () => {
        const ctx = {...createPageContext(), querystring: 'closeAfterLogin'};
        const closeStub = sinon.stub(window, 'close');
        const result = router.handleRootRoute(ctx);
        assert.isNotOk(result);
        assert.isTrue(closeStub.called);
        assert.isFalse(redirectStub.called);
      });

      test('redirects to dashboard if logged in', () => {
        const ctx = {...createPageContext(), canonicalPath: '/', path: '/'};
        const result = router.handleRootRoute(ctx);
        assert.isOk(result);
        return result!.then(() => {
          assert.isTrue(redirectStub.calledWithExactly('/dashboard/self'));
        });
      });

      test('redirects to open changes if not logged in', () => {
        stubRestApi('getLoggedIn').returns(Promise.resolve(false));
        const ctx = {...createPageContext(), canonicalPath: '/', path: '/'};
        const result = router.handleRootRoute(ctx);
        assert.isOk(result);
        return result!.then(() => {
          assert.isTrue(
            redirectStub.calledWithExactly('/q/status:open+-is:wip')
          );
        });
      });

      suite('GWT hash-path URLs', () => {
        test('redirects hash-path URLs', () => {
          const ctx = {
            ...createPageContext(),
            canonicalPath: '/#/foo/bar/baz',
            hash: '/foo/bar/baz',
          };
          const result = router.handleRootRoute(ctx);
          assert.isNotOk(result);
          assert.isTrue(redirectStub.called);
          assert.isTrue(redirectStub.calledWithExactly('/foo/bar/baz'));
        });

        test('redirects hash-path URLs w/o leading slash', () => {
          const ctx = {
            ...createPageContext(),
            canonicalPath: '/#foo/bar/baz',
            hash: 'foo/bar/baz',
          };
          const result = router.handleRootRoute(ctx);
          assert.isNotOk(result);
          assert.isTrue(redirectStub.called);
          assert.isTrue(redirectStub.calledWithExactly('/foo/bar/baz'));
        });

        test('normalizes "/ /" in hash to "/+/"', () => {
          const ctx = {
            ...createPageContext(),
            canonicalPath: '/#/foo/bar/+/123/4',
            hash: '/foo/bar/ /123/4',
          };
          const result = router.handleRootRoute(ctx);
          assert.isNotOk(result);
          assert.isTrue(redirectStub.called);
          assert.isTrue(redirectStub.calledWithExactly('/foo/bar/+/123/4'));
        });

        test('prepends baseurl to hash-path', () => {
          const ctx = {
            ...createPageContext(),
            canonicalPath: '/#/foo/bar',
            hash: '/foo/bar',
          };
          stubBaseUrl('/baz');
          const result = router.handleRootRoute(ctx);
          assert.isNotOk(result);
          assert.isTrue(redirectStub.called);
          assert.isTrue(redirectStub.calledWithExactly('/baz/foo/bar'));
        });

        test('normalizes /VE/ settings hash-paths', () => {
          const ctx = {
            ...createPageContext(),
            canonicalPath: '/#/VE/foo/bar',
            hash: '/VE/foo/bar',
          };
          const result = router.handleRootRoute(ctx);
          assert.isNotOk(result);
          assert.isTrue(redirectStub.called);
          assert.isTrue(redirectStub.calledWithExactly('/settings/VE/foo/bar'));
        });

        test('does not drop "inner hashes"', () => {
          const ctx = {
            ...createPageContext(),
            canonicalPath: '/#/foo/bar#baz',
            hash: '/foo/bar',
          };
          const result = router.handleRootRoute(ctx);
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
        const ctx = {
          ...createPageContext(),
          canonicalPath: '/dashboard/',
          params: {0: 'seLF'},
        };
        return router.handleDashboardRoute(ctx).then(() => {
          assert.isTrue(redirectToLoginStub.calledOnce);
          assert.isFalse(redirectStub.called);
          assert.isFalse(setStateStub.called);
        });
      });

      test('non-self dashboard but signed out does not redirect', () => {
        stubRestApi('getLoggedIn').returns(Promise.resolve(false));
        const ctx = {
          ...createPageContext(),
          canonicalPath: '/dashboard/',
          params: {0: 'foo'},
        };
        return router.handleDashboardRoute(ctx).then(() => {
          assert.isFalse(redirectToLoginStub.called);
          assert.isFalse(setStateStub.called);
          assert.isTrue(redirectStub.calledOnce);
          assert.equal(redirectStub.lastCall.args[0], '/q/owner:foo');
        });
      });

      test('dashboard while signed in sets params', () => {
        const ctx = {
          ...createPageContext(),
          canonicalPath: '/dashboard/',
          params: {0: 'foo'},
        };
        return router.handleDashboardRoute(ctx).then(() => {
          assert.isFalse(redirectToLoginStub.called);
          assert.isFalse(redirectStub.called);
          assert.isTrue(setStateStub.calledOnce);
          assert.deepEqual(setStateStub.lastCall.args[0], {
            view: GerritView.DASHBOARD,
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
        const ctx: PageContext = {
          ...createPageContext(),
          canonicalPath: '/dashboard/',
          params: {0: ''},
          querystring: '',
        };
        return router.handleCustomDashboardRoute(ctx).then(() => {
          assert.isFalse(setStateStub.called);
          assert.isTrue(redirectStub.called);
          assert.equal(redirectStub.lastCall.args[0], '/dashboard/self');
        });
      });

      test('custom dashboard without title', () => {
        const ctx: PageContext = {
          ...createPageContext(),
          canonicalPath: '/dashboard/',
          params: {0: ''},
          querystring: '?a=b&c&d=e',
        };
        return router.handleCustomDashboardRoute(ctx).then(() => {
          assert.isFalse(redirectStub.called);
          assert.isTrue(setStateStub.calledOnce);
          assert.deepEqual(setStateStub.lastCall.args[0], {
            view: GerritView.DASHBOARD,
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
        const ctx: PageContext = {
          ...createPageContext(),
          canonicalPath: '/dashboard/',
          params: {0: ''},
          querystring: '?a=b&c&d=&=e&title=t',
        };
        return router.handleCustomDashboardRoute(ctx).then(() => {
          assert.isFalse(redirectToLoginStub.called);
          assert.isFalse(redirectStub.called);
          assert.isTrue(setStateStub.calledOnce);
          assert.deepEqual(setStateStub.lastCall.args[0], {
            view: GerritView.DASHBOARD,
            user: 'self',
            sections: [{name: 'a', query: 'b'}],
            title: 't',
          });
        });
      });

      test('custom dashboard with foreach', () => {
        const ctx: PageContext = {
          ...createPageContext(),
          canonicalPath: '/dashboard/',
          params: {0: ''},
          querystring: '?a=b&c&d=&=e&foreach=is:open',
        };
        return router.handleCustomDashboardRoute(ctx).then(() => {
          assert.isFalse(redirectToLoginStub.called);
          assert.isFalse(redirectStub.called);
          assert.isTrue(setStateStub.calledOnce);
          assert.deepEqual(setStateStub.lastCall.args[0], {
            view: GerritView.DASHBOARD,
            user: 'self',
            sections: [{name: 'a', query: 'is:open b'}],
            title: 'Custom Dashboard',
          });
        });
      });
    });

    suite('group routes', () => {
      test('handleGroupInfoRoute', () => {
        const ctx = {...createPageContext(), params: {0: '1234'}};
        router.handleGroupInfoRoute(ctx);
        assert.isTrue(redirectStub.calledOnce);
        assert.equal(redirectStub.lastCall.args[0], '/admin/groups/1234');
      });

      test('handleGroupAuditLogRoute', () => {
        const ctx = {...createPageContext(), params: {0: '1234'}};
        assertctxToParams(ctx, 'handleGroupAuditLogRoute', {
          view: GerritView.GROUP,
          detail: GroupDetailView.LOG,
          groupId: '1234' as GroupId,
        });
      });

      test('handleGroupMembersRoute', () => {
        const ctx = {...createPageContext(), params: {0: '1234'}};
        assertctxToParams(ctx, 'handleGroupMembersRoute', {
          view: GerritView.GROUP,
          detail: GroupDetailView.MEMBERS,
          groupId: '1234' as GroupId,
        });
      });

      test('list of groups', async () => {
        router.startRouter();

        const checkUrlToState = async (
          url: string,
          partial: Partial<AdminViewState>
        ) => {
          console.log(`reset ${Date.now() % 100000} ${setStateStub.callCount}`);
          setStateStub.reset();
          console.log(`show ${Date.now() % 100000} ${setStateStub.callCount}`);
          router.page.show(url);
          console.log(
            `waitUntilCalled ${Date.now() % 100000} ${setStateStub.callCount}`
          );
          await waitUntilCalled(setStateStub, 'setState');
          console.log(`oops ${Date.now() % 100000} ${setStateStub.callCount}`);
          assert.deepEqual(setStateStub.lastCall.firstArg, {
            view: GerritView.ADMIN,
            adminView: AdminChildView.GROUPS,
            offset: '0',
            openCreateModal: false,
            filter: null,
            ...partial,
          });
        };
        const checkUrlNotMatched = async (url: string) => {
          handlePassThroughRoute.reset();
          router.page.show(url);
          await waitUntilCalled(
            handlePassThroughRoute,
            'handlePassThroughRoute'
          );
        };

        // await checkUrlToState('/admin/groups', {});
        await checkUrlToState('/admin/groups/', {});
        await checkUrlToState('/admin/groups#create', {
          openCreateModal: true,
        });
        await checkUrlToState('/admin/groups/', {});
        await checkUrlToState('/admin/groups,123', {offset: '123'});
        await checkUrlToState('/admin/groups,123#create', {
          offset: '123',
          openCreateModal: true,
        });
        await checkUrlToState('/admin/groups/q/filter:asdf,11', {
          filter: 'asdf',
          offset: '11',
        });
        // #create is ignored when filtering
        await checkUrlToState('/admin/groups/q/filter:asdf,11#create', {
          filter: 'asdf',
          offset: '11',
        });
        // filter is decoded (twice)
        await checkUrlToState(
          '/admin/groups/q/filter:XX%20XX%2520XX%252FXX%3FXX',
          {filter: 'XX XX XX/XX?XX'}
        );

        // Slash must be double encoded in `filter` param.
        await checkUrlNotMatched('/admin/groups/q/filter:asdf/qwer,11');
        await checkUrlNotMatched('/admin/groups/q/filter:asdf%2Fqwer,11');
      });

      test('handleGroupRoute', () => {
        const ctx = {...createPageContext(), params: {0: '4321'}};
        assertctxToParams(ctx, 'handleGroupRoute', {
          view: GerritView.GROUP,
          groupId: '4321' as GroupId,
        });
      });
    });

    suite('repo routes', () => {
      test('handleProjectsOldRoute', () => {
        const ctx = {...createPageContext(), params: {}};
        router.handleProjectsOldRoute(ctx);
        assert.isTrue(redirectStub.calledOnce);
        assert.equal(redirectStub.lastCall.args[0], '/admin/repos/');
      });

      test('handleProjectsOldRoute test', () => {
        const ctx = {...createPageContext(), params: {1: 'test'}};
        router.handleProjectsOldRoute(ctx);
        assert.isTrue(redirectStub.calledOnce);
        assert.equal(redirectStub.lastCall.args[0], '/admin/repos/test');
      });

      test('handleProjectsOldRoute test,branches', () => {
        const ctx = {...createPageContext(), params: {1: 'test,branches'}};
        router.handleProjectsOldRoute(ctx);
        assert.isTrue(redirectStub.calledOnce);
        assert.equal(
          redirectStub.lastCall.args[0],
          '/admin/repos/test,branches'
        );
      });

      test('handleRepoRoute', () => {
        const ctx = {...createPageContext(), path: '/admin/repos/test'};
        router.handleRepoRoute(ctx);
        assert.isTrue(redirectStub.calledOnce);
        assert.equal(
          redirectStub.lastCall.args[0],
          '/admin/repos/test,general'
        );
      });

      test('handleRepoGeneralRoute', () => {
        const ctx = {...createPageContext(), params: {0: '4321'}};
        assertctxToParams(ctx, 'handleRepoGeneralRoute', {
          view: GerritView.REPO,
          detail: RepoDetailView.GENERAL,
          repo: '4321' as RepoName,
        });
      });

      test('handleRepoCommandsRoute', () => {
        const ctx = {...createPageContext(), params: {0: '4321'}};
        assertctxToParams(ctx, 'handleRepoCommandsRoute', {
          view: GerritView.REPO,
          detail: RepoDetailView.COMMANDS,
          repo: '4321' as RepoName,
        });
      });

      test('handleRepoAccessRoute', () => {
        const ctx = {...createPageContext(), params: {0: '4321'}};
        assertctxToParams(ctx, 'handleRepoAccessRoute', {
          view: GerritView.REPO,
          detail: RepoDetailView.ACCESS,
          repo: '4321' as RepoName,
        });
      });

      suite('branch list routes', () => {
        test('handleBranchListOffsetRoute', () => {
          const ctx: PageContext = {
            ...createPageContext(),
            params: {0: '4321'},
          };
          assertctxToParams(ctx, 'handleBranchListOffsetRoute', {
            view: GerritView.REPO,
            detail: RepoDetailView.BRANCHES,
            repo: '4321' as RepoName,
            offset: 0,
            filter: null,
          });

          ctx.params[2] = '42';
          assertctxToParams(ctx, 'handleBranchListOffsetRoute', {
            view: GerritView.REPO,
            detail: RepoDetailView.BRANCHES,
            repo: '4321' as RepoName,
            offset: '42',
            filter: null,
          });
        });

        test('handleBranchListFilterOffsetRoute', () => {
          const ctx = {
            ...createPageContext(),
            params: {repo: '4321', filter: 'foo', offset: '42'},
          };
          assertctxToParams(ctx, 'handleBranchListFilterOffsetRoute', {
            view: GerritView.REPO,
            detail: RepoDetailView.BRANCHES,
            repo: '4321' as RepoName,
            offset: '42',
            filter: 'foo',
          });
        });

        test('handleBranchListFilterRoute', () => {
          const ctx = {
            ...createPageContext(),
            params: {repo: '4321', filter: 'foo'},
          };
          assertctxToParams(ctx, 'handleBranchListFilterRoute', {
            view: GerritView.REPO,
            detail: RepoDetailView.BRANCHES,
            repo: '4321' as RepoName,
            filter: 'foo',
          });
        });
      });

      suite('tag list routes', () => {
        test('handleTagListOffsetRoute', () => {
          const ctx = {...createPageContext(), params: {0: '4321'}};
          assertctxToParams(ctx, 'handleTagListOffsetRoute', {
            view: GerritView.REPO,
            detail: RepoDetailView.TAGS,
            repo: '4321' as RepoName,
            offset: 0,
            filter: null,
          });
        });

        test('handleTagListFilterOffsetRoute', () => {
          const ctx = {
            ...createPageContext(),
            params: {repo: '4321', filter: 'foo', offset: '42'},
          };
          assertctxToParams(ctx, 'handleTagListFilterOffsetRoute', {
            view: GerritView.REPO,
            detail: RepoDetailView.TAGS,
            repo: '4321' as RepoName,
            offset: '42',
            filter: 'foo',
          });
        });

        test('handleTagListFilterRoute', () => {
          const ctx: PageContext = {
            ...createPageContext(),
            params: {repo: '4321'},
          };
          assertctxToParams(ctx, 'handleTagListFilterRoute', {
            view: GerritView.REPO,
            detail: RepoDetailView.TAGS,
            repo: '4321' as RepoName,
            filter: null,
          });

          ctx.params.filter = 'foo';
          assertctxToParams(ctx, 'handleTagListFilterRoute', {
            view: GerritView.REPO,
            detail: RepoDetailView.TAGS,
            repo: '4321' as RepoName,
            filter: 'foo',
          });
        });
      });

      suite('repo list routes', () => {
        test('handleRepoListOffsetRoute', () => {
          const ctx = createPageContext();
          assertctxToParams(ctx, 'handleRepoListOffsetRoute', {
            view: GerritView.ADMIN,
            adminView: AdminChildView.REPOS,
            offset: 0,
            filter: null,
            openCreateModal: false,
          });

          ctx.params[1] = '42';
          assertctxToParams(ctx, 'handleRepoListOffsetRoute', {
            view: GerritView.ADMIN,
            adminView: AdminChildView.REPOS,
            offset: '42',
            filter: null,
            openCreateModal: false,
          });

          ctx.hash = 'create';
          assertctxToParams(ctx, 'handleRepoListOffsetRoute', {
            view: GerritView.ADMIN,
            adminView: AdminChildView.REPOS,
            offset: '42',
            filter: null,
            openCreateModal: true,
          });
        });

        test('handleRepoListFilterOffsetRoute', () => {
          const ctx = {
            ...createPageContext(),
            params: {filter: 'foo', offset: '42'},
          };
          assertctxToParams(ctx, 'handleRepoListFilterOffsetRoute', {
            view: GerritView.ADMIN,
            adminView: AdminChildView.REPOS,
            offset: '42',
            filter: 'foo',
          });
        });

        test('handleRepoListFilterRoute', () => {
          const ctx = createPageContext();
          assertctxToParams(ctx, 'handleRepoListFilterRoute', {
            view: GerritView.ADMIN,
            adminView: AdminChildView.REPOS,
            filter: null,
          });

          ctx.params.filter = 'foo';
          assertctxToParams(ctx, 'handleRepoListFilterRoute', {
            view: GerritView.ADMIN,
            adminView: AdminChildView.REPOS,
            filter: 'foo',
          });
        });
      });
    });

    suite('plugin routes', () => {
      test('handlePluginListOffsetRoute', () => {
        const ctx = createPageContext();
        assertctxToParams(ctx, 'handlePluginListOffsetRoute', {
          view: GerritView.ADMIN,
          adminView: AdminChildView.PLUGINS,
          offset: 0,
          filter: null,
        });

        ctx.params[1] = '42';
        assertctxToParams(ctx, 'handlePluginListOffsetRoute', {
          view: GerritView.ADMIN,
          adminView: AdminChildView.PLUGINS,
          offset: '42',
          filter: null,
        });
      });

      test('handlePluginListFilterOffsetRoute', () => {
        const ctx = {
          ...createPageContext(),
          params: {filter: 'foo', offset: '42'},
        };
        assertctxToParams(ctx, 'handlePluginListFilterOffsetRoute', {
          view: GerritView.ADMIN,
          adminView: AdminChildView.PLUGINS,
          offset: '42',
          filter: 'foo',
        });
      });

      test('handlePluginListFilterRoute', () => {
        const ctx = createPageContext();
        assertctxToParams(ctx, 'handlePluginListFilterRoute', {
          view: GerritView.ADMIN,
          adminView: AdminChildView.PLUGINS,
          filter: null,
        });

        ctx.params.filter = 'foo';
        assertctxToParams(ctx, 'handlePluginListFilterRoute', {
          view: GerritView.ADMIN,
          adminView: AdminChildView.PLUGINS,
          filter: 'foo',
        });
      });
    });

    suite('change/diff routes', () => {
      test('handleChangeNumberLegacyRoute', () => {
        const ctx = {...createPageContext(), params: {0: '12345'}};
        router.handleChangeNumberLegacyRoute(ctx);
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
        await waitEventLoop();
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
        function makeParams(_path: string, _hash: string): PageContext {
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
          stubRestApi('setInProjectLookup');
        });

        test('change view', () => {
          const ctx = makeParams('', '');
          assertctxToParams(ctx, 'handleChangeRoute', {
            view: GerritView.CHANGE,
            childView: ChangeChildView.OVERVIEW,
            repo: 'foo/bar' as RepoName,
            changeNum: 1234 as NumericChangeId,
            basePatchNum: 4 as BasePatchSetNum,
            patchNum: 7 as RevisionPatchSetNum,
          });
          assert.isFalse(redirectStub.called);
        });

        test('params', () => {
          const ctx = makeParams('', '');
          const queryMap = new URLSearchParams();
          queryMap.set('tab', 'checks');
          queryMap.set('filter', 'fff');
          queryMap.set('select', 'sss');
          queryMap.set('attempt', '1');
          queryMap.set('checksRunsSelected', 'asdf,qwer');
          queryMap.set('checksResultsFilter', 'asdf.*qwer');
          ctx.querystring = queryMap.toString();
          assertctxToParams(ctx, 'handleChangeRoute', {
            view: GerritView.CHANGE,
            childView: ChangeChildView.OVERVIEW,
            repo: 'foo/bar' as RepoName,
            changeNum: 1234 as NumericChangeId,
            basePatchNum: 4 as BasePatchSetNum,
            patchNum: 7 as RevisionPatchSetNum,
            attempt: 1,
            filter: 'fff',
            tab: 'checks',
            checksRunsSelected: new Set(['asdf', 'qwer']),
            checksResultsFilter: 'asdf.*qwer',
          });
        });
      });

      suite('handleDiffRoute', () => {
        function makeParams(path: string, hash: string): PageContext {
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
          stubRestApi('setInProjectLookup');
        });

        test('diff view', () => {
          const ctx = makeParams('foo/bar/baz', 'b44');
          assertctxToParams(ctx, 'handleDiffRoute', {
            view: GerritView.CHANGE,
            childView: ChangeChildView.DIFF,
            repo: 'foo/bar' as RepoName,
            changeNum: 1234 as NumericChangeId,
            basePatchNum: 4 as BasePatchSetNum,
            patchNum: 7 as RevisionPatchSetNum,
            diffView: {
              path: 'foo/bar/baz',
              lineNum: 44,
              leftSide: true,
            },
          });
          assert.isFalse(redirectStub.called);
        });

        test('comment route base..1', async () => {
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

          const url = `/c/${repo}/+/${changeNum}/comment/${id}/`;
          const groups = url.match(_testOnly_RoutePattern.COMMENT);
          assert.deepEqual(groups!.slice(1), [repo, `${changeNum}`, id]);

          await router.handleCommentRoute({params: groups!.slice(1)} as any);
          assert.isTrue(redirectStub.calledOnce);
          assert.equal(
            redirectStub.lastCall.args[0],
            `/c/${repo}/+/${changeNum}/${ps}/filepath#${line}`
          );
        });

        test('comment route 1..2', async () => {
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

          const url = `/c/${repo}/+/${changeNum}/comment/${id}/`;
          const groups = url.match(_testOnly_RoutePattern.COMMENT);

          // If getDiff() returns a diff with changes, then we will compare
          // the patchset of the comment (1) against latest (2).
          diffStub.onFirstCall().resolves(createDiff());
          await router.handleCommentRoute({params: groups!.slice(1)} as any);
          assert.isTrue(redirectStub.calledOnce);
          assert.equal(
            redirectStub.lastCall.args[0],
            `/c/${repo}/+/${changeNum}/${ps}..2/filepath#b${line}`
          );

          // If getDiff() returns an unchanged diff, then we will compare
          // the patchset of the comment (1) against base.
          diffStub.onSecondCall().resolves({
            ...createDiff(),
            content: [],
          });
          await router.handleCommentRoute({params: groups!.slice(1)} as any);
          assert.isTrue(redirectStub.calledTwice);
          assert.equal(
            redirectStub.lastCall.args[0],
            `/c/${repo}/+/${changeNum}/${ps}/filepath#${line}`
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
          assertctxToParams(
            {params: groups!.slice(1)} as any,
            'handleCommentsRoute',
            {
              repo: 'gerrit' as RepoName,
              changeNum: 264833 as NumericChangeId,
              commentId: '00049681_f34fd6a9' as UrlEncodedCommentId,
              view: GerritView.CHANGE,
              childView: ChangeChildView.OVERVIEW,
            }
          );
        });
      });

      test('handleDiffEditRoute', () => {
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
        const appParams: ChangeViewState = {
          repo: 'foo/bar' as RepoName,
          changeNum: 1234 as NumericChangeId,
          view: GerritView.CHANGE,
          childView: ChangeChildView.EDIT,
          patchNum: 3 as RevisionPatchSetNum,
          editView: {path: 'foo/bar/baz', lineNum: 0},
        };

        router.handleDiffEditRoute(ctx);
        assert.isFalse(redirectStub.called);
        assert.deepEqual(setStateStub.lastCall.args[0], appParams);
      });

      test('handleDiffEditRoute with lineNum', () => {
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
        const appParams: ChangeViewState = {
          repo: 'foo/bar' as RepoName,
          changeNum: 1234 as NumericChangeId,
          view: GerritView.CHANGE,
          childView: ChangeChildView.EDIT,
          patchNum: 3 as RevisionPatchSetNum,
          editView: {path: 'foo/bar/baz', lineNum: 4},
        };

        router.handleDiffEditRoute(ctx);
        assert.isFalse(redirectStub.called);
        assert.deepEqual(setStateStub.lastCall.args[0], appParams);
      });

      test('handleChangeEditRoute', () => {
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
        const appParams: ChangeViewState = {
          repo: 'foo/bar' as RepoName,
          changeNum: 1234 as NumericChangeId,
          view: GerritView.CHANGE,
          childView: ChangeChildView.OVERVIEW,
          patchNum: 3 as RevisionPatchSetNum,
          edit: true,
        };

        router.handleChangeEditRoute(ctx);
        assert.isFalse(redirectStub.called);
        assert.deepEqual(setStateStub.lastCall.args[0], appParams);
      });
    });

    test('handlePluginScreen', () => {
      const ctx = {...createPageContext(), params: {0: 'foo', 1: 'bar'}};
      assertctxToParams(ctx, 'handlePluginScreen', {
        view: GerritView.PLUGIN_SCREEN,
        plugin: 'foo',
        screen: 'bar',
      });
      assert.isFalse(redirectStub.called);
    });
  });
});
