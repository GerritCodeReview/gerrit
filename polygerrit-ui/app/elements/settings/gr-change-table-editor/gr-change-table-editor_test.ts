/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import '../../../test/common-test-setup-karma';
import './gr-change-table-editor';
import {GrChangeTableEditor} from './gr-change-table-editor';
import {queryAndAssert} from '../../../test/test-utils';
import {createServerInfo} from '../../../test/test-data-generators';

const basicFixture = fixtureFromElement('gr-change-table-editor');

suite('gr-change-table-editor tests', () => {
  let element: GrChangeTableEditor;
  let columns: string[];

  setup(async () => {
    element = basicFixture.instantiate();

    columns = [
      'Subject',
      'Status',
      'Owner',
      'Reviewers',
      'Comments',
      'Repo',
      'Branch',
      'Updated',
    ];

    element.set('displayedColumns', columns);
    element.showNumber = false;
    element.serverConfig = createServerInfo();
    await flush();
  });

  test('renders', () => {
    const rows = queryAndAssert(element, 'tbody').querySelectorAll('tr');
    let tds;

    // The `+ 1` is for the number column, which isn't included in the change
    // table behavior's list.
    assert.equal(rows.length, element.defaultColumns.length + 1);
    for (let i = 0; i < element.defaultColumns.length; i++) {
      tds = rows[i + 1].querySelectorAll('td');
      assert.equal(tds[0].textContent, element.defaultColumns[i]);
    }
  });

  test('hide item', () => {
    const checkbox = queryAndAssert<HTMLInputElement>(
      element,
      'table tr:nth-child(2) input'
    );
    const isChecked = checkbox.checked;
    const displayedLength = element.displayedColumns.length;
    assert.isTrue(isChecked);

    MockInteractions.tap(checkbox);
    flush();

    assert.equal(element.displayedColumns.length, displayedLength - 1);
  });

  test('show item', () => {
    element.set('displayedColumns', [
      'Status',
      'Owner',
      'Repo',
      'Branch',
      'Updated',
    ]);
    // trigger computation of enabled displayed columns
    element.serverConfig = createServerInfo();
    flush();
    const checkbox = queryAndAssert<HTMLInputElement>(
      element,
      'table tr:nth-child(2) input'
    );
    const isChecked = checkbox.checked;
    const displayedLength = element.displayedColumns.length;
    assert.isFalse(isChecked);
    const table = queryAndAssert<HTMLTableElement>(element, 'table');
    assert.equal(table.style.display, '');

    MockInteractions.tap(checkbox);
    flush();

    assert.equal(element.displayedColumns.length, displayedLength + 1);
  });

  test('_getDisplayedColumns', () => {
    const enabledColumns = columns.filter(column =>
      element._isColumnEnabled(column, element.serverConfig!, [])
    );
    assert.deepEqual(element._getDisplayedColumns(), enabledColumns);
    const input = queryAndAssert<HTMLInputElement>(
      element,
      '.checkboxContainer input[name=Subject]'
    );
    MockInteractions.tap(input);
    assert.deepEqual(
      element._getDisplayedColumns(),
      enabledColumns.filter(c => c !== 'Subject')
    );
  });

  test('_handleCheckboxContainerClick relays taps to checkboxes', () => {
    const checkBoxClickStub = sinon.stub(element, '_handleNumberCheckboxClick');
    const targetClickStub = sinon.stub(element, '_handleTargetClick');

    const firstContainer = queryAndAssert(
      element,
      'table tr:first-of-type .checkboxContainer'
    );
    MockInteractions.tap(firstContainer);
    assert.isTrue(checkBoxClickStub.calledOnce);
    assert.isFalse(targetClickStub.called);

    const lastContainer = queryAndAssert(
      element,
      'table tr:last-of-type .checkboxContainer'
    );
    MockInteractions.tap(lastContainer);
    assert.isTrue(checkBoxClickStub.calledOnce);
    assert.isTrue(targetClickStub.calledOnce);
  });

  test('_handleNumberCheckboxClick', () => {
    const checkBoxClickSpy = sinon.spy(element, '_handleNumberCheckboxClick');

    const numberInput = queryAndAssert(
      element,
      '.checkboxContainer input[name=number]'
    );
    MockInteractions.tap(numberInput);
    assert.isTrue(checkBoxClickSpy.calledOnce);
    assert.isTrue(element.showNumber);

    MockInteractions.tap(numberInput);
    assert.isTrue(checkBoxClickSpy.calledTwice);
    assert.isFalse(element.showNumber);
  });

  test('_handleTargetClick', () => {
    const targetClickSpy = sinon.spy(element, '_handleTargetClick');
    assert.include(element.displayedColumns, 'Subject');
    const subjectInput = queryAndAssert(
      element,
      '.checkboxContainer input[name=Subject]'
    );
    MockInteractions.tap(subjectInput);
    assert.isTrue(targetClickSpy.calledOnce);
    assert.notInclude(element.displayedColumns, 'Subject');
  });
});
