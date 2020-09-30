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
import {GerritNav} from './gr-navigation.js';

suite('gr-navigation tests', () => {
  test('invalid patch ranges throw exceptions', () => {
    assert.throw(() => { return GerritNav.getUrlForChange('123', undefined, 12); });
    assert.throw(() => { return GerritNav.getUrlForDiff('123', 'x.c', undefined, 12); });
  });

  suite('_getUserDashboard', () => {
    const sections = [
      {name: 'section 1', query: 'query 1'},
      {name: 'section 2', query: 'query 2 for ${user}'},
      {name: 'section 3', query: 'self only query', selfOnly: true},
      {name: 'section 4', query: 'query 4', suffixForDashboard: 'suffix'},
    ];

    test('dashboard for self', () => {
      const dashboard =
           GerritNav.getUserDashboard('self', sections, 'title');
      assert.deepEqual(
          dashboard,
          {
            title: 'title',
            sections: [
              {name: 'section 1', query: 'query 1'},
              {name: 'section 2', query: 'query 2 for self'},
              {
                name: 'section 3',
                query: 'self only query',
                selfOnly: true,
              }, {
                name: 'section 4',
                query: 'query 4',
                suffixForDashboard: 'suffix',
              },
            ],
          });
    });

    test('dashboard for other user', () => {
      const dashboard =
           GerritNav.getUserDashboard('user', sections, 'title');
      assert.deepEqual(
          dashboard,
          {
            title: 'title',
            sections: [
              {name: 'section 1', query: 'query 1'},
              {name: 'section 2', query: 'query 2 for user'},
              {
                name: 'section 4',
                query: 'query 4',
                suffixForDashboard: 'suffix',
              },
            ],
          });
    });
  });
});

