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

import '../../../test/common-test-setup-karma.js';
import './gr-change-table-editor.js';

const basicFixture = fixtureFromElement('gr-change-table-editor');

suite('gr-change-table-editor tests', () => {
  let element;
  let columns;

  setup(() => {
    element = basicFixture.instantiate();

    columns = [
      'Subject',
      'Status',
      'Owner',
      'Assignee',
      'Reviewers',
      'Comments',
      'Repo',
      'Branch',
      'Updated',
    ];

    element.set('displayedColumns', columns);
    element.showNumber = false;
    flush();
  });

  test('renders', () => {
    const rows = element.shadowRoot
        .querySelector('tbody').querySelectorAll('tr');
    let tds;

    // The `+ 1` is for the number column, which isn't included in the change
    // table behavior's list.
    assert.equal(rows.length, element.columnNames.length + 1);
    for (let i = 0; i < columns.length; i++) {
      tds = rows[i + 1].querySelectorAll('td');
      assert.equal(tds[0].textContent, columns[i]);
    }
  });

  test('hide item', () => {
    const checkbox = element.shadowRoot
        .querySelector('table tr:nth-child(2) input');
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
      'Assignee',
      'Repo',
      'Branch',
      'Updated',
    ]);
    flush();
    const checkbox = element.shadowRoot
        .querySelector('table tr:nth-child(2) input');
    const isChecked = checkbox.checked;
    const displayedLength = element.displayedColumns.length;
    assert.isFalse(isChecked);
    assert.equal(element.shadowRoot
        .querySelector('table').style.display, '');

    MockInteractions.tap(checkbox);
    flush();

    assert.equal(element.displayedColumns.length,
        displayedLength + 1);
  });

  test('_getDisplayedColumns', () => {
    assert.deepEqual(element._getDisplayedColumns(), columns);
    MockInteractions.tap(
        element.shadowRoot
            .querySelector('.checkboxContainer input[name=Assignee]'));
    assert.deepEqual(element._getDisplayedColumns(),
        columns.filter(c => c !== 'Assignee'));
  });

  test('_handleCheckboxContainerClick relays taps to checkboxes', () => {
    sinon.stub(element, '_handleNumberCheckboxClick');
    sinon.stub(element, '_handleTargetClick');

    MockInteractions.tap(
        element.shadowRoot
            .querySelector('table tr:first-of-type .checkboxContainer'));
    assert.isTrue(element._handleNumberCheckboxClick.calledOnce);
    assert.isFalse(element._handleTargetClick.called);

    MockInteractions.tap(
        element.shadowRoot
            .querySelector('table tr:last-of-type .checkboxContainer'));
    assert.isTrue(element._handleNumberCheckboxClick.calledOnce);
    assert.isTrue(element._handleTargetClick.calledOnce);
  });

  test('_handleNumberCheckboxClick', () => {
    sinon.spy(element, '_handleNumberCheckboxClick');

    MockInteractions
        .tap(element.shadowRoot
            .querySelector('.checkboxContainer input[name=number]'));
    assert.isTrue(element._handleNumberCheckboxClick.calledOnce);
    assert.isTrue(element.showNumber);

    MockInteractions
        .tap(element.shadowRoot
            .querySelector('.checkboxContainer input[name=number]'));
    assert.isTrue(element._handleNumberCheckboxClick.calledTwice);
    assert.isFalse(element.showNumber);
  });

  test('_handleTargetClick', () => {
    sinon.spy(element, '_handleTargetClick');
    assert.include(element.displayedColumns, 'Assignee');
    MockInteractions
        .tap(element.shadowRoot
            .querySelector('.checkboxContainer input[name=Assignee]'));
    assert.isTrue(element._handleTargetClick.calledOnce);
    assert.notInclude(element.displayedColumns, 'Assignee');
  });
});

