/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import {createChange} from '../../../test/test-data-generators';
import {BasePatchSetNum, NumericChangeId} from '../../../types/common';
import {GerritNav} from './gr-navigation';

suite('gr-navigation tests', () => {
  test('invalid patch ranges throw exceptions', () => {
    assert.throw(() =>
      GerritNav.getUrlForChange(
        {...createChange(), _number: 123 as NumericChangeId},
        {basePatchNum: 12 as BasePatchSetNum}
      )
    );
    assert.throw(() =>
      GerritNav.getUrlForDiff(
        {...createChange(), _number: 123 as NumericChangeId},
        'x.c',
        undefined,
        12 as BasePatchSetNum
      )
    );
  });

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
