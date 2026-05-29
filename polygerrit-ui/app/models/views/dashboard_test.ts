/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {EmailAddress, RepoName} from '../../api/rest-api';
import {GerritView} from '../../services/router/router-model';
import '../../test/common-test-setup';
import {assertRouteFalse, assertRouteState} from '../../test/test-utils';
import {DashboardId} from '../../types/common';
import {
  createDashboardUrl,
  DashboardType,
  DashboardViewState,
  PROJECT_DASHBOARD_ROUTE,
} from './dashboard';

suite('dashboard view state tests', () => {
  suite('routes', () => {
    test('PROJECT_DASHBOARD_ROUTE', () => {
      assertRouteFalse(PROJECT_DASHBOARD_ROUTE, '/p//+/dashboard/qwer');
      assertRouteFalse(PROJECT_DASHBOARD_ROUTE, '/p/asdf/+/dashboard/');

      const state: DashboardViewState = {
        view: GerritView.DASHBOARD,
        type: DashboardType.REPO,
        project: 'asdf' as RepoName,
        dashboard: 'qwer' as DashboardId,
      };
      assertRouteState(
        PROJECT_DASHBOARD_ROUTE,
        '/p/asdf/+/dashboard/qwer',
        state,
        createDashboardUrl
      );
    });
  });

  suite('createDashboardUrl()', () => {
    test('self dashboard', () => {
      assert.equal(
        createDashboardUrl({type: DashboardType.USER}),
        '/dashboard/self'
      );
    });

    test('baseUrl', () => {
      window.CANONICAL_PATH = '/base';
      assert.equal(
        createDashboardUrl({type: DashboardType.USER}).substring(0, 5),
        '/base'
      );
      window.CANONICAL_PATH = undefined;
    });

    test('user dashboard', () => {
      assert.equal(
        createDashboardUrl({
          type: DashboardType.USER,
          user: 'user@email.com' as EmailAddress,
        }),
        '/dashboard/user@email.com'
      );
    });

    test('custom self dashboard, no title', () => {
      const state = {
        type: DashboardType.CUSTOM,
        sections: [
          {name: 'section 1', query: 'query 1'},
          {name: 'section 2', query: 'query 2'},
        ],
      };
      assert.equal(
        createDashboardUrl(state),
        '/dashboard/?section+1=query+1&section+2=query+2'
      );
    });

    test('custom repo dashboard', () => {
      const state = {
        type: DashboardType.CUSTOM,
        sections: [
          {name: 'section 1', query: 'query 1 ${project}'},
          {name: 'section 2', query: 'query 2 ${repo}'},
        ],
        project: 'repo-name' as RepoName,
      };
      assert.equal(
        createDashboardUrl(state),
        '/dashboard/?section+1=query+1+repo-name&' +
          'section+2=query+2+repo-name'
      );
    });

    test('custom user dashboard, with title', () => {
      const state = {
        type: DashboardType.CUSTOM,
        user: 'user@email.com' as EmailAddress,
        sections: [{name: 'name', query: 'query'}],
        title: 'custom dashboard',
      };
      assert.equal(
        createDashboardUrl(state),
        '/dashboard/user@email.com?name=query&title=custom+dashboard'
      );
    });

    test('repo dashboard', () => {
      const state = {
        type: DashboardType.REPO,
        project: 'gerrit/repo' as RepoName,
        dashboard: 'default:main' as DashboardId,
      };
      assert.equal(
        createDashboardUrl(state),
        '/p/gerrit/repo/+/dashboard/default:main'
      );
    });

    test('project dashboard (legacy)', () => {
      const state = {
        type: DashboardType.REPO,
        project: 'gerrit/project' as RepoName,
        dashboard: 'default:main' as DashboardId,
      };
      assert.equal(
        createDashboardUrl(state),
        '/p/gerrit/project/+/dashboard/default:main'
      );
    });
  });
});
