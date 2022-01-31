import {GrChangeListSection} from './gr-change-list-section';
import '../../../test/common-test-setup-karma';
import './gr-change-list-section';
import {createChange} from '../../../test/test-data-generators';
import {NumericChangeId} from '../../../api/rest-api';
import {queryAll, pressKey} from '../../../test/test-utils';
import {GrChangeListItem} from '../gr-change-list-item/gr-change-list-item';
import {GerritNav, YOUR_TURN} from '../../core/gr-navigation/gr-navigation';
import {Key} from '../../../utils/dom-util';

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
    await element.updateComplete;
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
  });

  test('colspans', async () => {
    element.sections = [{results: [{...createChange()}]}];
    await element.updateComplete;
    const tdItemCount = queryAll<HTMLTableElement>(element, 'td').length;

    element.visibleChangeTableColumns = [];
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

  test('empty sections', async () => {
    element.sections = [{results: []}, {results: []}];
    await element.updateComplete;
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
