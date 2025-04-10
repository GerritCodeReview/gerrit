/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-repo-dashboards';
import {GrRepoDashboards} from './gr-repo-dashboards';
import {
  addListenerForTest,
  mockPromise,
  query,
  stubRestApi,
  waitEventLoop,
} from '../../../test/test-utils';
import {DashboardInfo, RepoName} from '../../../types/common';
import {PageErrorEvent} from '../../../types/events';
import {fixture, html, assert} from '@open-wc/testing';

suite('gr-repo-dashboards tests', () => {
  let element: GrRepoDashboards;

  setup(async () => {
    element = await fixture(html`<gr-repo-dashboards></gr-repo-dashboards>`);
  });

  suite('dashboard table', () => {
    setup(() => {
      stubRestApi('getRepoDashboards').returns(
        Promise.resolve([
          {
            id: 'default:contributor',
            project: 'gerrit',
            defining_project: 'gerrit',
            ref: 'default',
            path: 'contributor',
            description: 'Own contributions.',
            foreach: 'owner:self',
            url: '/dashboard/?params',
            title: 'Contributor Dashboard',
            sections: [
              {
                name: 'Mine To Rebase',
                query: 'is:open -is:mergeable',
              },
              {
                name: 'My Recently Merged',
                query: 'is:merged limit:10',
              },
            ],
          },
          {
            id: 'custom:custom2',
            project: 'gerrit',
            defining_project: 'Public-Projects',
            ref: 'custom',
            path: 'open',
            description: 'Recent open changes.',
            url: '/dashboard/?params',
            title: 'Open Changes',
            sections: [
              {
                name: 'Open Changes',
                query: 'status:open project:${project} -age:7w',
              },
            ],
          },
          {
            id: 'default:abc',
            project: 'gerrit',
            ref: 'default',
          },
          {
            id: 'custom:custom1',
            project: 'gerrit',
            ref: 'custom',
          },
        ] as DashboardInfo[])
      );
    });

    test('render', () => {
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <table class="genericList" id="list">
            <tbody id="dashboards">
              <tr class="headerRow">
                <th class="topHeader">Dashboard name</th>
                <th class="topHeader">Dashboard title</th>
                <th class="topHeader">Dashboard description</th>
                <th class="topHeader">Inherited from</th>
                <th class="topHeader">Default</th>
              </tr>
              <tr id="loadingContainer">
                <td>Loading...</td>
              </tr>
            </tbody>
          </table>
        `
      );
    });

    test('loading, sections, and ordering', async () => {
      assert.isTrue(element.loading);
      assert.isOk(query(element, '#loadingContainer'));

      element.repo = 'test' as RepoName;
      await waitEventLoop();
      assert.isNotOk(query(element, '#loadingContainer'));

      const dashboard = element.dashboards!;
      assert.equal(dashboard.length, 2);
      assert.equal(dashboard[0].section, 'custom');
      assert.equal(dashboard[1].section, 'default');

      const dashboards = dashboard[0].dashboards;
      assert.equal(dashboards.length, 2);
      assert.equal(dashboards[0].id, 'custom:custom1');
      assert.equal(dashboards[1].id, 'custom:custom2');
    });
  });

  suite('404', () => {
    test('fires page-error', async () => {
      const response = {status: 404} as Response;
      stubRestApi('getRepoDashboards').callsFake((_repo, errFn) => {
        // eslint-disable-next-line @typescript-eslint/no-unnecessary-type-assertion
        errFn!(response);
        return Promise.resolve([]);
      });

      const promise = mockPromise();
      addListenerForTest(document, 'page-error', e => {
        assert.deepEqual((e as PageErrorEvent).detail.response, response);
        promise.resolve();
      });

      element.repo = 'test' as RepoName;
      await promise;
    });
  });
});
