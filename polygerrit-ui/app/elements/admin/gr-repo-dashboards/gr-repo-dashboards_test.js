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

import '../../../test/common-test-setup-karma.js';
import './gr-repo-dashboards.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {addListenerForTest, stubRestApi} from '../../../test/test-utils.js';

const basicFixture = fixtureFromElement('gr-repo-dashboards');

suite('gr-repo-dashboards tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
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
          ]));
    });

    test('loading, sections, and ordering', done => {
      assert.isTrue(element._loading);
      assert.notEqual(getComputedStyle(element.$.loadingContainer).display,
          'none');
      assert.equal(getComputedStyle(element.$.dashboards).display,
          'none');
      element.repo = 'test';
      flush(() => {
        assert.equal(getComputedStyle(element.$.loadingContainer).display,
            'none');
        assert.notEqual(getComputedStyle(element.$.dashboards).display,
            'none');

        assert.equal(element._dashboards.length, 2);
        assert.equal(element._dashboards[0].section, 'custom');
        assert.equal(element._dashboards[1].section, 'default');

        const dashboards = element._dashboards[0].dashboards;
        assert.equal(dashboards.length, 2);
        assert.equal(dashboards[0].id, 'custom:custom1');
        assert.equal(dashboards[1].id, 'custom:custom2');

        done();
      });
    });
  });

  suite('test url', () => {
    test('_getUrl', () => {
      sinon.stub(GerritNav, 'getUrlForRepoDashboard').callsFake(
          () => '/r/dashboard/test');

      assert.equal(element._getUrl('/dashboard/test', {}), '/r/dashboard/test');

      assert.equal(element._getUrl(undefined, undefined), '');
    });
  });

  suite('404', () => {
    test('fires page-error', done => {
      const response = {status: 404};
      stubRestApi('getRepoDashboards').callsFake((repo, errFn) => {
        errFn(response);
      });

      addListenerForTest(document, 'page-error', e => {
        assert.deepEqual(e.detail.response, response);
        done();
      });

      element.repo = 'test';
    });
  });
});

