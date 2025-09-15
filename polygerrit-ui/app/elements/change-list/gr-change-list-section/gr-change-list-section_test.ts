/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import {
  computeLabelShortcut,
  GrChangeListSection,
} from './gr-change-list-section';
import '../../../test/common-test-setup';
import './gr-change-list-section';
import '../gr-change-list-item/gr-change-list-item';
import {
  createAccountDetailWithId,
  createAccountWithEmail,
  createChange,
  createServerInfo,
} from '../../../test/test-data-generators';
import {ChangeInfoId, NumericChangeId, Timestamp} from '../../../api/rest-api';
import {
  query,
  queryAll,
  queryAndAssert,
  stubFlags,
  waitUntilObserved,
} from '../../../test/test-utils';
import {GrChangeListItem} from '../gr-change-list-item/gr-change-list-item';
import {ChangeListSection} from '../gr-change-list/gr-change-list';
import {assert, fixture, html} from '@open-wc/testing';
import {ColumnNames} from '../../../constants/constants';
import {testResolver} from '../../../test/common-test-setup';
import {UserModel, userModelToken} from '../../../models/user/user-model';

suite('gr-change-list section', () => {
  let element: GrChangeListSection;
  let userModel: UserModel;

  setup(async () => {
    userModel = testResolver(userModelToken);
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
        .loggedInUser=${createAccountDetailWithId(1)}
        .config=${createServerInfo()}
        .visibleChangeTableColumns=${Object.values(ColumnNames)}
        .changeSection=${changeSection}
      ></gr-change-list-section> `
    );
  });

  test('renders headers when no changes are selected', () => {
    // TODO: Check table elements. The shadowDom helper does not understand
    // tables interacting with display: contents, even wrapping the element in a
    // table, does not help.
    assert.shadowDom.equal(
      element,
      /* prettier-ignore */ /* HTML */ `
      <td class="selection">
        <md-checkbox class="selection-checkbox">
        </md-checkbox>
      </td>
      #
              SubjectOwnerReviewersRepoBranchUpdatedSizeStatus
      <gr-change-list-item
        aria-label="Test subject, section: test"
        role="button"
        tabindex="0"
      >
      </gr-change-list-item>
      <gr-change-list-item
        aria-label="Test subject, section: test"
        role="button"
        tabindex="0"
      >
      </gr-change-list-item>
    `
    );
  });

  test('renders action bar when some changes are selected', async () => {
    assert.isNotOk(query(element, 'gr-change-list-action-bar'));
    element.bulkActionsModel.setState({
      ...element.bulkActionsModel.getState(),
      selectedChangeNums: [1 as NumericChangeId],
    });
    await waitUntilObserved(
      element.bulkActionsModel.selectedChangeNums$,
      s => s.length === 1
    );

    element.requestUpdate();
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* prettier-ignore */ /* HTML */ `
        <td class="selection">
      <md-checkbox
        checked=""
         class="selection-checkbox"
       >
      </md-checkbox>
        </td>
        <gr-change-list-action-bar></gr-change-list-action-bar>
        <gr-change-list-item
          aria-label="Test subject, section: test"
          role="button"
          tabindex="0"
        >
        </gr-change-list-item>
        <gr-change-list-item
          aria-label="Test subject, section: test"
          checked=""
          role="button"
          tabindex="0"
        >
        </gr-change-list-item>
      `
    );
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

    test('actions header is enabled/disabled based on selected changes', async () => {
      element.bulkActionsModel.setState({
        ...element.bulkActionsModel.getState(),
        selectedChangeNums: [],
      });
      await waitUntilObserved(
        element.bulkActionsModel.selectedChangeNums$,
        s => s.length === 0
      );
      assert.isFalse(element.numSelected > 0);

      element.bulkActionsModel.setState({
        ...element.bulkActionsModel.getState(),
        selectedChangeNums: [1 as NumericChangeId],
      });
      await waitUntilObserved(
        element.bulkActionsModel.selectedChangeNums$,
        s => s.length === 1
      );
      assert.isTrue(element.numSelected > 0);
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
      userModel.setAccount({
        ...createAccountWithEmail('abc@def.com'),
        registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
      });
      await element.updateComplete;
      let rows = queryAll(element, 'gr-change-list-item');
      assert.lengthOf(rows, 2);
      assert.isFalse(
        queryAndAssert<HTMLInputElement>(rows[0], 'input').checked
      );
      assert.isFalse(
        queryAndAssert<HTMLInputElement>(rows[1], 'input').checked
      );

      const checkbox = queryAndAssert<HTMLInputElement>(element, 'md-checkbox');
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

    test('checkbox matches partial and fully selected state', async () => {
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
      userModel.setAccount({
        ...createAccountWithEmail('abc@def.com'),
        registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
      });
      await element.updateComplete;
      const rows = queryAll(element, 'gr-change-list-item');

      // zero case
      let checkbox = queryAndAssert<HTMLInputElement>(element, 'md-checkbox');
      assert.isFalse(checkbox.checked);
      assert.isFalse(checkbox.indeterminate);

      // partial case
      queryAndAssert<HTMLInputElement>(rows[0], 'input').click();
      await element.updateComplete;

      checkbox = queryAndAssert<HTMLInputElement>(element, 'md-checkbox');
      assert.isTrue(checkbox.indeterminate);

      // plural case
      queryAndAssert<HTMLInputElement>(rows[1], 'input').click();
      await element.updateComplete;

      checkbox = queryAndAssert<HTMLInputElement>(element, 'md-checkbox');
      assert.isFalse(checkbox.indeterminate);
      assert.isTrue(checkbox.checked);

      // Clicking Check All checkbox when all checkboxes selected unselects
      // all checkboxes
      queryAndAssert<HTMLInputElement>(element, 'md-checkbox');
      checkbox.click();
      await element.updateComplete;

      assert.isFalse(
        queryAndAssert<HTMLInputElement>(rows[0], 'input').checked
      );
      assert.isFalse(
        queryAndAssert<HTMLInputElement>(rows[1], 'input').checked
      );
    });
  });

  test('no checkbox when logged out', async () => {
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
    userModel.setAccount(undefined);
    await element.updateComplete;
    const rows = queryAll(element, 'gr-change-list-item');
    assert.lengthOf(rows, 2);
    assert.isUndefined(query<HTMLInputElement>(rows[0], 'input'));
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
