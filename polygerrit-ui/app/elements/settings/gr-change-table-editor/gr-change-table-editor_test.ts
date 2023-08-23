/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-change-table-editor';
import {GrChangeTableEditor} from './gr-change-table-editor';
import {queryAndAssert} from '../../../test/test-utils';
import {createServerInfo} from '../../../test/test-data-generators';
import {fixture, html, assert} from '@open-wc/testing';
import {ColumnNames} from '../../../constants/constants';

suite('gr-change-table-editor tests', () => {
  let element: GrChangeTableEditor;
  let columns: string[];

  setup(async () => {
    element = await fixture<GrChangeTableEditor>(
      html`<gr-change-table-editor></gr-change-table-editor>`
    );

    columns = [
      ColumnNames.SUBJECT,
      ColumnNames.OWNER,
      ColumnNames.REVIEWERS,
      ColumnNames.REPO,
      ColumnNames.BRANCH,
      ColumnNames.UPDATED,
      ColumnNames.SIZE,
      // ColumnNames.STATUS omitted for testing
    ];

    element.displayedColumns = columns;
    element.showNumber = false;
    element.serverConfig = createServerInfo();
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ ` <div class="gr-form-styles">
        <table id="changeCols">
          <thead>
            <tr>
              <th class="nameHeader">Column</th>
              <th class="visibleHeader">Visible</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><label for="numberCheckbox"> Number </label></td>
              <td class="checkboxContainer">
                <input id="numberCheckbox" name="number" type="checkbox" />
              </td>
            </tr>
            <tr>
              <td><label for="Subject"> Subject </label></td>
              <td class="checkboxContainer">
                <input checked="" id="Subject" name="Subject" type="checkbox" />
              </td>
            </tr>
            <tr>
              <td><label for="Owner"> Owner </label></td>
              <td class="checkboxContainer">
                <input checked="" id="Owner" name="Owner" type="checkbox" />
              </td>
            </tr>
            <tr>
              <td><label for="Reviewers"> Reviewers </label></td>
              <td class="checkboxContainer">
                <input
                  checked=""
                  id="Reviewers"
                  name="Reviewers"
                  type="checkbox"
                />
              </td>
            </tr>
            <tr>
              <td><label for="Repo"> Repo </label></td>
              <td class="checkboxContainer">
                <input checked="" id="Repo" name="Repo" type="checkbox" />
              </td>
            </tr>
            <tr>
              <td><label for="Branch"> Branch </label></td>
              <td class="checkboxContainer">
                <input checked="" id="Branch" name="Branch" type="checkbox" />
              </td>
            </tr>
            <tr>
              <td><label for="Updated"> Updated </label></td>
              <td class="checkboxContainer">
                <input checked="" id="Updated" name="Updated" type="checkbox" />
              </td>
            </tr>
            <tr>
              <td><label for="Size"> Size </label></td>
              <td class="checkboxContainer">
                <input checked="" id="Size" name="Size" type="checkbox" />
              </td>
            </tr>
            <tr>
              <td><label for="Status"> Status </label></td>
              <td class="checkboxContainer">
                <input id="Status" name="Status" type="checkbox" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>`
    );
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

  test('hide item', async () => {
    const checkbox = queryAndAssert<HTMLInputElement>(
      element,
      'table tr:nth-child(2) input'
    );
    const isChecked = checkbox.checked;
    const displayedLength = element.displayedColumns.length;
    assert.isTrue(isChecked);

    checkbox.click();
    await element.updateComplete;

    assert.equal(element.displayedColumns.length, displayedLength - 1);
  });

  test('show item', async () => {
    element.displayedColumns = [
      ColumnNames.STATUS,
      ColumnNames.OWNER,
      ColumnNames.REPO,
      ColumnNames.BRANCH,
      ColumnNames.UPDATED,
    ];
    // trigger computation of enabled displayed columns
    element.serverConfig = createServerInfo();
    await element.updateComplete;
    const checkbox = queryAndAssert<HTMLInputElement>(
      element,
      'table tr:nth-child(2) input'
    );
    const isChecked = checkbox.checked;
    const displayedLength = element.displayedColumns.length;
    assert.isFalse(isChecked);
    const table = queryAndAssert<HTMLTableElement>(element, 'table');
    assert.equal(table.style.display, '');

    checkbox.click();
    await element.updateComplete;

    assert.equal(element.displayedColumns.length, displayedLength + 1);
  });

  test('getDisplayedColumns', () => {
    const enabledColumns = columns;
    assert.deepEqual(element.getDisplayedColumns(), enabledColumns);
    const input = queryAndAssert<HTMLInputElement>(
      element,
      '.checkboxContainer input[name=Subject]'
    );
    input.click();
    assert.deepEqual(
      element.getDisplayedColumns(),
      enabledColumns.filter(c => c !== 'Subject')
    );
  });

  test('handleCheckboxContainerClick relays taps to checkboxes', async () => {
    const firstContainer = queryAndAssert<HTMLTableRowElement>(
      element,
      'table tr:first-of-type .checkboxContainer'
    );
    assert.isFalse(element.showNumber);
    firstContainer.click();
    assert.isTrue(element.showNumber);

    const lastContainer = queryAndAssert<HTMLTableRowElement>(
      element,
      'table tr:last-of-type .checkboxContainer'
    );
    const lastColumn =
      element.defaultColumns[element.defaultColumns.length - 1];
    assert.notInclude(element.displayedColumns, lastColumn);
    lastContainer.click();
    await element.updateComplete;
    assert.include(element.displayedColumns, lastColumn);
  });

  test('handleNumberCheckboxClick', () => {
    const numberInput = queryAndAssert<HTMLInputElement>(
      element,
      '.checkboxContainer input[name=number]'
    );
    numberInput.click();
    assert.isTrue(element.showNumber);

    numberInput.click();
    assert.isFalse(element.showNumber);
  });

  test('handleTargetClick', () => {
    assert.include(element.displayedColumns, 'Subject');
    const subjectInput = queryAndAssert<HTMLInputElement>(
      element,
      '.checkboxContainer input[name=Subject]'
    );
    subjectInput.click();
    assert.notInclude(element.displayedColumns, 'Subject');
  });
});
