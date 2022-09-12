/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {
  RepoName,
  BranchName,
  TopicName,
  NumericChangeId,
  RevisionPatchSetNum,
  BasePatchSetNum,
  EDIT,
  GroupId,
} from '../api/rest-api';
import {PatchRangeParams} from '../elements/core/gr-router/gr-router';
import {GerritView} from '../services/router/router-model';
import '../test/common-test-setup-karma';
import {DashboardId} from '../types/common';
import {
  generateUrl,
  GenerateUrlChangeViewParameters,
  GenerateUrlDashboardViewParameters,
  GenerateUrlDiffViewParameters,
  GenerateUrlEditViewParameters,
  GenerateUrlGroupViewParameters,
  GenerateUrlSearchViewParameters,
  GroupChildPage,
  TEST_ONLY,
} from './router-util';

suite('router-util tests', () => {
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
        generateUrl(params),
        '/q/owner:a%2525b+project:c%2525d+branch:e%2525f+' +
          'topic:g%2525h+status:op%2525en'
      );

      params.offset = 100;
      assert.equal(
        generateUrl(params),
        '/q/owner:a%2525b+project:c%2525d+branch:e%2525f+' +
          'topic:g%2525h+status:op%2525en,100'
      );
      delete params.offset;

      // The presence of the query param overrides other params.
      params.query = 'foo$bar';
      assert.equal(generateUrl(params), '/q/foo%2524bar');

      params.offset = 100;
      assert.equal(generateUrl(params), '/q/foo%2524bar,100');

      params = {
        view: GerritView.SEARCH,
        statuses: ['a', 'b', 'c'],
      };
      assert.equal(
        generateUrl(params),
        '/q/(status:a OR status:b OR status:c)'
      );

      params = {
        view: GerritView.SEARCH,
        topic: 'test' as TopicName,
      };
      assert.equal(generateUrl(params), '/q/topic:test');
      params = {
        view: GerritView.SEARCH,
        topic: 'test test' as TopicName,
      };
      assert.equal(generateUrl(params), '/q/topic:"test+test"');
      params = {
        view: GerritView.SEARCH,
        topic: 'test:test' as TopicName,
      };
      assert.equal(generateUrl(params), '/q/topic:"test:test"');
    });

    test('change', () => {
      const params: GenerateUrlChangeViewParameters = {
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
      const params: GenerateUrlChangeViewParameters = {
        view: GerritView.CHANGE,
        changeNum: 1234 as NumericChangeId,
        project: 'x+/y+/z+/w' as RepoName,
      };
      assert.equal(generateUrl(params), '/c/x%252B/y%252B/z%252B/w/+/1234');
    });

    test('diff', () => {
      const params: GenerateUrlDiffViewParameters = {
        view: GerritView.DIFF,
        changeNum: 42 as NumericChangeId,
        path: 'x+y/path.cpp' as RepoName,
        patchNum: 12 as RevisionPatchSetNum,
        project: '' as RepoName,
      };
      assert.equal(generateUrl(params), '/c/42/12/x%252By/path.cpp');

      params.project = 'test' as RepoName;
      assert.equal(generateUrl(params), '/c/test/+/42/12/x%252By/path.cpp');

      params.basePatchNum = 6 as BasePatchSetNum;
      assert.equal(generateUrl(params), '/c/test/+/42/6..12/x%252By/path.cpp');

      params.path = 'foo bar/my+file.txt%';
      params.patchNum = 2 as RevisionPatchSetNum;
      delete params.basePatchNum;
      assert.equal(
        generateUrl(params),
        '/c/test/+/42/2/foo+bar/my%252Bfile.txt%2525'
      );

      params.path = 'file.cpp';
      params.lineNum = 123;
      assert.equal(generateUrl(params), '/c/test/+/42/2/file.cpp#123');

      params.leftSide = true;
      assert.equal(generateUrl(params), '/c/test/+/42/2/file.cpp#b123');
    });

    test('diff with repo name encoding', () => {
      const params: GenerateUrlDiffViewParameters = {
        view: GerritView.DIFF,
        changeNum: 42 as NumericChangeId,
        path: 'x+y/path.cpp',
        patchNum: 12 as RevisionPatchSetNum,
        project: 'x+/y' as RepoName,
      };
      assert.equal(generateUrl(params), '/c/x%252B/y/+/42/12/x%252By/path.cpp');
    });

    test(EDIT, () => {
      const params: GenerateUrlEditViewParameters = {
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
        const params: GenerateUrlDashboardViewParameters = {
          view: GerritView.DASHBOARD,
        };
        assert.equal(generateUrl(params), '/dashboard/self');
      });

      test('user dashboard', () => {
        const params: GenerateUrlDashboardViewParameters = {
          view: GerritView.DASHBOARD,
          user: 'user',
        };
        assert.equal(generateUrl(params), '/dashboard/user');
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
          generateUrl(params),
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
          generateUrl(params),
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
          generateUrl(params),
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
          generateUrl(params),
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
          generateUrl(params),
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
        assert.equal(generateUrl(params), '/admin/groups/1234');
      });

      test('group members', () => {
        const params: GenerateUrlGroupViewParameters = {
          view: GerritView.GROUP,
          groupId: '1234' as GroupId,
          detail: 'members' as GroupChildPage,
        };
        assert.equal(generateUrl(params), '/admin/groups/1234,members');
      });

      test('group audit log', () => {
        const params: GenerateUrlGroupViewParameters = {
          view: GerritView.GROUP,
          groupId: '1234' as GroupId,
          detail: 'log' as GroupChildPage,
        };
        assert.equal(generateUrl(params), '/admin/groups/1234,audit-log');
      });
    });
  });
});
