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
  waitUntilObserved,
} from '../../../test/test-utils';
import {GrChangeListItem} from '../gr-change-list-item/gr-change-list-item';
import {ChangeListSection} from '../gr-change-list/gr-change-list';
import {fixture, html} from '@open-wc/testing-helpers';
import {ColumnNames} from '../../../constants/constants';

suite('gr-change-list section', () => {
  let element: GrChangeListSection;

  setup(async () => {
    const changeSection: ChangeListSection = {
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
    element = await fixture<GrChangeListSection>(
      html`<gr-change-list-section
        .account=${createAccountDetailWithId(1)}
        .config=${createServerInfo()}
        .visibleChangeTableColumns=${Object.values(ColumnNames)}
        .changeSection=${changeSection}
      ></gr-change-list-section> `
    );
  });

  test('selection checkbox is only shown if experiment is enabled', async () => {
    assert.isNotOk(query(element, '.selection'));

    stubFlags('isEnabled').returns(true);
    element.requestUpdate();
    await element.updateComplete;

    assert.isOk(query(element, '.selection'));
  });

  test('selection header is only shown if experiment is enabled', async () => {
    element.bulkActionsModel.setState({
      ...element.bulkActionsModel.getState(),
      selectedChangeNums: [1 as NumericChangeId],
    });
    await waitUntilObserved(
      element.bulkActionsModel.selectedChangeNums$,
      s => s.length === 1
    );

    assert.isNotOk(query(element, 'gr-change-list-action-bar'));
    stubFlags('isEnabled').returns(true);
    element.requestUpdate();
    await element.updateComplete;
    queryAndAssert(element, 'gr-change-list-action-bar');
  });

  suite('bulk actions selection', () => {
    let isEnabled: sinon.SinonStub;
    setup(async () => {
      isEnabled = stubFlags('isEnabled');
      isEnabled.returns(true);
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

    test('changing section does on trigger model sync when flag is disabled', async () => {
      isEnabled.returns(false);
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

      assert.isFalse(syncStub.called);
    });

    test('actions header is enabled/disabled based on selected changes', async () => {
      element.bulkActionsModel.setState({
        ...element.bulkActionsModel.getState(),
        selectedChangeNums: [],
      });
      await waitUntilObserved(
        element.bulkActionsModel.selectedChangeNums$,
        s => s.length === 0
      );
      assert.isFalse(element.showBulkActionsHeader);

      element.bulkActionsModel.setState({
        ...element.bulkActionsModel.getState(),
        selectedChangeNums: [1 as NumericChangeId],
      });
      await waitUntilObserved(
        element.bulkActionsModel.selectedChangeNums$,
        s => s.length === 1
      );
      assert.isTrue(element.showBulkActionsHeader);
    });

    test('select all checkbox checks all when none are selected', async () => {
      element.changeSection = {
        name: 'test',
        query: 'test',
        results: [
          {
            ...createChange(),
            _number: 1 as NumericChangeId,
            id: '1' as ChangeInfoId,
          },
          {
            ...createChange(),
            _number: 2 as NumericChangeId,
            id: '2' as ChangeInfoId,
          },
        ],
        emptyStateSlotName: 'test',
      };
      await element.updateComplete;
      let rows = queryAll(element, 'gr-change-list-item');
      assert.lengthOf(rows, 2);
      assert.isFalse(
        queryAndAssert<HTMLInputElement>(rows[0], 'input').checked
      );
      assert.isFalse(
        queryAndAssert<HTMLInputElement>(rows[1], 'input').checked
      );

      const checkbox = queryAndAssert<HTMLInputElement>(element, 'input');
      checkbox.click();
      await waitUntilObserved(
        element.bulkActionsModel.selectedChangeNums$,
        s => s.length === 2
      );
      await element.updateComplete;

      rows = queryAll(element, 'gr-change-list-item');
      assert.lengthOf(rows, 2);
      assert.isTrue(queryAndAssert<HTMLInputElement>(rows[0], 'input').checked);
      assert.isTrue(queryAndAssert<HTMLInputElement>(rows[1], 'input').checked);
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

  suite('dashboard queries', () => {
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
});
