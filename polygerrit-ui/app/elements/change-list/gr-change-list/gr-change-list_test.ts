/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-change-list';
import {GrChangeList, computeRelativeIndex} from './gr-change-list';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {
  pressKey,
  query,
  queryAll,
  queryAndAssert,
  stubFlags,
  waitUntil,
} from '../../../test/test-utils';
import {Key} from '../../../utils/dom-util';
import {
  ColumnNames,
  createDefaultPreferences,
  TimeFormat,
} from '../../../constants/constants';
import {AccountId, NumericChangeId} from '../../../types/common';
import {
  createAccountWithEmail,
  createChange,
  createServerInfo,
  createSubmitRequirementResultInfo,
} from '../../../test/test-data-generators';
import {GrChangeListItem} from '../gr-change-list-item/gr-change-list-item';
import {GrChangeListSection} from '../gr-change-list-section/gr-change-list-section';
import {fixture, assert} from '@open-wc/testing';
import {html} from 'lit';
import {testResolver} from '../../../test/common-test-setup';
import {Timestamp} from '../../../api/rest-api';

suite('gr-change-list basic tests', () => {
  let element: GrChangeList;

  setup(async () => {
    element = await fixture(html`<gr-change-list></gr-change-list>`);
  });

  test('renders', async () => {
    element.preferences = {
      legacycid_in_change_table: true,
      time_format: TimeFormat.HHMM_12,
      change_table: [],
    };
    element.account = {_account_id: 1001 as AccountId};
    element.config = createServerInfo();
    element.sections = [
      {
        results: [{...createChange(), _number: 0 as NumericChangeId}],
      },
      {
        results: [
          {...createChange(), _number: 1 as NumericChangeId},
          {...createChange(), _number: 2 as NumericChangeId},
        ],
      },
    ];
    element.selectedIndex = 0;
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-change-list-section> </gr-change-list-section>
        <gr-change-list-section> </gr-change-list-section>
        <table id="changeList"></table>
      `
    );
  });

  test('sections receive global startIndex', async () => {
    element.selectedIndex = 0;
    element.sections = [
      {
        results: [{...createChange(), _number: 0 as NumericChangeId}],
      },
      {
        results: [
          {...createChange(), _number: 1 as NumericChangeId},
          {...createChange(), _number: 2 as NumericChangeId},
        ],
      },
      {
        results: [
          {...createChange(), _number: 3 as NumericChangeId},
          {...createChange(), _number: 4 as NumericChangeId},
        ],
      },
    ];
    await element.updateComplete;

    assert.deepEqual(
      [...element.shadowRoot!.querySelectorAll('gr-change-list-section')].map(
        section => section.startIndex
      ),
      [0, 1, 3]
    );
  });

  test('show change number disabled when not logged in', async () => {
    element.account = undefined;
    element.preferences = undefined;
    element.config = createServerInfo();
    await element.updateComplete;

    assert.isFalse(element.showNumber);
  });

  test('show legacy change num when legacycid preference enabled', async () => {
    element.preferences = {
      legacycid_in_change_table: true,
      time_format: TimeFormat.HHMM_12,
      change_table: [],
    };
    element.account = {_account_id: 1001 as AccountId};
    element.config = createServerInfo();
    await element.updateComplete;

    assert.isTrue(element.showNumber);
  });

  test('hide legacy change num if legacycid preference disabled', async () => {
    // legacycid_in_change_table is not set when false.
    element.preferences = {
      time_format: TimeFormat.HHMM_12,
      change_table: [],
    };
    element.account = {_account_id: 1001 as AccountId};
    element.config = createServerInfo();
    await element.updateComplete;

    assert.isFalse(element.showNumber);
  });

  test('computeRelativeIndex', () => {
    element.sections = [{results: new Array(1)}, {results: new Array(2)}];

    let selectedChangeIndex = 0;
    assert.equal(
      computeRelativeIndex(selectedChangeIndex, 0, element.sections),
      0
    );

    // index lies outside the first section
    assert.equal(
      computeRelativeIndex(selectedChangeIndex, 1, element.sections),
      undefined
    );

    selectedChangeIndex = 2;

    // index lies outside the first section
    assert.equal(
      computeRelativeIndex(selectedChangeIndex, 0, element.sections),
      undefined
    );

    // 3rd change belongs to the second section
    assert.equal(
      computeRelativeIndex(selectedChangeIndex, 1, element.sections),
      1
    );
  });

  test('computed fields', () => {
    assert.equal(
      element.computeLabelNames([
        {
          results: [
            {...createChange(), _number: 0 as NumericChangeId, labels: {}},
          ],
        },
      ]).length,
      0
    );
    assert.equal(
      element.computeLabelNames([
        {
          results: [
            {
              ...createChange(),
              _number: 0 as NumericChangeId,
              submit_requirements: [
                {
                  ...createSubmitRequirementResultInfo(),
                  name: 'Verified',
                },
              ],
            },
            {
              ...createChange(),
              _number: 1 as NumericChangeId,
              submit_requirements: [
                {
                  ...createSubmitRequirementResultInfo(),
                  name: 'Verified',
                },
                {
                  ...createSubmitRequirementResultInfo(),
                  name: 'Code-Review',
                },
              ],
            },
            {
              ...createChange(),
              _number: 2 as NumericChangeId,
              submit_requirements: [
                {
                  ...createSubmitRequirementResultInfo(),
                  name: 'Library-Compliance',
                },
              ],
            },
          ],
        },
      ]).length,
      3
    );
  });

  test('keyboard shortcuts', async () => {
    sinon.stub(element, 'computeLabelNames');
    element.sections = [{results: new Array(1)}, {results: new Array(2)}];
    element.selectedIndex = 0;
    element.preferences = createDefaultPreferences();
    element.config = createServerInfo();
    element.changes = [
      {...createChange(), _number: 0 as NumericChangeId},
      {...createChange(), _number: 1 as NumericChangeId},
      {...createChange(), _number: 2 as NumericChangeId},
    ];
    // explicitly trigger sectionsChanged so that cursor stops are properly
    // updated
    await element.sectionsChanged();
    await element.updateComplete;
    const section = queryAndAssert<GrChangeListSection>(
      element,
      'gr-change-list-section'
    );
    await section.updateComplete;
    const elementItems = queryAll<GrChangeListItem>(
      section,
      'gr-change-list-item'
    );
    assert.equal(elementItems.length, 3);

    assert.isTrue(elementItems[0].selected);
    await element.updateComplete;
    pressKey(element, 'j');
    await element.updateComplete;
    await section.updateComplete;

    assert.equal(element.selectedIndex, 1);
    assert.isTrue(elementItems[1].selected);
    pressKey(element, 'j');
    await element.updateComplete;
    assert.equal(element.selectedIndex, 2);
    assert.isTrue(elementItems[2].selected);

    const setUrlStub = sinon.stub(testResolver(navigationToken), 'setUrl');
    assert.equal(element.selectedIndex, 2);
    pressKey(element, Key.ENTER);
    await waitUntil(() => setUrlStub.callCount >= 1);
    assert.equal(setUrlStub.lastCall.firstArg, '/c/test-project/+/2');

    pressKey(element, 'k');
    await element.updateComplete;
    await section.updateComplete;

    assert.equal(element.selectedIndex, 1);

    const prevCount = setUrlStub.callCount;
    pressKey(element, Key.ENTER);

    await waitUntil(() => setUrlStub.callCount > prevCount);
    assert.equal(setUrlStub.lastCall.firstArg, '/c/test-project/+/1');

    pressKey(element, 'k');
    pressKey(element, 'k');
    pressKey(element, 'k');
    assert.equal(element.selectedIndex, 0);
  });

  test('toggle checkbox keyboard shortcut', async () => {
    element.userModel.setAccount({
      ...createAccountWithEmail('abc@def.com'),
      registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
    });
    await element.updateComplete;

    const getCheckbox = (item: GrChangeListItem) =>
      queryAndAssert<HTMLInputElement>(query(item, '.selection'), 'input');

    sinon.stub(element, 'computeLabelNames');
    element.sections = [{results: new Array(1)}, {results: new Array(2)}];
    element.selectedIndex = 0;
    element.preferences = createDefaultPreferences();
    element.config = createServerInfo();
    element.changes = [
      {...createChange(), _number: 0 as NumericChangeId},
      {...createChange(), _number: 1 as NumericChangeId},
      {...createChange(), _number: 2 as NumericChangeId},
    ];
    // explicitly trigger sectionsChanged so that cursor stops are properly
    // updated
    await element.sectionsChanged();
    await element.updateComplete;
    const section = queryAndAssert<GrChangeListSection>(
      element,
      'gr-change-list-section'
    );
    await section.updateComplete;
    const elementItems = queryAll<GrChangeListItem>(
      section,
      'gr-change-list-item'
    );
    assert.equal(elementItems.length, 3);

    assert.isTrue(elementItems[0].selected);
    await element.updateComplete;

    pressKey(element, 'x');
    await element.updateComplete;

    assert.isTrue(getCheckbox(elementItems[0]).checked);

    pressKey(element, 'x');
    await element.updateComplete;
    assert.isFalse(getCheckbox(elementItems[0]).checked);

    pressKey(element, 'j');
    await element.updateComplete;

    assert.equal(element.selectedIndex, 1);
    assert.isTrue(elementItems[1].selected);

    pressKey(element, 'x');
    await element.updateComplete;

    assert.isTrue(getCheckbox(elementItems[1]).checked);

    pressKey(element, 'x');
    await element.updateComplete;
    assert.isFalse(getCheckbox(elementItems[1]).checked);

    pressKey(element, 'j');
    await element.updateComplete;
    assert.equal(element.selectedIndex, 2);
    assert.isTrue(elementItems[2].selected);

    pressKey(element, 'x');
    await element.updateComplete;

    assert.isTrue(getCheckbox(elementItems[2]).checked);

    pressKey(element, 'x');
    await element.updateComplete;
    assert.isFalse(getCheckbox(elementItems[2]).checked);
  });

  test('no changes', async () => {
    element.changes = [];
    await element.updateComplete;
    const listItems = queryAll<GrChangeListItem>(
      element,
      'gr-change-list-item'
    );
    assert.equal(listItems.length, 0);
    const section = queryAndAssert(element, 'gr-change-list-section');
    const noChangesMsg = queryAndAssert<HTMLTableRowElement>(
      section,
      '.noChanges'
    );
    assert.ok(noChangesMsg);
  });

  test('empty sections', async () => {
    element.sections = [{results: []}, {results: []}];
    await element.updateComplete;
    const listItems = queryAll<GrChangeListItem>(
      element,
      'gr-change-list-item'
    );
    assert.equal(listItems.length, 0);
    const sections = queryAll<GrChangeListSection>(
      element,
      'gr-change-list-section'
    );
    sections.forEach(section => {
      assert.isOk(query(section, '.noChanges'));
    });
  });

  suite('empty column preference', () => {
    let element: GrChangeList;

    setup(async () => {
      stubFlags('isEnabled').returns(true);
      element = await fixture(html`<gr-change-list></gr-change-list>`);
      element.sections = [{results: [{...createChange()}]}];
      element.account = {_account_id: 1001 as AccountId};
      element.preferences = {
        legacycid_in_change_table: true,
        time_format: TimeFormat.HHMM_12,
        change_table: [],
      };
      element.config = createServerInfo();
      await element.updateComplete;
    });

    test('show number enabled', () => {
      assert.isTrue(element.showNumber);
    });

    test('all columns visible', () => {
      for (const column of element.changeTableColumns!) {
        const elementClass = '.' + column.trim().toLowerCase();
        const section = queryAndAssert(element, 'gr-change-list-section');
        assert.isFalse(
          queryAndAssert<HTMLElement>(section, elementClass)!.hidden
        );
      }
    });
  });

  suite('full column preference', () => {
    let element: GrChangeList;

    setup(async () => {
      stubFlags('isEnabled').returns(true);
      element = await fixture(html`<gr-change-list></gr-change-list>`);
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
          ColumnNames.STATUS2,
        ],
      };
      element.config = createServerInfo();
      await element.updateComplete;
    });

    test('all columns visible', () => {
      for (const column of element.changeTableColumns!) {
        const elementClass = '.' + column.trim().toLowerCase();
        const section = queryAndAssert(element, 'gr-change-list-section');
        assert.isFalse(
          queryAndAssert<HTMLElement>(section, elementClass).hidden
        );
      }
    });
  });

  suite('partial column preference', () => {
    let element: GrChangeList;

    setup(async () => {
      stubFlags('isEnabled').returns(true);
      element = await fixture(html`<gr-change-list></gr-change-list>`);
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
          ColumnNames.STATUS2,
        ],
      };
      element.config = createServerInfo();
      await element.updateComplete;
    });

    test('all columns except repo visible', () => {
      for (const column of element.changeTableColumns!) {
        const elementClass = '.' + column.trim().toLowerCase();
        const section = queryAndAssert(element, 'gr-change-list-section');
        if (column === 'Repo') {
          assert.isNotOk(query<HTMLElement>(section, elementClass));
        } else {
          assert.isOk(queryAndAssert<HTMLElement>(section, elementClass));
        }
      }
    });

    test('show default order not preferences order', async () => {
      element.preferences = {
        legacycid_in_change_table: true,
        time_format: TimeFormat.HHMM_12,
        change_table: ['Owner', 'Subject'],
      };
      element.config = createServerInfo();
      await element.updateComplete;
      assert.equal(element.visibleChangeTableColumns?.[0], 'Subject');
      assert.equal(element.visibleChangeTableColumns?.[1], 'Owner');
    });
  });

  test('obsolete column in preferences not visible', () => {
    assert.isTrue(element.isColumnEnabled('Subject'));
    assert.isFalse(element.isColumnEnabled('Assignee'));
  });

  test('loggedIn and showNumber', async () => {
    element.sections = [{results: [{...createChange()}], name: 'a'}];
    element.account = {_account_id: 1001 as AccountId};
    element.preferences = {
      legacycid_in_change_table: false, // sets showNumber false
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
        ColumnNames.STATUS2,
      ],
    };
    element.config = createServerInfo();
    element.userModel.setAccount(undefined);
    await element.updateComplete;
    const section = query<GrChangeListSection>(
      element,
      'gr-change-list-section'
    )!;
    await section.updateComplete;

    const items = await element.getListItems();
    assert.equal(items.length, 1);

    assert.isNotOk(query(query(section, 'gr-change-list-item'), '.star'));
    assert.isNotOk(query(query(section, 'gr-change-list-item'), '.number'));

    element.userModel.setAccount({
      ...createAccountWithEmail('abc@def.com'),
      registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
    });
    await element.updateComplete;
    await section.updateComplete;
    assert.isOk(query(query(section, 'gr-change-list-item'), '.star'));
    assert.isNotOk(query(query(section, 'gr-change-list-item'), '.number'));

    element.showNumber = true;
    await element.updateComplete;
    await section.updateComplete;
    assert.isOk(query(query(section, 'gr-change-list-item'), '.star'));
    assert.isOk(query(query(section, 'gr-change-list-item'), '.number'));
  });

  test('garbage columns in preference are not shown', async () => {
    // This would only exist if somebody manually updated the config file.
    element.account = {_account_id: 1001 as AccountId};
    element.preferences = {
      legacycid_in_change_table: true,
      time_format: TimeFormat.HHMM_12,
      change_table: ['Bad'],
    };
    await element.updateComplete;

    assert.isNotOk(query<HTMLElement>(element, '.bad'));
  });

  test('Show new status with feature flag', async () => {
    stubFlags('isEnabled').returns(true);
    const element: GrChangeList = await fixture(
      html`<gr-change-list></gr-change-list>`
    );
    element.sections = [{results: [{...createChange()}]}];
    element.account = {_account_id: 1001 as AccountId};
    element.preferences = {
      change_table: [
        'Status', // old status
      ],
    };
    element.config = createServerInfo();
    await element.updateComplete;
    assert.isTrue(
      element.visibleChangeTableColumns?.includes(ColumnNames.STATUS2),
      'Show new status'
    );
    const section = queryAndAssert(element, 'gr-change-list-section');
    queryAndAssert<HTMLElement>(section, '.status');
  });
});
