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
import './gr-router';
import {page} from '../../../utils/page-wrapper-utils';
import {
  GenerateUrlChangeViewParameters,
  GenerateUrlDashboardViewParameters,
  GenerateUrlDiffViewParameters,
  GenerateUrlEditViewParameters,
  GenerateUrlGroupViewParameters,
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
  ParentPatchSetNum,
  PatchSetNum,
  RepoName,
  TopicName,
} from '../../../types/common';
import {
  createGerritInfo,
  createServerInfo,
} from '../../../test/test-data-generators';

const basicFixture = fixtureFromElement('gr-router');

suite('gr-router tests', () => {
  let element: GrRouter;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('_firstCodeBrowserWeblink', () => {
    assert.deepEqual(
      element._firstCodeBrowserWeblink([
        {name: 'gitweb'},
        {name: 'gitiles'},
        {name: 'browse'},
        {name: 'test'},
      ]),
      {name: 'gitiles'}
    );

    assert.deepEqual(
      element._firstCodeBrowserWeblink([{name: 'gitweb'}, {name: 'test'}]),
      {name: 'gitweb'}
    );
  });

  test('_getBrowseCommitWeblink', () => {
    const browserLink = {name: 'browser', url: 'browser/url'};
    const link = {name: 'test', url: 'test/url'};
    const weblinks = [browserLink, link];
    const config = {
      ...createServerInfo(),
      gerrit: {...createGerritInfo(), primary_weblink_name: browserLink.name},
    };
    sinon.stub(element, '_firstCodeBrowserWeblink').returns(link);

    assert.deepEqual(
      element._getBrowseCommitWeblink(weblinks, config),
      browserLink
    );

    assert.deepEqual(element._getBrowseCommitWeblink(weblinks), link);
  });

  test('_getChangeWeblinks', () => {
    const link = {name: 'test', url: 'test/url'};
    const browserLink = {name: 'browser', url: 'browser/url'};
    const mapLinksToConfig = (weblinks: any) => {
      return {
        type: 'change' as WeblinkType.CHANGE,
        repo: 'test' as RepoName,
        commit: '111' as CommitId,
        options: {weblinks},
      };
    };
    sinon.stub(element, '_getBrowseCommitWeblink').returns(browserLink);

    assert.deepEqual(
      element._getChangeWeblinks(mapLinksToConfig([link, browserLink]))[0],
      {name: 'test', url: 'test/url'}
    );

    assert.deepEqual(element._getChangeWeblinks(mapLinksToConfig([link]))[0], {
      name: 'test',
      url: 'test/url',
    });

    link.url = 'https://' + link.url;
    assert.deepEqual(element._getChangeWeblinks(mapLinksToConfig([link]))[0], {
      name: 'test',
      url: 'https://test/url',
    });
  });

  test('_getHashFromCanonicalPath', () => {
    let url = '/foo/bar';
    let hash = element._getHashFromCanonicalPath(url);
    assert.equal(hash, '');

    url = '';
    hash = element._getHashFromCanonicalPath(url);
    assert.equal(hash, '');

    url = '/foo#bar';
    hash = element._getHashFromCanonicalPath(url);
    assert.equal(hash, 'bar');

    url = '/foo#bar#baz';
    hash = element._getHashFromCanonicalPath(url);
    assert.equal(hash, 'bar#baz');

    url = '#foo#bar#baz';
    hash = element._getHashFromCanonicalPath(url);
    assert.equal(hash, 'foo#bar#baz');
  });

  suite('_parseLineAddress', () => {
    test('returns null for empty and invalid hashes', () => {
      let actual = element._parseLineAddress('');
      assert.isNull(actual);

      actual = element._parseLineAddress('foobar');
      assert.isNull(actual);

      actual = element._parseLineAddress('foo123');
      assert.isNull(actual);

      actual = element._parseLineAddress('123bar');
      assert.isNull(actual);
    });

    test('parses correctly', () => {
      let actual = element._parseLineAddress('1234');
      assert.isOk(actual);
      assert.equal(actual!.lineNum, 1234);
      assert.isFalse(actual!.leftSide);

      actual = element._parseLineAddress('a4');
      assert.isOk(actual);
      assert.equal(actual!.lineNum, 4);
      assert.isTrue(actual!.leftSide);

      actual = element._parseLineAddress('b77');
      assert.isOk(actual);
      assert.equal(actual!.lineNum, 77);
      assert.isTrue(actual!.leftSide);
    });
  });

  test('_startRouter requires auth for the right handlers', () => {
    // This test encodes the lists of route handler methods that gr-router
    // automatically checks for authentication before triggering.

    const requiresAuth: any = {};
    const doesNotRequireAuth: any = {};
    sinon.stub(GerritNav, 'setup');
    sinon.stub(page, 'start');
    sinon.stub(page, 'base');
    sinon
      .stub(element, '_mapRoute')
      .callsFake((_pattern, methodName, usesAuth) => {
        if (usesAuth) {
          requiresAuth[methodName] = true;
        } else {
          doesNotRequireAuth[methodName] = true;
        }
      });
    element._startRouter();

    const actualRequiresAuth = Object.keys(requiresAuth);
    actualRequiresAuth.sort();
    const actualDoesNotRequireAuth = Object.keys(doesNotRequireAuth);
    actualDoesNotRequireAuth.sort();

    const shouldRequireAutoAuth = [
      '_handleAgreementsRoute',
      '_handleChangeEditRoute',
      '_handleCreateGroupRoute',
      '_handleCreateProjectRoute',
      '_handleDiffEditRoute',
      '_handleGroupAuditLogRoute',
      '_handleGroupInfoRoute',
      '_handleGroupListFilterOffsetRoute',
      '_handleGroupListFilterRoute',
      '_handleGroupListOffsetRoute',
      '_handleGroupMembersRoute',
      '_handleGroupRoute',
      '_handleGroupSelfRedirectRoute',
      '_handleNewAgreementsRoute',
      '_handlePluginListFilterOffsetRoute',
      '_handlePluginListFilterRoute',
      '_handlePluginListOffsetRoute',
      '_handlePluginListRoute',
      '_handleRepoCommandsRoute',
      '_handleSettingsLegacyRoute',
      '_handleSettingsRoute',
    ];
    assert.deepEqual(actualRequiresAuth, shouldRequireAutoAuth);

    const unauthenticatedHandlers = [
      '_handleBranchListFilterOffsetRoute',
      '_handleBranchListFilterRoute',
      '_handleBranchListOffsetRoute',
      '_handleChangeIdQueryRoute',
      '_handleChangeNumberLegacyRoute',
      '_handleChangeRoute',
      '_handleCommentRoute',
      '_handleCommentsRoute',
      '_handleDiffRoute',
      '_handleDefaultRoute',
      '_handleChangeLegacyRoute',
      '_handleDocumentationRedirectRoute',
      '_handleDocumentationSearchRoute',
      '_handleDocumentationSearchRedirectRoute',
      '_handleLegacyLinenum',
      '_handleImproperlyEncodedPlusRoute',
      '_handlePassThroughRoute',
      '_handleProjectDashboardRoute',
      '_handleLegacyProjectDashboardRoute',
      '_handleProjectsOldRoute',
      '_handleRepoAccessRoute',
      '_handleRepoDashboardsRoute',
      '_handleRepoGeneralRoute',
      '_handleRepoListFilterOffsetRoute',
      '_handleRepoListFilterRoute',
      '_handleRepoListOffsetRoute',
      '_handleRepoRoute',
      '_handleQueryLegacySuffixRoute',
      '_handleQueryRoute',
      '_handleRegisterRoute',
      '_handleTagListFilterOffsetRoute',
      '_handleTagListFilterRoute',
      '_handleTagListOffsetRoute',
      '_handlePluginScreen',
    ];

    // Handler names that check authentication themselves, and thus don't need
    // it performed for them.
    const selfAuthenticatingHandlers = [
      '_handleDashboardRoute',
      '_handleCustomDashboardRoute',
      '_handleRootRoute',
    ];

    const shouldNotRequireAuth = unauthenticatedHandlers.concat(
      selfAuthenticatingHandlers
    );
    shouldNotRequireAuth.sort();
    assert.deepEqual(actualDoesNotRequireAuth, shouldNotRequireAuth);
  });

  test('_redirectIfNotLoggedIn while logged in', () => {
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
    const redirectStub = sinon.stub(element, '_redirectToLogin');
    return element._redirectIfNotLoggedIn(data).then(() => {
      assert.isFalse(redirectStub.called);
    });
  });

  test('_redirectIfNotLoggedIn while logged out', () => {
    stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    const redirectStub = sinon.stub(element, '_redirectToLogin');
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
      element
        ._redirectIfNotLoggedIn(data)
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
        element._generateUrl(params),
        '/q/owner:a%2525b+project:c%2525d+branch:e%2525f+' +
          'topic:g%2525h+status:op%2525en'
      );

      params.offset = 100;
      assert.equal(
        element._generateUrl(params),
        '/q/owner:a%2525b+project:c%2525d+branch:e%2525f+' +
          'topic:g%2525h+status:op%2525en,100'
      );
      delete params.offset;

      // The presence of the query param overrides other params.
      params.query = 'foo$bar';
      assert.equal(element._generateUrl(params), '/q/foo%2524bar');

      params.offset = 100;
      assert.equal(element._generateUrl(params), '/q/foo%2524bar,100');

      params = {
        view: GerritNav.View.SEARCH,
        statuses: ['a', 'b', 'c'],
      };
      assert.equal(
        element._generateUrl(params),
        '/q/(status:a OR status:b OR status:c)'
      );

      params = {
        view: GerritNav.View.SEARCH,
        topic: 'test' as TopicName,
      };
      assert.equal(element._generateUrl(params), '/q/topic:test');
      params = {
        view: GerritNav.View.SEARCH,
        topic: 'test test' as TopicName,
      };
      assert.equal(element._generateUrl(params), '/q/topic:"test+test"');
      params = {
        view: GerritNav.View.SEARCH,
        topic: 'test:test' as TopicName,
      };
      assert.equal(element._generateUrl(params), '/q/topic:"test:test"');
    });

    test('change', () => {
      const params: GenerateUrlChangeViewParameters = {
        view: GerritView.CHANGE,
        changeNum: 1234 as NumericChangeId,
        project: 'test' as RepoName,
      };

      assert.equal(element._generateUrl(params), '/c/test/+/1234');

      params.patchNum = 10 as PatchSetNum;
      assert.equal(element._generateUrl(params), '/c/test/+/1234/10');

      params.basePatchNum = 5 as BasePatchSetNum;
      assert.equal(element._generateUrl(params), '/c/test/+/1234/5..10');

      params.messageHash = '#123';
      assert.equal(element._generateUrl(params), '/c/test/+/1234/5..10#123');
    });

    test('change with repo name encoding', () => {
      const params: GenerateUrlChangeViewParameters = {
        view: GerritView.CHANGE,
        changeNum: 1234 as NumericChangeId,
        project: 'x+/y+/z+/w' as RepoName,
      };
      assert.equal(
        element._generateUrl(params),
        '/c/x%252B/y%252B/z%252B/w/+/1234'
      );
    });

    test('diff', () => {
      const params: GenerateUrlDiffViewParameters = {
        view: GerritView.DIFF,
        changeNum: 42 as NumericChangeId,
        path: 'x+y/path.cpp' as RepoName,
        patchNum: 12 as PatchSetNum,
        project: '' as RepoName,
      };
      assert.equal(element._generateUrl(params), '/c/42/12/x%252By/path.cpp');

      params.project = 'test' as RepoName;
      assert.equal(
        element._generateUrl(params),
        '/c/test/+/42/12/x%252By/path.cpp'
      );

      params.basePatchNum = 6 as BasePatchSetNum;
      assert.equal(
        element._generateUrl(params),
        '/c/test/+/42/6..12/x%252By/path.cpp'
      );

      params.path = 'foo bar/my+file.txt%';
      params.patchNum = 2 as PatchSetNum;
      delete params.basePatchNum;
      assert.equal(
        element._generateUrl(params),
        '/c/test/+/42/2/foo+bar/my%252Bfile.txt%2525'
      );

      params.path = 'file.cpp';
      params.lineNum = 123;
      assert.equal(element._generateUrl(params), '/c/test/+/42/2/file.cpp#123');

      params.leftSide = true;
      assert.equal(
        element._generateUrl(params),
        '/c/test/+/42/2/file.cpp#b123'
      );
    });

    test('diff with repo name encoding', () => {
      const params: GenerateUrlDiffViewParameters = {
        view: GerritView.DIFF,
        changeNum: 42 as NumericChangeId,
        path: 'x+y/path.cpp',
        patchNum: 12 as PatchSetNum,
        project: 'x+/y' as RepoName,
      };
      assert.equal(
        element._generateUrl(params),
        '/c/x%252B/y/+/42/12/x%252By/path.cpp'
      );
    });

    test('edit', () => {
      const params: GenerateUrlEditViewParameters = {
        view: GerritView.EDIT,
        changeNum: 42 as NumericChangeId,
        project: 'test' as RepoName,
        path: 'x+y/path.cpp',
        patchNum: 'edit' as PatchSetNum,
      };
      assert.equal(
        element._generateUrl(params),
        '/c/test/+/42/edit/x%252By/path.cpp,edit'
      );
    });

    test('_getPatchRangeExpression', () => {
      const params: PatchRangeParams = {};
      let actual = element._getPatchRangeExpression(params);
      assert.equal(actual, '');

      params.patchNum = 4 as PatchSetNum;
      actual = element._getPatchRangeExpression(params);
      assert.equal(actual, '4');

      params.basePatchNum = 2 as BasePatchSetNum;
      actual = element._getPatchRangeExpression(params);
      assert.equal(actual, '2..4');

      delete params.patchNum;
      actual = element._getPatchRangeExpression(params);
      assert.equal(actual, '2..');
    });

    suite('dashboard', () => {
      test('self dashboard', () => {
        const params: GenerateUrlDashboardViewParameters = {
          view: GerritView.DASHBOARD,
        };
        assert.equal(element._generateUrl(params), '/dashboard/self');
      });

      test('user dashboard', () => {
        const params: GenerateUrlDashboardViewParameters = {
          view: GerritView.DASHBOARD,
          user: 'user',
        };
        assert.equal(element._generateUrl(params), '/dashboard/user');
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
          element._generateUrl(params),
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
          element._generateUrl(params),
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
          element._generateUrl(params),
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
          element._generateUrl(params),
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
          element._generateUrl(params),
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
        assert.equal(element._generateUrl(params), '/admin/groups/1234');
      });

      test('group members', () => {
        const params: GenerateUrlGroupViewParameters = {
          view: GerritView.GROUP,
          groupId: '1234' as GroupId,
          detail: 'members' as GroupDetailView,
        };
        assert.equal(
          element._generateUrl(params),
          '/admin/groups/1234,members'
        );
      });

      test('group audit log', () => {
        const params: GenerateUrlGroupViewParameters = {
          view: GerritView.GROUP,
          groupId: '1234' as GroupId,
          detail: 'log' as GroupDetailView,
        };
        assert.equal(
          element._generateUrl(params),
          '/admin/groups/1234,audit-log'
        );
      });
    });
  });

  suite('param normalization', () => {
    suite('_normalizePatchRangeParams', () => {
      test('range n..n normalizes to n', () => {
        const params: PatchRangeParams = {
          basePatchNum: 4 as BasePatchSetNum,
          patchNum: 4 as PatchSetNum,
        };
        const needsRedirect = element._normalizePatchRangeParams(params);
        assert.isTrue(needsRedirect);
        assert.equal(params.basePatchNum, ParentPatchSetNum);
        assert.equal(params.patchNum, 4 as PatchSetNum);
      });

      test('range n.. normalizes to n', () => {
        const params: PatchRangeParams = {basePatchNum: 4 as BasePatchSetNum};
        const needsRedirect = element._normalizePatchRangeParams(params);
        assert.isFalse(needsRedirect);
        assert.equal(params.basePatchNum, ParentPatchSetNum);
        assert.equal(params.patchNum, 4 as PatchSetNum);
      });
    });
  });

  suite('route handlers', () => {
    let redirectStub: sinon.SinonStub;
    let setParamsStub: sinon.SinonStub;
    let handlePassThroughRoute: any;

    // Simple route handlers are direct mappings from parsed route data to a
    // new set of app.params. This test helper asserts that passing `data`
    // into `methodName` results in setting the params specified in `params`.
    function assertDataToParams(
      data: PageContextWithQueryMap,
      methodName: string,
      params: Object
    ) {
      (element as any)[methodName](data);
      assert.deepEqual(setParamsStub.lastCall.args[0], params);
    }

    setup(() => {
      redirectStub = sinon.stub(element, '_redirect');
      setParamsStub = sinon.stub(element, '_setParams');
      handlePassThroughRoute = sinon.stub(element, '_handlePassThroughRoute');
    });

    test('_handleLegacyProjectDashboardRoute', () => {
      const params: PageContextWithQueryMap = {
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
        params: {0: 'gerrit/project', 1: 'dashboard:main'},
      };
      element._handleLegacyProjectDashboardRoute(params);
      assert.isTrue(redirectStub.calledOnce);
      assert.equal(
        redirectStub.lastCall.args[0],
        '/p/gerrit/project/+/dashboard/dashboard:main'
      );
    });

    test('_handleAgreementsRoute', () => {
      element._handleAgreementsRoute();
      assert.isTrue(redirectStub.calledOnce);
      assert.equal(redirectStub.lastCall.args[0], '/settings/#Agreements');
    });

    test('_handleNewAgreementsRoute', () => {
      const params: PageContextWithQueryMap = {
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
      element._handleNewAgreementsRoute(params);
      assert.isTrue(setParamsStub.calledOnce);
      assert.equal(
        setParamsStub.lastCall.args[0].view,
        GerritNav.View.AGREEMENTS
      );
    });

    test('_handleSettingsLegacyRoute', () => {
      const data: PageContextWithQueryMap = {
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
        params: {0: 'my-token'},
      };
      assertDataToParams(data, '_handleSettingsLegacyRoute', {
        view: GerritNav.View.SETTINGS,
        emailToken: 'my-token',
      });
    });

    test('_handleSettingsLegacyRoute with +', () => {
      const data: PageContextWithQueryMap = {
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
        params: {0: 'my-token test'},
      };
      assertDataToParams(data, '_handleSettingsLegacyRoute', {
        view: GerritNav.View.SETTINGS,
        emailToken: 'my-token+test',
      });
    });

    test('_handleSettingsRoute', () => {
      const data: PageContextWithQueryMap = {
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
      assertDataToParams(data, '_handleSettingsRoute', {
        view: GerritNav.View.SETTINGS,
      });
    });

    test('_handleDefaultRoute on first load', () => {
      const spy = sinon.spy();
      addListenerForTest(document, 'page-error', spy);
      element._handleDefaultRoute();
      assert.isTrue(spy.calledOnce);
      assert.equal(spy.lastCall.args[0].detail.response.status, 404);
    });

    test('_handleDefaultRoute after internal navigation', () => {
      let onExit: any = null;
      const onRegisteringExit = (_match: any, _onExit: any) => {
        onExit = _onExit;
      };
      sinon.stub(page, 'exit').callsFake(onRegisteringExit);
      sinon.stub(GerritNav, 'setup');
      sinon.stub(page, 'start');
      sinon.stub(page, 'base');
      element._startRouter();

      element._handleDefaultRoute();

      onExit!('', () => {}); // we left page;

      element._handleDefaultRoute();
      assert.isTrue(handlePassThroughRoute.calledOnce);
    });

    test('_handleImproperlyEncodedPlusRoute', () => {
      const params: PageContextWithQueryMap = {
        queryMap: new Map(),
        save() {},
        handled: true,
        canonicalPath: '/c/test/%20/42',
        path: '',
        querystring: '',
        pathname: '',
        state: '',
        title: '',
        hash: '',
        params: {0: 'test', 1: '42'},
      };
      // Regression test for Issue 7100.
      element._handleImproperlyEncodedPlusRoute(params);
      assert.isTrue(redirectStub.calledOnce);
      assert.equal(redirectStub.lastCall.args[0], '/c/test/+/42');

      sinon.stub(element, '_getHashFromCanonicalPath').returns('foo');
      element._handleImproperlyEncodedPlusRoute(params);
      assert.equal(redirectStub.lastCall.args[0], '/c/test/+/42#foo');
    });

    test('_handleQueryRoute', () => {
      const data: PageContextWithQueryMap = {
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
        params: {0: 'project:foo/bar/baz'},
      };
      assertDataToParams(data, '_handleQueryRoute', {
        view: GerritNav.View.SEARCH,
        query: 'project:foo/bar/baz',
        offset: undefined,
      });

      data.params[1] = '123';
      data.params[2] = '123';
      assertDataToParams(data, '_handleQueryRoute', {
        view: GerritNav.View.SEARCH,
        query: 'project:foo/bar/baz',
        offset: '123',
      });
    });

    test('_handleQueryLegacySuffixRoute', () => {
      const params: PageContextWithQueryMap = {
        queryMap: new Map(),
        save() {},
        handled: true,
        canonicalPath: '',
        path: '/q/foo+bar,n,z',
        querystring: '',
        pathname: '',
        state: '',
        title: '',
        hash: '',
        params: {},
      };
      element._handleQueryLegacySuffixRoute(params);
      assert.isTrue(redirectStub.calledOnce);
      assert.equal(redirectStub.lastCall.args[0], '/q/foo+bar');
    });

    test('_handleChangeIdQueryRoute', () => {
      const data: PageContextWithQueryMap = {
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
        params: {0: 'I0123456789abcdef0123456789abcdef01234567'},
      };
      assertDataToParams(data, '_handleChangeIdQueryRoute', {
        view: GerritNav.View.SEARCH,
        query: 'I0123456789abcdef0123456789abcdef01234567',
      });
    });

    suite('_handleRegisterRoute', () => {
      test('happy path', () => {
        const ctx: PageContextWithQueryMap = {
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
          params: {0: '/foo/bar'},
        };
        element._handleRegisterRoute(ctx);
        assert.isTrue(redirectStub.calledWithExactly('/foo/bar'));
        assert.isTrue(setParamsStub.calledOnce);
        assert.isTrue(setParamsStub.lastCall.args[0].justRegistered);
      });

      test('no param', () => {
        const ctx: PageContextWithQueryMap = {
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
        element._handleRegisterRoute(ctx);
        assert.isTrue(redirectStub.calledWithExactly('/'));
        assert.isTrue(setParamsStub.calledOnce);
        assert.isTrue(setParamsStub.lastCall.args[0].justRegistered);
      });

      test('prevent redirect', () => {
        const ctx: PageContextWithQueryMap = {
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
          params: {0: '/register'},
        };
        element._handleRegisterRoute(ctx);
        assert.isTrue(redirectStub.calledWithExactly('/'));
        assert.isTrue(setParamsStub.calledOnce);
        assert.isTrue(setParamsStub.lastCall.args[0].justRegistered);
      });
    });

    suite('_handleRootRoute', () => {
      test('closes for closeAfterLogin', () => {
        const data: PageContextWithQueryMap = {
          queryMap: new Map(),
          save() {},
          handled: true,
          canonicalPath: '',
          path: '',
          querystring: 'closeAfterLogin',
          pathname: '',
          state: '',
          title: '',
          hash: '',
          params: {},
        };
        const closeStub = sinon.stub(window, 'close');
        const result = element._handleRootRoute(data);
        assert.isNotOk(result);
        assert.isTrue(closeStub.called);
        assert.isFalse(redirectStub.called);
      });

      test('redirects to dashboard if logged in', () => {
        const data: PageContextWithQueryMap = {
          queryMap: new Map(),
          save() {},
          handled: true,
          canonicalPath: '/',
          path: '/',
          querystring: '',
          pathname: '',
          state: '',
          title: '',
          hash: '',
          params: {},
        };
        const result = element._handleRootRoute(data);
        assert.isOk(result);
        return result!.then(() => {
          assert.isTrue(redirectStub.calledWithExactly('/dashboard/self'));
        });
      });

      test('redirects to open changes if not logged in', () => {
        stubRestApi('getLoggedIn').returns(Promise.resolve(false));
        const data: PageContextWithQueryMap = {
          queryMap: new Map(),
          save() {},
          handled: true,
          canonicalPath: '/',
          path: '/',
          querystring: '',
          pathname: '',
          state: '',
          title: '',
          hash: '',
          params: {},
        };
        const result = element._handleRootRoute(data);
        assert.isOk(result);
        return result!.then(() => {
          assert.isTrue(
            redirectStub.calledWithExactly('/q/status:open+-is:wip')
          );
        });
      });

      suite('GWT hash-path URLs', () => {
        test('redirects hash-path URLs', () => {
          const data: PageContextWithQueryMap = {
            queryMap: new Map(),
            save() {},
            handled: true,
            canonicalPath: '/#/foo/bar/baz',
            path: '',
            querystring: '',
            pathname: '',
            state: '',
            title: '',
            hash: '/foo/bar/baz',
            params: {},
          };
          const result = element._handleRootRoute(data);
          assert.isNotOk(result);
          assert.isTrue(redirectStub.called);
          assert.isTrue(redirectStub.calledWithExactly('/foo/bar/baz'));
        });

        test('redirects hash-path URLs w/o leading slash', () => {
          const data: PageContextWithQueryMap = {
            queryMap: new Map(),
            save() {},
            handled: true,
            canonicalPath: '/#foo/bar/baz',
            path: '',
            querystring: '',
            pathname: '',
            state: '',
            title: '',
            hash: 'foo/bar/baz',
            params: {},
          };
          const result = element._handleRootRoute(data);
          assert.isNotOk(result);
          assert.isTrue(redirectStub.called);
          assert.isTrue(redirectStub.calledWithExactly('/foo/bar/baz'));
        });

        test('normalizes "/ /" in hash to "/+/"', () => {
          const data: PageContextWithQueryMap = {
            queryMap: new Map(),
            save() {},
            handled: true,
            canonicalPath: '/#/foo/bar/+/123/4',
            path: '',
            querystring: '',
            pathname: '',
            state: '',
            title: '',
            hash: '/foo/bar/ /123/4',
            params: {},
          };
          const result = element._handleRootRoute(data);
          assert.isNotOk(result);
          assert.isTrue(redirectStub.called);
          assert.isTrue(redirectStub.calledWithExactly('/foo/bar/+/123/4'));
        });

        test('prepends baseurl to hash-path', () => {
          const data: PageContextWithQueryMap = {
            queryMap: new Map(),
            save() {},
            handled: true,
            canonicalPath: '/#/foo/bar',
            path: '',
            querystring: '',
            pathname: '',
            state: '',
            title: '',
            hash: '/foo/bar',
            params: {},
          };
          stubBaseUrl('/baz');
          const result = element._handleRootRoute(data);
          assert.isNotOk(result);
          assert.isTrue(redirectStub.called);
          assert.isTrue(redirectStub.calledWithExactly('/baz/foo/bar'));
        });

        test('normalizes /VE/ settings hash-paths', () => {
          const data: PageContextWithQueryMap = {
            queryMap: new Map(),
            save() {},
            handled: true,
            canonicalPath: '/#/VE/foo/bar',
            path: '',
            querystring: '',
            pathname: '',
            state: '',
            title: '',
            hash: '/VE/foo/bar',
            params: {},
          };
          const result = element._handleRootRoute(data);
          assert.isNotOk(result);
          assert.isTrue(redirectStub.called);
          assert.isTrue(redirectStub.calledWithExactly('/settings/VE/foo/bar'));
        });

        test('does not drop "inner hashes"', () => {
          const data: PageContextWithQueryMap = {
            queryMap: new Map(),
            save() {},
            handled: true,
            canonicalPath: '/#/foo/bar#baz',
            path: '',
            querystring: '',
            pathname: '',
            state: '',
            title: '',
            hash: '/foo/bar',
            params: {},
          };
          const result = element._handleRootRoute(data);
          assert.isNotOk(result);
          assert.isTrue(redirectStub.called);
          assert.isTrue(redirectStub.calledWithExactly('/foo/bar#baz'));
        });
      });
    });

    suite('_handleDashboardRoute', () => {
      let redirectToLoginStub: sinon.SinonStub;

      setup(() => {
        redirectToLoginStub = sinon.stub(element, '_redirectToLogin');
      });

      test('own dashboard but signed out redirects to login', () => {
        stubRestApi('getLoggedIn').returns(Promise.resolve(false));
        const data: PageContextWithQueryMap = {
          queryMap: new Map(),
          save() {},
          handled: true,
          canonicalPath: '/dashboard/',
          path: '',
          querystring: '',
          pathname: '',
          state: '',
          title: '',
          hash: '',
          params: {0: 'seLF'},
        };
        return element._handleDashboardRoute(data).then(() => {
          assert.isTrue(redirectToLoginStub.calledOnce);
          assert.isFalse(redirectStub.called);
          assert.isFalse(setParamsStub.called);
        });
      });

      test('non-self dashboard but signed out does not redirect', () => {
        stubRestApi('getLoggedIn').returns(Promise.resolve(false));
        const data: PageContextWithQueryMap = {
          queryMap: new Map(),
          save() {},
          handled: true,
          canonicalPath: '/dashboard/',
          path: '',
          querystring: '',
          pathname: '',
          state: '',
          title: '',
          hash: '',
          params: {0: 'foo'},
        };
        return element._handleDashboardRoute(data).then(() => {
          assert.isFalse(redirectToLoginStub.called);
          assert.isFalse(setParamsStub.called);
          assert.isTrue(redirectStub.calledOnce);
          assert.equal(redirectStub.lastCall.args[0], '/q/owner:foo');
        });
      });

      test('dashboard while signed in sets params', () => {
        const data: PageContextWithQueryMap = {
          queryMap: new Map(),
          save() {},
          handled: true,
          canonicalPath: '/dashboard/',
          path: '',
          querystring: '',
          pathname: '',
          state: '',
          title: '',
          hash: '',
          params: {0: 'foo'},
        };
        return element._handleDashboardRoute(data).then(() => {
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

    suite('_handleCustomDashboardRoute', () => {
      let redirectToLoginStub: sinon.SinonStub;

      setup(() => {
        redirectToLoginStub = sinon.stub(element, '_redirectToLogin');
      });

      test('no user specified', () => {
        const data: PageContextWithQueryMap = {
          queryMap: new Map(),
          save() {},
          handled: true,
          canonicalPath: '/dashboard/',
          path: '',
          querystring: '',
          pathname: '',
          state: '',
          title: '',
          hash: '',
          params: {0: ''},
        };
        return element._handleCustomDashboardRoute(data, '').then(() => {
          assert.isFalse(setParamsStub.called);
          assert.isTrue(redirectStub.called);
          assert.equal(redirectStub.lastCall.args[0], '/dashboard/self');
        });
      });

      test('custom dashboard without title', () => {
        const data: PageContextWithQueryMap = {
          queryMap: new Map(),
          save() {},
          handled: true,
          canonicalPath: '/dashboard/',
          path: '',
          querystring: '',
          pathname: '',
          state: '',
          title: '',
          hash: '',
          params: {0: ''},
        };
        return element
          ._handleCustomDashboardRoute(data, '?a=b&c&d=e')
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
        const data: PageContextWithQueryMap = {
          queryMap: new Map(),
          save() {},
          handled: true,
          canonicalPath: '/dashboard/',
          path: '',
          querystring: '',
          pathname: '',
          state: '',
          title: '',
          hash: '',
          params: {0: ''},
        };
        return element
          ._handleCustomDashboardRoute(data, '?a=b&c&d=&=e&title=t')
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
        const data: PageContextWithQueryMap = {
          queryMap: new Map(),
          save() {},
          handled: true,
          canonicalPath: '/dashboard/',
          path: '',
          querystring: '',
          pathname: '',
          state: '',
          title: '',
          hash: '',
          params: {0: ''},
        };
        return element
          ._handleCustomDashboardRoute(data, '?a=b&c&d=&=e&foreach=is:open')
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
      test('_handleGroupInfoRoute', () => {
        const data: PageContextWithQueryMap = {
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
          params: {0: '1234'},
        };
        element._handleGroupInfoRoute(data);
        assert.isTrue(redirectStub.calledOnce);
        assert.equal(redirectStub.lastCall.args[0], '/admin/groups/1234');
      });

      test('_handleGroupAuditLogRoute', () => {
        const data: PageContextWithQueryMap = {
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
          params: {0: '1234'},
        };
        assertDataToParams(data, '_handleGroupAuditLogRoute', {
          view: GerritNav.View.GROUP,
          detail: 'log',
          groupId: '1234',
        });
      });

      test('_handleGroupMembersRoute', () => {
        const data: PageContextWithQueryMap = {
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
          params: {0: '1234'},
        };
        assertDataToParams(data, '_handleGroupMembersRoute', {
          view: GerritNav.View.GROUP,
          detail: 'members',
          groupId: '1234',
        });
      });

      test('_handleGroupListOffsetRoute', () => {
        const data: PageContextWithQueryMap = {
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
        assertDataToParams(data, '_handleGroupListOffsetRoute', {
          view: GerritNav.View.ADMIN,
          adminView: 'gr-admin-group-list',
          offset: 0,
          filter: null,
          openCreateModal: false,
        });

        data.params[1] = '42';
        assertDataToParams(data, '_handleGroupListOffsetRoute', {
          view: GerritNav.View.ADMIN,
          adminView: 'gr-admin-group-list',
          offset: '42',
          filter: null,
          openCreateModal: false,
        });

        data.hash = 'create';
        assertDataToParams(data, '_handleGroupListOffsetRoute', {
          view: GerritNav.View.ADMIN,
          adminView: 'gr-admin-group-list',
          offset: '42',
          filter: null,
          openCreateModal: true,
        });
      });

      test('_handleGroupListFilterOffsetRoute', () => {
        const data: PageContextWithQueryMap = {
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
          params: {filter: 'foo', offset: '42'},
        };
        assertDataToParams(data, '_handleGroupListFilterOffsetRoute', {
          view: GerritNav.View.ADMIN,
          adminView: 'gr-admin-group-list',
          offset: '42',
          filter: 'foo',
        });
      });

      test('_handleGroupListFilterRoute', () => {
        const data: PageContextWithQueryMap = {
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
          params: {filter: 'foo'},
        };
        assertDataToParams(data, '_handleGroupListFilterRoute', {
          view: GerritNav.View.ADMIN,
          adminView: 'gr-admin-group-list',
          filter: 'foo',
        });
      });

      test('_handleGroupRoute', () => {
        const data: PageContextWithQueryMap = {
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
          params: {0: '4321'},
        };
        assertDataToParams(data, '_handleGroupRoute', {
          view: GerritNav.View.GROUP,
          groupId: '4321',
        });
      });
    });

    suite('repo routes', () => {
      test('_handleProjectsOldRoute', () => {
        const data: PageContextWithQueryMap = {
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
        element._handleProjectsOldRoute(data);
        assert.isTrue(redirectStub.calledOnce);
        assert.equal(redirectStub.lastCall.args[0], '/admin/repos/');
      });

      test('_handleProjectsOldRoute test', () => {
        const data: PageContextWithQueryMap = {
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
          params: {1: 'test'},
        };
        element._handleProjectsOldRoute(data);
        assert.isTrue(redirectStub.calledOnce);
        assert.equal(redirectStub.lastCall.args[0], '/admin/repos/test');
      });

      test('_handleProjectsOldRoute test,branches', () => {
        const data: PageContextWithQueryMap = {
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
          params: {1: 'test,branches'},
        };
        element._handleProjectsOldRoute(data);
        assert.isTrue(redirectStub.calledOnce);
        assert.equal(
          redirectStub.lastCall.args[0],
          '/admin/repos/test,branches'
        );
      });

      test('_handleRepoRoute', () => {
        const data: PageContextWithQueryMap = {
          queryMap: new Map(),
          save() {},
          handled: true,
          canonicalPath: '',
          path: '/admin/repos/test',
          querystring: '',
          pathname: '',
          state: '',
          title: '',
          hash: '',
          params: {},
        };
        element._handleRepoRoute(data);
        assert.isTrue(redirectStub.calledOnce);
        assert.equal(
          redirectStub.lastCall.args[0],
          '/admin/repos/test,general'
        );
      });

      test('_handleRepoGeneralRoute', () => {
        const data: PageContextWithQueryMap = {
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
          params: {0: '4321'},
        };
        assertDataToParams(data, '_handleRepoGeneralRoute', {
          view: GerritNav.View.REPO,
          detail: GerritNav.RepoDetailView.GENERAL,
          repo: '4321',
        });
      });

      test('_handleRepoCommandsRoute', () => {
        const data: PageContextWithQueryMap = {
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
          params: {0: '4321'},
        };
        assertDataToParams(data, '_handleRepoCommandsRoute', {
          view: GerritNav.View.REPO,
          detail: GerritNav.RepoDetailView.COMMANDS,
          repo: '4321',
        });
      });

      test('_handleRepoAccessRoute', () => {
        const data: PageContextWithQueryMap = {
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
          params: {0: '4321'},
        };
        assertDataToParams(data, '_handleRepoAccessRoute', {
          view: GerritNav.View.REPO,
          detail: GerritNav.RepoDetailView.ACCESS,
          repo: '4321',
        });
      });

      suite('branch list routes', () => {
        test('_handleBranchListOffsetRoute', () => {
          const data: any = {params: {0: 4321}};
          assertDataToParams(data, '_handleBranchListOffsetRoute', {
            view: GerritNav.View.REPO,
            detail: GerritNav.RepoDetailView.BRANCHES,
            repo: 4321,
            offset: 0,
            filter: null,
          });

          data.params[2] = 42;
          assertDataToParams(data, '_handleBranchListOffsetRoute', {
            view: GerritNav.View.REPO,
            detail: GerritNav.RepoDetailView.BRANCHES,
            repo: 4321,
            offset: 42,
            filter: null,
          });
        });

        test('_handleBranchListFilterOffsetRoute', () => {
          const data: PageContextWithQueryMap = {
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
            params: {repo: '4321', filter: 'foo', offset: '42'},
          };
          assertDataToParams(data, '_handleBranchListFilterOffsetRoute', {
            view: GerritNav.View.REPO,
            detail: GerritNav.RepoDetailView.BRANCHES,
            repo: '4321',
            offset: '42',
            filter: 'foo',
          });
        });

        test('_handleBranchListFilterRoute', () => {
          const data: PageContextWithQueryMap = {
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
            params: {repo: '4321', filter: 'foo'},
          };
          assertDataToParams(data, '_handleBranchListFilterRoute', {
            view: GerritNav.View.REPO,
            detail: GerritNav.RepoDetailView.BRANCHES,
            repo: '4321',
            filter: 'foo',
          });
        });
      });

      suite('tag list routes', () => {
        test('_handleTagListOffsetRoute', () => {
          const data: PageContextWithQueryMap = {
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
            params: {0: '4321'},
          };
          assertDataToParams(data, '_handleTagListOffsetRoute', {
            view: GerritNav.View.REPO,
            detail: GerritNav.RepoDetailView.TAGS,
            repo: '4321',
            offset: 0,
            filter: null,
          });
        });

        test('_handleTagListFilterOffsetRoute', () => {
          const data: PageContextWithQueryMap = {
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
            params: {repo: '4321', filter: 'foo', offset: '42'},
          };
          assertDataToParams(data, '_handleTagListFilterOffsetRoute', {
            view: GerritNav.View.REPO,
            detail: GerritNav.RepoDetailView.TAGS,
            repo: '4321',
            offset: '42',
            filter: 'foo',
          });
        });

        test('_handleTagListFilterRoute', () => {
          const data: any = {params: {repo: 4321}};
          assertDataToParams(data, '_handleTagListFilterRoute', {
            view: GerritNav.View.REPO,
            detail: GerritNav.RepoDetailView.TAGS,
            repo: 4321,
            filter: null,
          });

          data.params.filter = 'foo';
          assertDataToParams(data, '_handleTagListFilterRoute', {
            view: GerritNav.View.REPO,
            detail: GerritNav.RepoDetailView.TAGS,
            repo: 4321,
            filter: 'foo',
          });
        });
      });

      suite('repo list routes', () => {
        test('_handleRepoListOffsetRoute', () => {
          const data: any = {params: {}};
          assertDataToParams(data, '_handleRepoListOffsetRoute', {
            view: GerritNav.View.ADMIN,
            adminView: 'gr-repo-list',
            offset: 0,
            filter: null,
            openCreateModal: false,
          });

          data.params[1] = 42;
          assertDataToParams(data, '_handleRepoListOffsetRoute', {
            view: GerritNav.View.ADMIN,
            adminView: 'gr-repo-list',
            offset: 42,
            filter: null,
            openCreateModal: false,
          });

          data.hash = 'create';
          assertDataToParams(data, '_handleRepoListOffsetRoute', {
            view: GerritNav.View.ADMIN,
            adminView: 'gr-repo-list',
            offset: 42,
            filter: null,
            openCreateModal: true,
          });
        });

        test('_handleRepoListFilterOffsetRoute', () => {
          const data: any = {params: {filter: 'foo', offset: 42}};
          assertDataToParams(data, '_handleRepoListFilterOffsetRoute', {
            view: GerritNav.View.ADMIN,
            adminView: 'gr-repo-list',
            offset: 42,
            filter: 'foo',
          });
        });

        test('_handleRepoListFilterRoute', () => {
          const data: any = {params: {}};
          assertDataToParams(data, '_handleRepoListFilterRoute', {
            view: GerritNav.View.ADMIN,
            adminView: 'gr-repo-list',
            filter: null,
          });

          data.params.filter = 'foo';
          assertDataToParams(data, '_handleRepoListFilterRoute', {
            view: GerritNav.View.ADMIN,
            adminView: 'gr-repo-list',
            filter: 'foo',
          });
        });
      });
    });

    suite('plugin routes', () => {
      test('_handlePluginListOffsetRoute', () => {
        const data: any = {params: {}};
        assertDataToParams(data, '_handlePluginListOffsetRoute', {
          view: GerritNav.View.ADMIN,
          adminView: 'gr-plugin-list',
          offset: 0,
          filter: null,
        });

        data.params[1] = 42;
        assertDataToParams(data, '_handlePluginListOffsetRoute', {
          view: GerritNav.View.ADMIN,
          adminView: 'gr-plugin-list',
          offset: 42,
          filter: null,
        });
      });

      test('_handlePluginListFilterOffsetRoute', () => {
        const data: any = {params: {filter: 'foo', offset: 42}};
        assertDataToParams(data, '_handlePluginListFilterOffsetRoute', {
          view: GerritNav.View.ADMIN,
          adminView: 'gr-plugin-list',
          offset: 42,
          filter: 'foo',
        });
      });

      test('_handlePluginListFilterRoute', () => {
        const data: any = {params: {}};
        assertDataToParams(data, '_handlePluginListFilterRoute', {
          view: GerritNav.View.ADMIN,
          adminView: 'gr-plugin-list',
          filter: null,
        });

        data.params.filter = 'foo';
        assertDataToParams(data, '_handlePluginListFilterRoute', {
          view: GerritNav.View.ADMIN,
          adminView: 'gr-plugin-list',
          filter: 'foo',
        });
      });

      test('_handlePluginListRoute', () => {
        const data: any = {params: {}};
        assertDataToParams(data, '_handlePluginListRoute', {
          view: GerritNav.View.ADMIN,
          adminView: 'gr-plugin-list',
        });
      });
    });

    suite('change/diff routes', () => {
      test('_handleChangeNumberLegacyRoute', () => {
        const data: any = {params: {0: 12345}};
        element._handleChangeNumberLegacyRoute(data);
        assert.isTrue(redirectStub.calledOnce);
        assert.isTrue(redirectStub.calledWithExactly('/c/12345'));
      });

      test('_handleChangeLegacyRoute', async () => {
        stubRestApi('getFromProjectLookup').returns(
          Promise.resolve('project' as RepoName)
        );
        const ctx: PageContextWithQueryMap = {
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
          params: {0: '1234', 1: 'comment/6789'},
        };
        element._handleChangeLegacyRoute(ctx);
        await flush();
        assert.isTrue(
          redirectStub.calledWithExactly('/c/project/+/1234' + '/comment/6789')
        );
      });

      test('_handleLegacyLinenum w/ @321', () => {
        const ctx: PageContextWithQueryMap = {
          queryMap: new Map(),
          save() {},
          handled: true,
          canonicalPath: '',
          path: '/c/1234/3..8/foo/bar@321',
          querystring: '',
          pathname: '',
          state: '',
          title: '',
          hash: '',
          params: {},
        };
        element._handleLegacyLinenum(ctx);
        assert.isTrue(redirectStub.calledOnce);
        assert.isTrue(
          redirectStub.calledWithExactly('/c/1234/3..8/foo/bar#321')
        );
      });

      test('_handleLegacyLinenum w/ @b123', () => {
        const ctx: PageContextWithQueryMap = {
          queryMap: new Map(),
          save() {},
          handled: true,
          canonicalPath: '',
          path: '/c/1234/3..8/foo/bar@b123',
          querystring: '',
          pathname: '',
          state: '',
          title: '',
          hash: '',
          params: {},
        };
        element._handleLegacyLinenum(ctx);
        assert.isTrue(redirectStub.calledOnce);
        assert.isTrue(
          redirectStub.calledWithExactly('/c/1234/3..8/foo/bar#b123')
        );
      });

      suite('_handleChangeRoute', () => {
        let normalizeRangeStub: sinon.SinonStub;

        function makeParams(_path: any, _hash: any): PageContextWithQueryMap {
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
            params: {
              0: 'foo/bar',
              1: '1234',
              2: '',
              3: '',
              4: '4',
              5: '',
              6: '7',
            },
          };
        }

        setup(() => {
          normalizeRangeStub = sinon.stub(
            element,
            '_normalizePatchRangeParams'
          );
          stubRestApi('setInProjectLookup');
        });

        test('needs redirect', () => {
          normalizeRangeStub.returns(true);
          sinon.stub(element, '_generateUrl').returns('foo');
          const ctx = makeParams(null, '');
          element._handleChangeRoute(ctx);
          assert.isTrue(normalizeRangeStub.called);
          assert.isFalse(setParamsStub.called);
          assert.isTrue(redirectStub.calledOnce);
          assert.isTrue(redirectStub.calledWithExactly('foo'));
        });

        test('change view', () => {
          normalizeRangeStub.returns(false);
          sinon.stub(element, '_generateUrl').returns('foo');
          const ctx = makeParams(null, '');
          assertDataToParams(ctx, '_handleChangeRoute', {
            view: GerritView.CHANGE,
            project: 'foo/bar',
            changeNum: 1234,
            basePatchNum: 4,
            patchNum: 7,
          });
          assert.isFalse(redirectStub.called);
          assert.isTrue(normalizeRangeStub.called);
        });

        test('params', () => {
          normalizeRangeStub.returns(false);
          sinon.stub(element, '_generateUrl').returns('foo');
          const ctx = makeParams(null, '');
          ctx.queryMap.set('tab', 'checks');
          ctx.queryMap.set('filter', 'fff');
          ctx.queryMap.set('select', 'sss');
          ctx.queryMap.set('attempt', '1');
          assertDataToParams(ctx, '_handleChangeRoute', {
            view: GerritView.CHANGE,
            project: 'foo/bar',
            changeNum: 1234,
            basePatchNum: 4,
            patchNum: 7,
            attempt: 1,
            filter: 'fff',
            select: 'sss',
            tab: 'checks',
          });
        });
      });

      suite('_handleDiffRoute', () => {
        let normalizeRangeStub: sinon.SinonStub;

        function makeParams(path: any, hash: any): any {
          return {
            params: [
              'foo/bar', // 0 Project
              1234, // 1 Change number
              null, // 2 Unused
              null, // 3 Unused
              4, // 4 Base patch number
              null, // 5 Unused
              7, // 6 Patch number
              null, // 7 Unused,
              path, // 8 Diff path
            ],
            hash,
          };
        }

        setup(() => {
          normalizeRangeStub = sinon.stub(
            element,
            '_normalizePatchRangeParams'
          );
          stubRestApi('setInProjectLookup');
        });

        test('needs redirect', () => {
          normalizeRangeStub.returns(true);
          sinon.stub(element, '_generateUrl').returns('foo');
          const ctx = makeParams(null, '');
          element._handleDiffRoute(ctx);
          assert.isTrue(normalizeRangeStub.called);
          assert.isFalse(setParamsStub.called);
          assert.isTrue(redirectStub.calledOnce);
          assert.isTrue(redirectStub.calledWithExactly('foo'));
        });

        test('diff view', () => {
          normalizeRangeStub.returns(false);
          sinon.stub(element, '_generateUrl').returns('foo');
          const ctx = makeParams('foo/bar/baz', 'b44');
          assertDataToParams(ctx, '_handleDiffRoute', {
            view: GerritView.DIFF,
            project: 'foo/bar',
            changeNum: 1234,
            basePatchNum: 4,
            patchNum: 7,
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
            '_handleCommentRoute',
            {
              project: 'gerrit',
              changeNum: 264833,
              commentId: '00049681_f34fd6a9',
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
            '_handleCommentsRoute',
            {
              project: 'gerrit',
              changeNum: 264833,
              commentId: '00049681_f34fd6a9',
              view: GerritView.CHANGE,
            }
          );
        });
      });

      test('_handleDiffEditRoute', () => {
        const normalizeRangeSpy = sinon.spy(
          element,
          '_normalizePatchRangeParams'
        );
        stubRestApi('setInProjectLookup');
        const ctx: any = {
          params: [
            'foo/bar', // 0 Project
            1234, // 1 Change number
            3, // 2 Patch num
            'foo/bar/baz', // 3 File path
          ],
        };
        const appParams: any = {
          project: 'foo/bar',
          changeNum: 1234,
          view: GerritNav.View.EDIT,
          path: 'foo/bar/baz',
          patchNum: 3,
          lineNum: undefined,
        };

        element._handleDiffEditRoute(ctx);
        assert.isFalse(redirectStub.called);
        assert.isTrue(normalizeRangeSpy.calledOnce);
        assert.deepEqual(normalizeRangeSpy.lastCall.args[0], appParams);
        assert.isFalse(normalizeRangeSpy.lastCall.returnValue);
        assert.deepEqual(setParamsStub.lastCall.args[0], appParams);
      });

      test('_handleDiffEditRoute with lineNum', () => {
        const normalizeRangeSpy = sinon.spy(
          element,
          '_normalizePatchRangeParams'
        );
        stubRestApi('setInProjectLookup');
        const ctx: any = {
          params: [
            'foo/bar', // 0 Project
            1234, // 1 Change number
            3, // 2 Patch num
            'foo/bar/baz', // 3 File path
          ],
          hash: 4,
        };
        const appParams: any = {
          project: 'foo/bar',
          changeNum: 1234,
          view: GerritNav.View.EDIT,
          path: 'foo/bar/baz',
          patchNum: 3,
          lineNum: 4,
        };

        element._handleDiffEditRoute(ctx);
        assert.isFalse(redirectStub.called);
        assert.isTrue(normalizeRangeSpy.calledOnce);
        assert.deepEqual(normalizeRangeSpy.lastCall.args[0], appParams);
        assert.isFalse(normalizeRangeSpy.lastCall.returnValue);
        assert.deepEqual(setParamsStub.lastCall.args[0], appParams);
      });

      test('_handleChangeEditRoute', () => {
        const normalizeRangeSpy = sinon.spy(
          element,
          '_normalizePatchRangeParams'
        );
        stubRestApi('setInProjectLookup');
        const ctx: any = {
          params: [
            'foo/bar', // 0 Project
            1234, // 1 Change number
            null,
            3, // 3 Patch num
          ],
          queryMap: new Map(),
        };
        const appParams: any = {
          project: 'foo/bar',
          changeNum: 1234,
          view: GerritView.CHANGE,
          patchNum: 3,
          edit: true,
          tab: '',
        };

        element._handleChangeEditRoute(ctx);
        assert.isFalse(redirectStub.called);
        assert.isTrue(normalizeRangeSpy.calledOnce);
        assert.deepEqual(normalizeRangeSpy.lastCall.args[0], appParams);
        assert.isFalse(normalizeRangeSpy.lastCall.returnValue);
        assert.deepEqual(setParamsStub.lastCall.args[0], appParams);
      });
    });

    test('_handlePluginScreen', () => {
      const ctx: any = {params: ['foo', 'bar']};
      assertDataToParams(ctx, '_handlePluginScreen', {
        view: GerritNav.View.PLUGIN_SCREEN,
        plugin: 'foo',
        screen: 'bar',
      });
      assert.isFalse(redirectStub.called);
    });
  });

  suite('_parseQueryString', () => {
    test('empty queries', () => {
      assert.deepEqual(element._parseQueryString(''), []);
      assert.deepEqual(element._parseQueryString('?'), []);
      assert.deepEqual(element._parseQueryString('??'), []);
      assert.deepEqual(element._parseQueryString('&&&'), []);
    });

    test('url decoding', () => {
      assert.deepEqual(element._parseQueryString('+'), [[' ', '']]);
      assert.deepEqual(element._parseQueryString('???+%3d+'), [[' = ', '']]);
      assert.deepEqual(
        element._parseQueryString('%6e%61%6d%65=%76%61%6c%75%65'),
        [['name', 'value']]
      );
    });

    test('multiple parameters', () => {
      assert.deepEqual(element._parseQueryString('a=b&c=d&e=f'), [
        ['a', 'b'],
        ['c', 'd'],
        ['e', 'f'],
      ]);
      assert.deepEqual(element._parseQueryString('&a=b&&&e=f&c'), [
        ['a', 'b'],
        ['e', 'f'],
        ['c', ''],
      ]);
    });
  });
});
