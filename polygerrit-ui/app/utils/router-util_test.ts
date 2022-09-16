/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {
  RepoName,
  NumericChangeId,
  RevisionPatchSetNum,
  BasePatchSetNum,
  EDIT,
} from '../api/rest-api';
import {PatchRangeParams} from '../elements/core/gr-router/gr-router';
import {ChangeViewState} from '../models/views/change';
import {DashboardViewState} from '../models/views/dashboard';
import {EditViewState} from '../models/views/edit';
import {GerritView} from '../services/router/router-model';
import '../test/common-test-setup';
import {DashboardId} from '../types/common';
import {generateUrl, TEST_ONLY} from './router-util';

suite('router-util tests', () => {
  suite('generateUrl', () => {
    test('change', () => {
      const params: ChangeViewState = {
        view: GerritView.CHANGE,
        changeNum: 1234 as NumericChangeId,
        project: 'test' as RepoName,
      };

      assert.equal(generateUrl(params), '/c/test/+/1234');

      params.patchNum = 10 as RevisionPatchSetNum;
      assert.equal(generateUrl(params), '/c/test/+/1234/10');

      params.basePatchNum = 5 as BasePatchSetNum;
      assert.equal(generateUrl(params), '/c/test/+/1234/5..10');

      params.messageHash = '#123';
      assert.equal(generateUrl(params), '/c/test/+/1234/5..10#123');
    });

    test('change with repo name encoding', () => {
      const params: ChangeViewState = {
        view: GerritView.CHANGE,
        changeNum: 1234 as NumericChangeId,
        project: 'x+/y+/z+/w' as RepoName,
      };
      assert.equal(generateUrl(params), '/c/x%252B/y%252B/z%252B/w/+/1234');
    });

    test(EDIT, () => {
      const params: EditViewState = {
        view: GerritView.EDIT,
        changeNum: 42 as NumericChangeId,
        project: 'test' as RepoName,
        path: 'x+y/path.cpp',
        patchNum: EDIT as RevisionPatchSetNum,
      };
      assert.equal(
        generateUrl(params),
        '/c/test/+/42/edit/x%252By/path.cpp,edit'
      );
    });

    test('getPatchRangeExpression', () => {
      const params: PatchRangeParams = {};
      let actual = TEST_ONLY.getPatchRangeExpression(params);
      assert.equal(actual, '');

      params.patchNum = 4 as RevisionPatchSetNum;
      actual = TEST_ONLY.getPatchRangeExpression(params);
      assert.equal(actual, '4');

      params.basePatchNum = 2 as BasePatchSetNum;
      actual = TEST_ONLY.getPatchRangeExpression(params);
      assert.equal(actual, '2..4');

      delete params.patchNum;
      actual = TEST_ONLY.getPatchRangeExpression(params);
      assert.equal(actual, '2..');
    });

    suite('dashboard', () => {
      test('self dashboard', () => {
        const params: DashboardViewState = {
          view: GerritView.DASHBOARD,
        };
        assert.equal(generateUrl(params), '/dashboard/self');
      });

      test('user dashboard', () => {
        const params: DashboardViewState = {
          view: GerritView.DASHBOARD,
          user: 'user',
        };
        assert.equal(generateUrl(params), '/dashboard/user');
      });

      test('custom self dashboard, no title', () => {
        const params: DashboardViewState = {
          view: GerritView.DASHBOARD,
          sections: [
            {name: 'section 1', query: 'query 1'},
            {name: 'section 2', query: 'query 2'},
          ],
        };
        assert.equal(
          generateUrl(params),
          '/dashboard/?section%201=query%201&section%202=query%202'
        );
      });

      test('custom repo dashboard', () => {
        const params: DashboardViewState = {
          view: GerritView.DASHBOARD,
          sections: [
            {name: 'section 1', query: 'query 1 ${project}'},
            {name: 'section 2', query: 'query 2 ${repo}'},
          ],
          project: 'repo-name' as RepoName,
        };
        assert.equal(
          generateUrl(params),
          '/dashboard/?section%201=query%201%20repo-name&' +
            'section%202=query%202%20repo-name'
        );
      });

      test('custom user dashboard, with title', () => {
        const params: DashboardViewState = {
          view: GerritView.DASHBOARD,
          user: 'user',
          sections: [{name: 'name', query: 'query'}],
          title: 'custom dashboard',
        };
        assert.equal(
          generateUrl(params),
          '/dashboard/user?name=query&title=custom%20dashboard'
        );
      });

      test('repo dashboard', () => {
        const params: DashboardViewState = {
          view: GerritView.DASHBOARD,
          project: 'gerrit/repo' as RepoName,
          dashboard: 'default:main' as DashboardId,
        };
        assert.equal(
          generateUrl(params),
          '/p/gerrit/repo/+/dashboard/default:main'
        );
      });

      test('project dashboard (legacy)', () => {
        const params: DashboardViewState = {
          view: GerritView.DASHBOARD,
          project: 'gerrit/project' as RepoName,
          dashboard: 'default:main' as DashboardId,
        };
        assert.equal(
          generateUrl(params),
          '/p/gerrit/project/+/dashboard/default:main'
        );
      });
    });
  });
});
