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
import '../../../test/common-test-setup-karma';
import './gr-change-list';
import {GrChangeList} from './gr-change-list';
import {afterNextRender} from '@polymer/polymer/lib/utils/render-status';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {
  mockPromise,
  pressKey,
  query,
  queryAll,
  queryAndAssert,
  stubFlags,
} from '../../../test/test-utils';
import {YOUR_TURN} from '../../core/gr-navigation/gr-navigation';
import {Key} from '../../../utils/dom-util';
import {TimeFormat} from '../../../constants/constants';
import {AccountId, NumericChangeId} from '../../../types/common';
import {
  createChange,
  createServerInfo,
} from '../../../test/test-data-generators';
import {GrChangeListItem} from '../gr-change-list-item/gr-change-list-item';

const basicFixture = fixtureFromElement('gr-change-list');

suite('gr-change-list basic tests', () => {
  let element: GrChangeList;

  setup(() => {
    element = basicFixture.instantiate();
  });

  suite('test show change number not logged in', () => {
    setup(() => {
      element = basicFixture.instantiate();
      element.account = undefined;
      element.preferences = undefined;
      element._config = createServerInfo();
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
        time_format: TimeFormat.HHMM_12,
        change_table: [],
      };
      element.account = {_account_id: 1001 as AccountId};
      element._config = createServerInfo();
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
        time_format: TimeFormat.HHMM_12,
        change_table: [],
      };
      element.account = {_account_id: 1001 as AccountId};
      element._config = createServerInfo();
      flush();
    });

    test('show number disabled', () => {
      assert.isFalse(element.showNumber);
    });
  });

  test('computed fields', () => {
    assert.equal(
      element._computeLabelNames([
        {
          results: [
            {...createChange(), _number: 0 as NumericChangeId, labels: {}},
          ],
        },
      ]).length,
      0
    );
    assert.equal(
      element._computeLabelNames([
        {
          results: [
            {
              ...createChange(),
              _number: 0 as NumericChangeId,
              labels: {Verified: {approved: {}}},
            },
            {
              ...createChange(),
              _number: 1 as NumericChangeId,
              labels: {
                Verified: {approved: {}},
                'Code-Review': {approved: {}},
              },
            },
            {
              ...createChange(),
              _number: 2 as NumericChangeId,
              labels: {
                Verified: {approved: {}},
                'Library-Compliance': {approved: {}},
              },
            },
          ],
        },
      ]).length,
      3
    );

    assert.equal(element._computeLabelShortcut('Code-Review'), 'CR');
    assert.equal(element._computeLabelShortcut('Verified'), 'V');
    assert.equal(element._computeLabelShortcut('Library-Compliance'), 'LC');
    assert.equal(element._computeLabelShortcut('PolyGerrit-Review'), 'PR');
    assert.equal(element._computeLabelShortcut('polygerrit-review'), 'PR');
    assert.equal(
      element._computeLabelShortcut(
        'Invalid-Prolog-Rules-Label-Name--Verified'
      ),
      'V'
    );
    assert.equal(element._computeLabelShortcut('Some-Special-Label-7'), 'SSL7');
    assert.equal(
      element._computeLabelShortcut('--Too----many----dashes---'),
      'TMD'
    );
    assert.equal(
      element._computeLabelShortcut(
        'Really-rather-entirely-too-long-of-a-label-name'
      ),
      'RRETL'
    );
  });

  test('colspans', () => {
    element.sections = [{results: [{...createChange()}]}];
    flush();
    const tdItemCount = queryAll<HTMLTableElement>(element, 'td').length;

    const changeTableColumns: string[] | undefined = [];
    const labelNames: string[] | undefined = [];
    assert.equal(
      tdItemCount,
      element._computeColspan(
        {results: [{...createChange()}]},
        changeTableColumns,
        labelNames
      )
    );
  });

  test('keyboard shortcuts', async () => {
    sinon.stub(element, '_computeLabelNames');
    element.sections = [{results: new Array(1)}, {results: new Array(2)}];
    element.selectedIndex = 0;
    element.changes = [
      {...createChange(), _number: 0 as NumericChangeId},
      {...createChange(), _number: 1 as NumericChangeId},
      {...createChange(), _number: 2 as NumericChangeId},
    ];
    await flush();
    const promise = mockPromise();
    afterNextRender(element, () => {
      promise.resolve();
    });
    await promise;
    const elementItems = queryAll<GrChangeListItem>(
      element,
      'gr-change-list-item'
    );
    assert.equal(elementItems.length, 3);

    assert.isTrue(elementItems[0].hasAttribute('selected'));
    pressKey(element, 'j');
    assert.equal(element.selectedIndex, 1);
    assert.isTrue(elementItems[1].hasAttribute('selected'));
    pressKey(element, 'j');
    assert.equal(element.selectedIndex, 2);
    assert.isTrue(elementItems[2].hasAttribute('selected'));

    const navStub = sinon.stub(GerritNav, 'navigateToChange');
    assert.equal(element.selectedIndex, 2);
    pressKey(element, Key.ENTER);
    assert.deepEqual(
      navStub.lastCall.args[0],
      {...createChange(), _number: 2 as NumericChangeId},
      'Should navigate to /c/2/'
    );

    pressKey(element, 'k');
    assert.equal(element.selectedIndex, 1);
    pressKey(element, Key.ENTER);
    assert.deepEqual(
      navStub.lastCall.args[0],
      {...createChange(), _number: 1 as NumericChangeId},
      'Should navigate to /c/1/'
    );

    pressKey(element, 'k');
    pressKey(element, 'k');
    pressKey(element, 'k');
    assert.equal(element.selectedIndex, 0);
  });

  test('no changes', () => {
    element.changes = [];
    flush();
    const listItems = queryAll<GrChangeListItem>(
      element,
      'gr-change-list-item'
    );
    assert.equal(listItems.length, 0);
    const noChangesMsg = queryAndAssert<HTMLTableRowElement>(
      element,
      '.noChanges'
    );
    assert.ok(noChangesMsg);
  });

  test('empty sections', () => {
    element.sections = [{results: []}, {results: []}];
    flush();
    const listItems = queryAll<GrChangeListItem>(
      element,
      'gr-change-list-item'
    );
    assert.equal(listItems.length, 0);
    const noChangesMsg = queryAll<HTMLTableRowElement>(element, '.noChanges');
    assert.equal(noChangesMsg.length, 2);
  });

  suite('empty section', () => {
    test('not shown on empty non-outgoing sections', () => {
      const section = {name: 'test', query: 'test', results: []};
      assert.isTrue(element._isEmpty(section));
      assert.equal(element._getSpecialEmptySlot(section), '');
    });

    test('shown on empty outgoing sections', () => {
      const section = {
        name: 'test',
        query: 'test',
        results: [],
        isOutgoing: true,
      };
      assert.isTrue(element._isEmpty(section));
      assert.equal(element._getSpecialEmptySlot(section), 'empty-outgoing');
    });

    test('shown on empty outgoing sections', () => {
      const section = {name: YOUR_TURN.name, query: 'test', results: []};
      assert.isTrue(element._isEmpty(section));
      assert.equal(element._getSpecialEmptySlot(section), 'empty-your-turn');
    });

    test('not shown on non-empty outgoing sections', () => {
      const section = {
        name: 'test',
        query: 'test',
        isOutgoing: true,
        results: [
          {
            ...createChange(),
            _number: 0 as NumericChangeId,
            labels: {Verified: {approved: {}}},
          },
        ],
      };
      assert.isFalse(element._isEmpty(section));
    });
  });

  suite('empty column preference', () => {
    let element: GrChangeList;

    setup(() => {
      stubFlags('isEnabled').returns(true);
      element = basicFixture.instantiate();
      element.sections = [{results: [{...createChange()}]}];
      element.account = {_account_id: 1001 as AccountId};
      element.preferences = {
        legacycid_in_change_table: true,
        time_format: TimeFormat.HHMM_12,
        change_table: [],
      };
      element._config = createServerInfo();
      flush();
    });

    test('show number enabled', () => {
      assert.isTrue(element.showNumber);
    });

    test('all columns visible', () => {
      for (const column of element.changeTableColumns!) {
        const elementClass = '.' + column.trim().toLowerCase();
        assert.isFalse(
          queryAndAssert<HTMLElement>(element, elementClass)!.hidden
        );
      }
    });
  });

  suite('full column preference', () => {
    let element: GrChangeList;

    setup(() => {
      stubFlags('isEnabled').returns(true);
      element = basicFixture.instantiate();
      element.sections = [{results: [{...createChange()}]}];
      element.account = {_account_id: 1001 as AccountId};
      element.preferences = {
        legacycid_in_change_table: true,
        time_format: TimeFormat.HHMM_12,
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
          ' Status ',
        ],
      };
      element._config = createServerInfo();
      flush();
    });

    test('all columns visible', () => {
      for (const column of element.changeTableColumns!) {
        const elementClass = '.' + column.trim().toLowerCase();
        assert.isFalse(
          queryAndAssert<HTMLElement>(element, elementClass).hidden
        );
      }
    });
  });

  suite('partial column preference', () => {
    let element: GrChangeList;

    setup(() => {
      stubFlags('isEnabled').returns(true);
      element = basicFixture.instantiate();
      element.sections = [{results: [{...createChange()}]}];
      element.account = {_account_id: 1001 as AccountId};
      element.preferences = {
        legacycid_in_change_table: true,
        time_format: TimeFormat.HHMM_12,
        change_table: [
          'Subject',
          'Status',
          'Owner',
          'Reviewers',
          'Comments',
          'Branch',
          'Updated',
          'Size',
          ' Status ',
        ],
      };
      element._config = createServerInfo();
      flush();
    });

    test('all columns except repo visible', () => {
      for (const column of element.changeTableColumns!) {
        const elementClass = '.' + column.trim().toLowerCase();
        if (column === 'Repo') {
          assert.isNotOk(query<HTMLElement>(element, elementClass));
        } else {
          assert.isOk(queryAndAssert<HTMLElement>(element, elementClass));
        }
      }
    });
  });

  test('obsolete column in preferences not visible', () => {
    assert.isTrue(element._isColumnEnabled('Subject'));
    assert.isFalse(element._isColumnEnabled('Assignee'));
  });

  suite('random column does not exist', () => {
    let element: GrChangeList;

    /* This would only exist if somebody manually updated the config
    file. */
    setup(() => {
      element = basicFixture.instantiate();
      element.account = {_account_id: 1001 as AccountId};
      element.preferences = {
        legacycid_in_change_table: true,
        time_format: TimeFormat.HHMM_12,
        change_table: ['Bad'],
      };
      flush();
    });

    test('bad column does not exist', () => {
      assert.isNotOk(query<HTMLElement>(element, '.bad'));
    });
  });

  suite('dashboard queries', () => {
    let element: GrChangeList;

    setup(() => {
      element = basicFixture.instantiate();
    });

    teardown(() => {
      sinon.restore();
    });

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
    let element: GrChangeList;

    setup(() => {
      element = basicFixture.instantiate();
    });

    test('keyboard shortcuts', async () => {
      element.selectedIndex = 0;
      element.sections = [
        {
          results: [
            {...createChange(), _number: 0 as NumericChangeId},
            {...createChange(), _number: 1 as NumericChangeId},
            {...createChange(), _number: 2 as NumericChangeId},
          ],
        },
        {
          results: [
            {...createChange(), _number: 3 as NumericChangeId},
            {...createChange(), _number: 4 as NumericChangeId},
            {...createChange(), _number: 5 as NumericChangeId},
          ],
        },
        {
          results: [
            {...createChange(), _number: 6 as NumericChangeId},
            {...createChange(), _number: 7 as NumericChangeId},
            {...createChange(), _number: 8 as NumericChangeId},
          ],
        },
      ];
      await flush();
      const promise = mockPromise();
      afterNextRender(element, () => {
        promise.resolve();
      });
      await promise;
      const elementItems = queryAll<GrChangeListItem>(
        element,
        'gr-change-list-item'
      );
      assert.equal(elementItems.length, 9);

      pressKey(element, 'j');
      assert.equal(element.selectedIndex, 1);
      pressKey(element, 'j');

      const navStub = sinon.stub(GerritNav, 'navigateToChange');
      assert.equal(element.selectedIndex, 2);

      pressKey(element, Key.ENTER);
      assert.deepEqual(
        navStub.lastCall.args[0],
        {...createChange(), _number: 2 as NumericChangeId},
        'Should navigate to /c/2/'
      );

      pressKey(element, 'k');
      assert.equal(element.selectedIndex, 1);
      pressKey(element, Key.ENTER);
      assert.deepEqual(
        navStub.lastCall.args[0],
        {...createChange(), _number: 1 as NumericChangeId},
        'Should navigate to /c/1/'
      );

      pressKey(element, 'j');
      pressKey(element, 'j');
      pressKey(element, 'j');
      assert.equal(element.selectedIndex, 4);
      pressKey(element, Key.ENTER);
      assert.deepEqual(
        navStub.lastCall.args[0],
        {...createChange(), _number: 4 as NumericChangeId},
        'Should navigate to /c/4/'
      );
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
