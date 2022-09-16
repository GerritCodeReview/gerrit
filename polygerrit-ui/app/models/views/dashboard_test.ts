/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {RepoName} from '../../api/rest-api';
import '../../test/common-test-setup';
import {DashboardId} from '../../types/common';
import {createDashboardUrl} from './dashboard';

suite('dashboard view state tests', () => {
  suite('createDashboardUrl()', () => {
    test('self dashboard', () => {
      assert.equal(createDashboardUrl({}), '/dashboard/self');
    });

    test('user dashboard', () => {
      assert.equal(createDashboardUrl({user: 'user'}), '/dashboard/user');
    });

    test('custom self dashboard, no title', () => {
      const state = {
        sections: [
          {name: 'section 1', query: 'query 1'},
          {name: 'section 2', query: 'query 2'},
        ],
      };
      assert.equal(
        createDashboardUrl(state),
        '/dashboard/?section%201=query%201&section%202=query%202'
      );
    });

    test('custom repo dashboard', () => {
      const state = {
        sections: [
          {name: 'section 1', query: 'query 1 ${project}'},
          {name: 'section 2', query: 'query 2 ${repo}'},
        ],
        project: 'repo-name' as RepoName,
      };
      assert.equal(
        createDashboardUrl(state),
        '/dashboard/?section%201=query%201%20repo-name&' +
          'section%202=query%202%20repo-name'
      );
    });

    test('custom user dashboard, with title', () => {
      const state = {
        user: 'user',
        sections: [{name: 'name', query: 'query'}],
        title: 'custom dashboard',
      };
      assert.equal(
        createDashboardUrl(state),
        '/dashboard/user?name=query&title=custom%20dashboard'
      );
    });

    test('repo dashboard', () => {
      const state = {
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
