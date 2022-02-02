/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {GrChangeListSection} from './gr-change-list-section';
import '../../../test/common-test-setup-karma';
import './gr-change-list-section';
import {
  createChange,
  createAccountDetailWithId,
  createServerInfo,
} from '../../../test/test-data-generators';
import {NumericChangeId} from '../../../api/rest-api';
import {queryAll, query, queryAndAssert} from '../../../test/test-utils';
import {GrChangeListItem} from '../gr-change-list-item/gr-change-list-item';
import {columnNames} from '../gr-change-list/gr-change-list';

const basicFixture = fixtureFromElement('gr-change-list-section');

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

suite('gr-change-list sections', () => {
  let element: GrChangeListSection;

  setup(() => {
    element = basicFixture.instantiate();
    element.account = createAccountDetailWithId(1);
    element.config = createServerInfo();
    element.visibleChangeTableColumns = columnNames;
    element.sectionIndex = 0;
  });

  suite('empty section slots', () => {
    test('are shown on empty sections with slot name', async () => {
      const section = {
        name: 'test',
        query: 'test',
        results: [],
        emptyStateSlotName: 'test',
      };
      element.sections = [section];
      element.changeSection = section;
      await element.updateComplete;

      assert.isEmpty(queryAll(element, 'gr-change-list-item'));
      queryAndAssert(element, 'slot[name="test"]');
    });

    test('are not shown on empty sections without slot name', async () => {
      const section = {name: 'test', query: 'test', results: []};
      element.sections = [section];
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
      element.sections = [section];
      element.changeSection = section;
      await element.updateComplete;

      assert.isNotEmpty(queryAll(element, 'gr-change-list-item'));
      assert.notExists(query(element, 'slot[name="test"]'));
    });
  });
});

suite('gr-change-list-section basic tests', () => {
  let element: GrChangeListSection;

  setup(() => {
    element = basicFixture.instantiate();
    element.account = createAccountDetailWithId(1);
    element.config = createServerInfo();
    element.visibleChangeTableColumns = columnNames;
    element.sectionIndex = 0;
    element.selectedIndex = 0;
  });

  test('colspans', async () => {
    element.visibleChangeTableColumns = [];
    element.sections = [{results: [{...createChange()}]}];
    element.changeSection = {results: [{...createChange()}]};
    await element.updateComplete;
    const tdItemCount = queryAll<HTMLTableElement>(element, 'td').length;

    element.labelNames = [];
    assert.equal(tdItemCount, element.computeColspan(element.computeColumns()));
  });

  test('computed fields', () => {
    assert.equal(element.computeLabelShortcut('Code-Review'), 'CR');
    assert.equal(element.computeLabelShortcut('Verified'), 'V');
    assert.equal(element.computeLabelShortcut('Library-Compliance'), 'LC');
    assert.equal(element.computeLabelShortcut('PolyGerrit-Review'), 'PR');
    assert.equal(element.computeLabelShortcut('polygerrit-review'), 'PR');
    assert.equal(
      element.computeLabelShortcut('Invalid-Prolog-Rules-Label-Name--Verified'),
      'V'
    );
    assert.equal(element.computeLabelShortcut('Some-Special-Label-7'), 'SSL7');
    assert.equal(
      element.computeLabelShortcut('--Too----many----dashes---'),
      'TMD'
    );
    assert.equal(
      element.computeLabelShortcut(
        'Really-rather-entirely-too-long-of-a-label-name'
      ),
      'RRETL'
    );
  });

  test('empty section', async () => {
    element.sections = [{results: []}, {results: []}];
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
});
