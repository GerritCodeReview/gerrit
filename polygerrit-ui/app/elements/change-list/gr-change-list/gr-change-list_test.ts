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
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {
  pressKey,
  query,
  queryAll,
  queryAndAssert,
  stubFlags,
  waitUntil,
} from '../../../test/test-utils';
import {Key} from '../../../utils/dom-util';
import {TimeFormat} from '../../../constants/constants';
import {AccountId, NumericChangeId} from '../../../types/common';
import {
  createChange,
  createServerInfo,
  createQuickLabelInfo,
} from '../../../test/test-data-generators';
import {GrChangeListItem} from '../gr-change-list-item/gr-change-list-item';
import {GrChangeListSection} from '../gr-change-list-section/gr-change-list-section';

const basicFixture = fixtureFromElement('gr-change-list');

suite('gr-change-list basic tests', () => {
  let element: GrChangeList;

  setup(() => {
    element = basicFixture.instantiate();
  });

  suite('test show change number not logged in', () => {
    setup(async () => {
      element = basicFixture.instantiate();
      element.account = undefined;
      element.preferences = undefined;
      element.config = createServerInfo();
      await element.updateComplete;
    });

    test('show number disabled', () => {
      assert.isFalse(element.showNumber);
    });
  });

  suite('test show change number preference enabled', () => {
    setup(async () => {
      element = basicFixture.instantiate();
      element.preferences = {
        legacycid_in_change_table: true,
        time_format: TimeFormat.HHMM_12,
        change_table: [],
      };
      element.account = {_account_id: 1001 as AccountId};
      element.config = createServerInfo();
      await element.updateComplete;
    });

    test('show number enabled', () => {
      assert.isTrue(element.showNumber);
    });
  });

  suite('test show change number preference disabled', () => {
    setup(async () => {
      element = basicFixture.instantiate();
      // legacycid_in_change_table is not set when false.
      element.preferences = {
        time_format: TimeFormat.HHMM_12,
        change_table: [],
      };
      element.account = {_account_id: 1001 as AccountId};
      element.config = createServerInfo();
      await element.updateComplete;
    });

    test('show number disabled', () => {
      assert.isFalse(element.showNumber);
    });
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
  });

  test('keyboard shortcuts', async () => {
    sinon.stub(element, 'computeLabelNames');
    element.sections = [{results: new Array(1)}, {results: new Array(2)}];
    element.selectedIndex = 0;
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
    element.config = createServerInfo();
    element.changes = [
      {...createChange(), _number: 0 as NumericChangeId},
      {...createChange(), _number: 1 as NumericChangeId},
      {...createChange(), _number: 2 as NumericChangeId},
    ];
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

    assert.isTrue(elementItems[0].hasAttribute('selected'));
    await element.updateComplete;
    pressKey(element, 'j');
    await element.updateComplete;
    assert.equal(element.selectedIndex, 1);
    assert.isTrue(elementItems[1].hasAttribute('selected'));
    pressKey(element, 'j');
    await element.updateComplete;
    assert.equal(element.selectedIndex, 2);
    assert.isTrue(elementItems[2].hasAttribute('selected'));

    const navStub = sinon.stub(GerritNav, 'navigateToChange');
    assert.equal(element.selectedIndex, 2);
    pressKey(element, Key.ENTER);
    await waitUntil(() => navStub.called);
    assert.deepEqual(
      navStub.lastCall.args[0],
      {...createChange(), _number: 2 as NumericChangeId},
      'Should navigate to /c/2/'
    );

    pressKey(element, 'k');
    await element.updateComplete;
    assert.equal(element.selectedIndex, 1);
    pressKey(element, Key.ENTER);
    await waitUntil(() => navStub.calledTwice);
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

  test('showNewUserHelp', async () => {
    element.showNewUserHelp = false;
    element.sections = [{results: [], isOutgoing: true}];
    element.selectedIndex = 0;
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
    element.config = createServerInfo();

    await element.updateComplete;

    assert.equal(
      query(element, '#emptyOutgoing')?.textContent?.trim(),
      'No changes'
    );
    assert.isNotOk(query(element, 'gr-create-change-help'));
    element.showNewUserHelp = true;

    await element.updateComplete;

    assert.notEqual(
      query(element, '#emptyOutgoing')?.textContent?.trim(),
      'No changes'
    );
    assert.isOk(query(element, 'gr-create-change-help'));
  });

  test('selection checkbox is only shown if experiment is enabled', async () => {
    function propertiesSetup(element: GrChangeList) {
      const change = {...createChange()};
      change.labels = {a: createQuickLabelInfo()};
      element.sections = [{results: [change], name: 'a'}];
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
      element.config = createServerInfo();
    }

    element = basicFixture.instantiate();
    propertiesSetup(element);
    await element.updateComplete;
    let section = query(element, 'gr-change-list-section');
    assert.isOk(section);
    assert.isNotOk(query(section, '.selection'));

    stubFlags('isEnabled').returns(true);
    element = basicFixture.instantiate();
    propertiesSetup(element);
    await element.updateComplete;

    section = query(element, 'gr-change-list-section');
    assert.isOk(section);
    assert.isOk(query(section, '.selection'));
  });

  suite('empty column preference', () => {
    let element: GrChangeList;

    setup(async () => {
      stubFlags('isEnabled').returns(true);
      element = basicFixture.instantiate();
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
  });

  test('obsolete column in preferences not visible', () => {
    assert.isTrue(element._isColumnEnabled('Subject'));
    assert.isFalse(element._isColumnEnabled('Assignee'));
  });

  suite('random column does not exist', () => {
    let element: GrChangeList;

    /* This would only exist if somebody manually updated the config
    file. */
    setup(async () => {
      element = basicFixture.instantiate();
      element.account = {_account_id: 1001 as AccountId};
      element.preferences = {
        legacycid_in_change_table: true,
        time_format: TimeFormat.HHMM_12,
        change_table: ['Bad'],
      };
      await element.updateComplete;
    });

    test('bad column does not exist', () => {
      assert.isNotOk(query<HTMLElement>(element, '.bad'));
    });
  });
});
