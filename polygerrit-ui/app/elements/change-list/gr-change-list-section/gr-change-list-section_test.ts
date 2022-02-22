/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {
  GrChangeListSection,
  computeLabelShortcut,
} from './gr-change-list-section';
import '../../../test/common-test-setup-karma';
import './gr-change-list-section';
import {
  createChange,
  createAccountDetailWithId,
  createServerInfo,
} from '../../../test/test-data-generators';
import {NumericChangeId, ChangeInfoId} from '../../../api/rest-api';
import {
  queryAll,
  query,
  queryAndAssert,
  stubFlags,
} from '../../../test/test-utils';
import {GrChangeListItem} from '../gr-change-list-item/gr-change-list-item';
import {columnNames} from '../gr-change-list/gr-change-list';

const basicFixture = fixtureFromElement('gr-change-list-section');

suite('gr-change-list section', () => {
  let element: GrChangeListSection;

  setup(async () => {
    element = basicFixture.instantiate();
    element.account = createAccountDetailWithId(1);
    element.config = createServerInfo();
    element.visibleChangeTableColumns = columnNames;
    element.changeSection = {
      name: 'test',
      query: 'test',
      results: [
        {
          ...createChange(),
          _number: 0 as NumericChangeId,
          id: '0' as ChangeInfoId,
        },
        {
          ...createChange(),
          _number: 1 as NumericChangeId,
          id: '1' as ChangeInfoId,
        },
      ],
      emptyStateSlotName: 'test',
    };
    await element.updateComplete;
  });

  test('selection checkbox is only shown if experiment is enabled', async () => {
    assert.isNotOk(query(element, '.selection'));

    stubFlags('isEnabled').returns(true);
    element.requestUpdate();
    await element.updateComplete;

    assert.isOk(query(element, '.selection'));
  });

  suite('bulk actions selection', () => {
    setup(async () => {
      stubFlags('isEnabled').returns(true);
      element.requestUpdate();
      await element.updateComplete;
    });

    test('changing section triggers model sync', async () => {
      const syncStub = sinon.stub(element.bulkActionsModel, 'sync');
      assert.isFalse(syncStub.called);
      element.changeSection = {
        name: 'test',
        query: 'test',
        results: [
          {
            ...createChange(),
            _number: 1 as NumericChangeId,
            id: '1' as ChangeInfoId,
          },
        ],
        emptyStateSlotName: 'test',
      };
      await element.updateComplete;

      assert.isTrue(syncStub.called);
    });
  });

  test('colspans', async () => {
    element.visibleChangeTableColumns = [];
    element.changeSection = {results: [{...createChange()}]};
    await element.updateComplete;
    const tdItemCount = queryAll<HTMLTableElement>(element, 'td').length;

    element.labelNames = [];
    assert.equal(tdItemCount, element.computeColspan(element.computeColumns()));
  });

  test('computeItemSelected', () => {
    element.selectedIndex = 1;
    assert.isTrue(element.computeItemSelected(1));
    assert.isFalse(element.computeItemSelected(2));
  });

  test('computed fields', () => {
    assert.equal(computeLabelShortcut('Code-Review'), 'CR');
    assert.equal(computeLabelShortcut('Verified'), 'V');
    assert.equal(computeLabelShortcut('Library-Compliance'), 'LC');
    assert.equal(computeLabelShortcut('PolyGerrit-Review'), 'PR');
    assert.equal(computeLabelShortcut('polygerrit-review'), 'PR');
    assert.equal(
      computeLabelShortcut('Invalid-Prolog-Rules-Label-Name--Verified'),
      'V'
    );
    assert.equal(computeLabelShortcut('Some-Special-Label-7'), 'SSL7');
    assert.equal(computeLabelShortcut('--Too----many----dashes---'), 'TMD');
    assert.equal(
      computeLabelShortcut('Really-rather-entirely-too-long-of-a-label-name'),
      'RRETL'
    );
  });

  suite('empty section slots', () => {
    test('empty section', async () => {
      element.changeSection = {results: []};
      await element.updateComplete;
      const listItems = queryAll<GrChangeListItem>(
        element,
        'gr-change-list-item'
      );
      assert.equal(listItems.length, 0);
      const noChangesMsg = queryAll<HTMLTableRowElement>(element, '.noChanges');
      assert.equal(noChangesMsg.length, 1);
    });

    test('are shown on empty sections with slot name', async () => {
      const section = {
        name: 'test',
        query: 'test',
        results: [],
        emptyStateSlotName: 'test',
      };
      element.changeSection = section;
      await element.updateComplete;

      assert.isEmpty(queryAll(element, 'gr-change-list-item'));
      queryAndAssert(element, 'slot[name="test"]');
    });

    test('are not shown on empty sections without slot name', async () => {
      const section = {name: 'test', query: 'test', results: []};
      element.changeSection = section;
      await element.updateComplete;

      assert.isEmpty(queryAll(element, 'gr-change-list-item'));
      assert.notExists(query(element, 'slot[name="test"]'));
    });

    test('are not shown on non-empty sections with slot name', async () => {
      const section = {
        name: 'test',
        query: 'test',
        emptyStateSlotName: 'test',
        results: [
          {
            ...createChange(),
            _number: 0 as NumericChangeId,
            labels: {Verified: {approved: {}}},
          },
        ],
      };
      element.changeSection = section;
      await element.updateComplete;

      assert.isNotEmpty(queryAll(element, 'gr-change-list-item'));
      assert.notExists(query(element, 'slot[name="test"]'));
    });
  });
});

suite('dashboard queries', () => {
  let element: GrChangeListSection;

  setup(() => {
    element = basicFixture.instantiate();
  });

  teardown(() => {
    sinon.restore();
  });

  test('query without age and limit unchanged', () => {
    const query = 'status:closed owner:me';
    assert.deepEqual(element.processQuery(query), query);
  });

  test('query with age and limit', () => {
    const query = 'status:closed age:1week limit:10 owner:me';
    const expectedQuery = 'status:closed owner:me';
    assert.deepEqual(element.processQuery(query), expectedQuery);
  });

  test('query with age', () => {
    const query = 'status:closed age:1week owner:me';
    const expectedQuery = 'status:closed owner:me';
    assert.deepEqual(element.processQuery(query), expectedQuery);
  });

  test('query with limit', () => {
    const query = 'status:closed limit:10 owner:me';
    const expectedQuery = 'status:closed owner:me';
    assert.deepEqual(element.processQuery(query), expectedQuery);
  });

  test('query with age as value and not key', () => {
    const query = 'status:closed random:age';
    const expectedQuery = 'status:closed random:age';
    assert.deepEqual(element.processQuery(query), expectedQuery);
  });

  test('query with limit as value and not key', () => {
    const query = 'status:closed random:limit';
    const expectedQuery = 'status:closed random:limit';
    assert.deepEqual(element.processQuery(query), expectedQuery);
  });

  test('query with -age key', () => {
    const query = 'status:closed -age:1week';
    const expectedQuery = 'status:closed';
    assert.deepEqual(element.processQuery(query), expectedQuery);
  });
});
