import {GrChangeListSection} from './gr-change-list-section';
import '../../../test/common-test-setup-karma';
import './gr-change-list-section';
import {
  createChange,
  createAccountDetailWithId,
  createServerInfo,
} from '../../../test/test-data-generators';
import {NumericChangeId} from '../../../api/rest-api';
import {queryAll} from '../../../test/test-utils';
import {GrChangeListItem} from '../gr-change-list-item/gr-change-list-item';
import {YOUR_TURN} from '../../core/gr-navigation/gr-navigation';
import {columnNames} from '../gr-change-list/gr-change-list';

/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

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

  test('computeItemAbsoluteIndex', () => {
    element.sections = [
      {results: new Array(1)},
      {results: new Array(2)},
      {results: new Array(3)},
    ];

    assert.equal(element.computeItemAbsoluteIndex(0, 0), 0);
    // Out of range but no matter.
    assert.equal(element.computeItemAbsoluteIndex(0, 1), 1);

    assert.equal(element.computeItemAbsoluteIndex(1, 0), 1);
    assert.equal(element.computeItemAbsoluteIndex(1, 1), 2);
    assert.equal(element.computeItemAbsoluteIndex(1, 2), 3);
    assert.equal(element.computeItemAbsoluteIndex(2, 0), 3);
    assert.equal(element.computeItemAbsoluteIndex(3, 0), 6);
  });
});

suite('gr-change-list basic tests', () => {
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

    const labelNames: string[] | undefined = [];
    assert.equal(
      tdItemCount,
      element.computeColspan({results: [{...createChange()}]}, labelNames)
    );
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

  suite('empty section', () => {
    test('not shown on empty non-outgoing sections', () => {
      const section = {name: 'test', query: 'test', results: []};
      assert.isTrue(element.isEmpty(section));
      assert.equal(element.getSpecialEmptySlot(section), '');
    });

    test('shown on empty outgoing sections', () => {
      const section = {
        name: 'test',
        query: 'test',
        results: [],
        isOutgoing: true,
      };
      assert.isTrue(element.isEmpty(section));
      assert.equal(element.getSpecialEmptySlot(section), 'empty-outgoing');
    });

    test('shown on empty outgoing sections', () => {
      const section = {name: YOUR_TURN.name, query: 'test', results: []};
      assert.isTrue(element.isEmpty(section));
      assert.equal(element.getSpecialEmptySlot(section), 'empty-your-turn');
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
      assert.isFalse(element.isEmpty(section));
    });
  });
});
