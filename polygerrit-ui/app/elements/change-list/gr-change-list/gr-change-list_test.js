/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import './gr-change-list.js';
import {afterNextRender} from '@polymer/polymer/lib/utils/render-status.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {mockPromise} from '../../../test/test-utils.js';
import {YOUR_TURN} from '../../core/gr-navigation/gr-navigation.js';

const basicFixture = fixtureFromElement('gr-change-list');

suite('gr-change-list basic tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
  });

  suite('test show change number not logged in', () => {
    setup(() => {
      element = basicFixture.instantiate();
      element.account = undefined;
      element.preferences = undefined;
      element._config = {};
    });

    test('show number disabled', () => {
      assert.isFalse(element.showNumber);
    });
  });

  suite('test show change number preference enabled', () => {
    setup(() => {
      element = basicFixture.instantiate();
      element.preferences = {
        legacycid_in_change_table: true,
        time_format: 'HHMM_12',
        change_table: [],
      };
      element.account = {_account_id: 1001};
      element._config = {};
      flush();
    });

    test('show number enabled', () => {
      assert.isTrue(element.showNumber);
    });
  });

  suite('test show change number preference disabled', () => {
    setup(() => {
      element = basicFixture.instantiate();
      // legacycid_in_change_table is not set when false.
      element.preferences = {
        time_format: 'HHMM_12',
        change_table: [],
      };
      element.account = {_account_id: 1001};
      element._config = {};
      flush();
    });

    test('show number disabled', () => {
      assert.isFalse(element.showNumber);
    });
  });

  test('computed fields', () => {
    assert.equal(element._computeLabelNames(
        [{results: [{_number: 0, labels: {}}]}]).length, 0);
    assert.equal(element._computeLabelNames([
      {results: [
        {_number: 0, labels: {Verified: {approved: {}}}},
        {
          _number: 1,
          labels: {
            'Verified': {approved: {}},
            'Code-Review': {approved: {}},
          },
        },
        {
          _number: 2,
          labels: {
            'Verified': {approved: {}},
            'Library-Compliance': {approved: {}},
          },
        },
      ]},
    ]).length, 3);

    assert.equal(element._computeLabelShortcut('Code-Review'), 'CR');
    assert.equal(element._computeLabelShortcut('Verified'), 'V');
    assert.equal(element._computeLabelShortcut('Library-Compliance'), 'LC');
    assert.equal(element._computeLabelShortcut('PolyGerrit-Review'), 'PR');
    assert.equal(element._computeLabelShortcut('polygerrit-review'), 'PR');
    assert.equal(element._computeLabelShortcut(
        'Invalid-Prolog-Rules-Label-Name--Verified'), 'V');
    assert.equal(element._computeLabelShortcut(
        'Some-Special-Label-7'), 'SSL7');
    assert.equal(element._computeLabelShortcut('--Too----many----dashes---'),
        'TMD');
    assert.equal(element._computeLabelShortcut(
        'Really-rather-entirely-too-long-of-a-label-name'), 'RRETL');
  });

  test('colspans', () => {
    element.sections = [
      {results: [{}]},
    ];
    flush();
    const tdItemCount = element.root.querySelectorAll(
        'td').length;

    const changeTableColumns = [];
    const labelNames = [];
    assert.equal(tdItemCount, element._computeColspan(
        {}, changeTableColumns, labelNames));
  });

  test('keyboard shortcuts', async () => {
    sinon.stub(element, '_computeLabelNames');
    element.sections = [
      {results: new Array(1)},
      {results: new Array(2)},
    ];
    element.selectedIndex = 0;
    element.changes = [
      {_number: 0},
      {_number: 1},
      {_number: 2},
    ];
    await flush();
    const promise = mockPromise();
    afterNextRender(element, () => {
      promise.resolve();
    });
    await promise;
    const elementItems = element.root.querySelectorAll(
        'gr-change-list-item');
    assert.equal(elementItems.length, 3);

    assert.isTrue(elementItems[0].hasAttribute('selected'));
    MockInteractions.pressAndReleaseKeyOn(element, 74, null, 'j');
    assert.equal(element.selectedIndex, 1);
    assert.isTrue(elementItems[1].hasAttribute('selected'));
    MockInteractions.pressAndReleaseKeyOn(element, 74, null, 'j');
    assert.equal(element.selectedIndex, 2);
    assert.isTrue(elementItems[2].hasAttribute('selected'));

    const navStub = sinon.stub(GerritNav, 'navigateToChange');
    assert.equal(element.selectedIndex, 2);
    MockInteractions.pressAndReleaseKeyOn(element, 13, null, 'enter');
    assert.deepEqual(navStub.lastCall.args[0], {_number: 2},
        'Should navigate to /c/2/');

    MockInteractions.pressAndReleaseKeyOn(element, 75, null, 'k');
    assert.equal(element.selectedIndex, 1);
    MockInteractions.pressAndReleaseKeyOn(element, 13, null, 'enter');
    assert.deepEqual(navStub.lastCall.args[0], {_number: 1},
        'Should navigate to /c/1/');

    MockInteractions.pressAndReleaseKeyOn(element, 75, null, 'k');
    MockInteractions.pressAndReleaseKeyOn(element, 75, null, 'k');
    MockInteractions.pressAndReleaseKeyOn(element, 75, null, 'k');
    assert.equal(element.selectedIndex, 0);
  });

  test('no changes', () => {
    element.changes = [];
    flush();
    const listItems = element.root.querySelectorAll(
        'gr-change-list-item');
    assert.equal(listItems.length, 0);
    const noChangesMsg =
        element.root.querySelector('.noChanges');
    assert.ok(noChangesMsg);
  });

  test('empty sections', () => {
    element.sections = [{results: []}, {results: []}];
    flush();
    const listItems = element.root.querySelectorAll(
        'gr-change-list-item');
    assert.equal(listItems.length, 0);
    const noChangesMsg = element.root.querySelectorAll(
        '.noChanges');
    assert.equal(noChangesMsg.length, 2);
  });

  suite('empty section', () => {
    test('not shown on empty non-outgoing sections', () => {
      const section = {results: []};
      assert.isTrue(element._isEmpty(section));
      assert.equal(element._getSpecialEmptySlot(section), '');
    });

    test('shown on empty outgoing sections', () => {
      const section = {results: [], isOutgoing: true};
      assert.isTrue(element._isEmpty(section));
      assert.equal(element._getSpecialEmptySlot(section), 'empty-outgoing');
    });

    test('shown on empty outgoing sections', () => {
      const section = {results: [], name: YOUR_TURN.name};
      assert.isTrue(element._isEmpty(section));
      assert.equal(element._getSpecialEmptySlot(section), 'empty-your-turn');
    });

    test('not shown on non-empty outgoing sections', () => {
      const section = {isOutgoing: true, results: [
        {_number: 0, labels: {Verified: {approved: {}}}}]};
      assert.isFalse(element._isEmpty(section));
    });
  });

  suite('empty column preference', () => {
    let element;

    setup(() => {
      element = basicFixture.instantiate();
      element.sections = [
        {results: [{}]},
      ];
      element.account = {_account_id: 1001};
      element.preferences = {
        legacycid_in_change_table: true,
        time_format: 'HHMM_12',
        change_table: [],
      };
      element._config = {};
      flush();
    });

    test('show number enabled', () => {
      assert.isTrue(element.showNumber);
    });

    test('all columns visible', () => {
      for (const column of element.changeTableColumns) {
        const elementClass = '.' + element._lowerCase(column);
        assert.isFalse(element.shadowRoot
            .querySelector(elementClass).hidden);
      }
    });
  });

  suite('full column preference', () => {
    let element;

    setup(() => {
      element = basicFixture.instantiate();
      element.sections = [
        {results: [{}]},
      ];
      element.account = {_account_id: 1001};
      element.preferences = {
        legacycid_in_change_table: true,
        time_format: 'HHMM_12',
        change_table: [
          'Subject',
          'Status',
          'Owner',
          'Reviewers',
          'Comments',
          'Repo',
          'Branch',
          'Updated',
          'Size',
          'Requirements',
        ],
      };
      element._config = {};
      flush();
    });

    test('all columns visible', () => {
      for (const column of element.changeTableColumns) {
        const elementClass = '.' + element._lowerCase(column);
        assert.isFalse(element.shadowRoot
            .querySelector(elementClass).hidden);
      }
    });
  });

  suite('partial column preference', () => {
    let element;

    setup(() => {
      element = basicFixture.instantiate();
      element.sections = [
        {results: [{}]},
      ];
      element.account = {_account_id: 1001};
      element.preferences = {
        legacycid_in_change_table: true,
        time_format: 'HHMM_12',
        change_table: [
          'Subject',
          'Status',
          'Owner',
          'Reviewers',
          'Comments',
          'Branch',
          'Updated',
          'Size',
          'Requirements',
        ],
      };
      element._config = {};
      flush();
    });

    test('all columns except repo visible', () => {
      for (const column of element.changeTableColumns) {
        const elementClass = '.' + column.toLowerCase();
        if (column === 'Repo') {
          assert.isNotOk(element.shadowRoot.querySelector(elementClass));
        } else {
          assert.isOk(element.shadowRoot.querySelector(elementClass));
        }
      }
    });
  });

  suite('random column does not exist', () => {
    let element;

    /* This would only exist if somebody manually updated the config
    file. */
    setup(() => {
      element = basicFixture.instantiate();
      element.account = {_account_id: 1001};
      element.preferences = {
        legacycid_in_change_table: true,
        time_format: 'HHMM_12',
        change_table: [
          'Bad',
        ],
      };
      flush();
    });

    test('bad column does not exist', () => {
      const elementClass = '.bad';
      assert.isNotOk(element.shadowRoot
          .querySelector(elementClass));
    });
  });

  suite('dashboard queries', () => {
    let element;

    setup(() => {
      element = basicFixture.instantiate();
    });

    teardown(() => { sinon.restore(); });

    test('query without age and limit unchanged', () => {
      const query = 'status:closed owner:me';
      assert.deepEqual(element._processQuery(query), query);
    });

    test('query with age and limit', () => {
      const query = 'status:closed age:1week limit:10 owner:me';
      const expectedQuery = 'status:closed owner:me';
      assert.deepEqual(element._processQuery(query), expectedQuery);
    });

    test('query with age', () => {
      const query = 'status:closed age:1week owner:me';
      const expectedQuery = 'status:closed owner:me';
      assert.deepEqual(element._processQuery(query), expectedQuery);
    });

    test('query with limit', () => {
      const query = 'status:closed limit:10 owner:me';
      const expectedQuery = 'status:closed owner:me';
      assert.deepEqual(element._processQuery(query), expectedQuery);
    });

    test('query with age as value and not key', () => {
      const query = 'status:closed random:age';
      const expectedQuery = 'status:closed random:age';
      assert.deepEqual(element._processQuery(query), expectedQuery);
    });

    test('query with limit as value and not key', () => {
      const query = 'status:closed random:limit';
      const expectedQuery = 'status:closed random:limit';
      assert.deepEqual(element._processQuery(query), expectedQuery);
    });

    test('query with -age key', () => {
      const query = 'status:closed -age:1week';
      const expectedQuery = 'status:closed';
      assert.deepEqual(element._processQuery(query), expectedQuery);
    });
  });

  suite('gr-change-list sections', () => {
    let element;

    setup(() => {
      element = basicFixture.instantiate();
    });

    test('keyboard shortcuts', async () => {
      element.selectedIndex = 0;
      element.sections = [
        {
          results: [
            {_number: 0},
            {_number: 1},
            {_number: 2},
          ],
        },
        {
          results: [
            {_number: 3},
            {_number: 4},
            {_number: 5},
          ],
        },
        {
          results: [
            {_number: 6},
            {_number: 7},
            {_number: 8},
          ],
        },
      ];
      await flush();
      const promise = mockPromise();
      afterNextRender(element, () => {
        promise.resolve();
      });
      await promise;
      const elementItems = element.root.querySelectorAll(
          'gr-change-list-item');
      assert.equal(elementItems.length, 9);

      MockInteractions.pressAndReleaseKeyOn(element, 74, null, 'j');
      assert.equal(element.selectedIndex, 1);
      MockInteractions.pressAndReleaseKeyOn(element, 74, null, 'j');

      const navStub = sinon.stub(GerritNav, 'navigateToChange');
      assert.equal(element.selectedIndex, 2);

      MockInteractions.pressAndReleaseKeyOn(element, 13, null, 'Enter');
      assert.deepEqual(navStub.lastCall.args[0], {_number: 2},
          'Should navigate to /c/2/');

      MockInteractions.pressAndReleaseKeyOn(element, 75, null, 'k');
      assert.equal(element.selectedIndex, 1);
      MockInteractions.pressAndReleaseKeyOn(element, 13, null, 'Enter');
      assert.deepEqual(navStub.lastCall.args[0], {_number: 1},
          'Should navigate to /c/1/');

      MockInteractions.pressAndReleaseKeyOn(element, 74, null, 'j');
      MockInteractions.pressAndReleaseKeyOn(element, 74, null, 'j');
      MockInteractions.pressAndReleaseKeyOn(element, 74, null, 'j');
      assert.equal(element.selectedIndex, 4);
      MockInteractions.pressAndReleaseKeyOn(element, 13, null, 'Enter');
      assert.deepEqual(navStub.lastCall.args[0], {_number: 4},
          'Should navigate to /c/4/');
    });

    test('_computeItemAbsoluteIndex', () => {
      sinon.stub(element, '_computeLabelNames');
      element.sections = [
        {results: new Array(1)},
        {results: new Array(2)},
        {results: new Array(3)},
      ];

      assert.equal(element._computeItemAbsoluteIndex(0, 0), 0);
      // Out of range but no matter.
      assert.equal(element._computeItemAbsoluteIndex(0, 1), 1);

      assert.equal(element._computeItemAbsoluteIndex(1, 0), 1);
      assert.equal(element._computeItemAbsoluteIndex(1, 1), 2);
      assert.equal(element._computeItemAbsoluteIndex(1, 2), 3);
      assert.equal(element._computeItemAbsoluteIndex(2, 0), 3);
      assert.equal(element._computeItemAbsoluteIndex(3, 0), 6);
    });
  });
});

