/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import '../../../test/common-test-setup';
import {GerritNav} from './gr-navigation';

suite('gr-navigation tests', () => {
  suite('_getUserDashboard', () => {
    const sections = [
      {name: 'section 1', query: 'query 1'},
      {name: 'section 2', query: 'query 2 for ${user}'},
      {name: 'section 3', query: 'self only query', selfOnly: true},
      {name: 'section 4', query: 'query 4', suffixForDashboard: 'suffix'},
    ];

    test('dashboard for self', () => {
      const dashboard = GerritNav.getUserDashboard('self', sections, 'title');
      assert.deepEqual(dashboard, {
        title: 'title',
        sections: [
          {name: 'section 1', query: 'query 1'},
          {name: 'section 2', query: 'query 2 for self'},
          {
            name: 'section 3',
            query: 'self only query',
            selfOnly: true,
          },
          {
            name: 'section 4',
            query: 'query 4',
            suffixForDashboard: 'suffix',
          },
        ],
      });
    });

    test('dashboard for other user', () => {
      const dashboard = GerritNav.getUserDashboard('user', sections, 'title');
      assert.deepEqual(dashboard, {
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
