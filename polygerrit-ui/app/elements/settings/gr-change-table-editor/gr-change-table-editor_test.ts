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
import {assert, fixture, html} from '@open-wc/testing';
import {ColumnNames} from '../../../constants/constants';
import {MdCheckbox} from '@material/web/checkbox/checkbox';

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
              <td>
                <label for="numberCheckbox"> Number </label>
              </td>
              <td class="checkboxContainer">
                <md-checkbox id="numberCheckbox" name="number"> </md-checkbox>
              </td>
            </tr>
            <tr>
              <td>
                <label for="Subject"> Subject </label>
              </td>
              <td class="checkboxContainer">
                <md-checkbox checked="" id="Subject" name="Subject">
                </md-checkbox>
              </td>
            </tr>
            <tr>
              <td>
                <label for="Owner"> Owner </label>
              </td>
              <td class="checkboxContainer">
                <md-checkbox checked="" id="Owner" name="Owner"> </md-checkbox>
              </td>
            </tr>
            <tr>
              <td>
                <label for="Reviewers"> Reviewers </label>
              </td>
              <td class="checkboxContainer">
                <md-checkbox checked="" id="Reviewers" name="Reviewers">
                </md-checkbox>
              </td>
            </tr>
            <tr>
              <td>
                <label for="Repo"> Repo </label>
              </td>
              <td class="checkboxContainer">
                <md-checkbox checked="" id="Repo" name="Repo"> </md-checkbox>
              </td>
            </tr>
            <tr>
              <td>
                <label for="Branch"> Branch </label>
              </td>
              <td class="checkboxContainer">
                <md-checkbox checked="" id="Branch" name="Branch">
                </md-checkbox>
              </td>
            </tr>
            <tr>
              <td>
                <label for="Updated"> Updated </label>
              </td>
              <td class="checkboxContainer">
                <md-checkbox checked="" id="Updated" name="Updated">
                </md-checkbox>
              </td>
            </tr>
            <tr>
              <td>
                <label for="Size"> Size </label>
              </td>
              <td class="checkboxContainer">
                <md-checkbox checked="" id="Size" name="Size"> </md-checkbox>
              </td>
            </tr>
            <tr>
              <td>
                <label for="Status"> Status </label>
              </td>
              <td class="checkboxContainer">
                <md-checkbox id="Status" name="Status"> </md-checkbox>
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
    const checkbox = queryAndAssert<MdCheckbox>(
      element,
      'table tr:nth-child(2) md-checkbox'
    );
    const isChecked = checkbox.checked;
    const displayedLength = element.displayedColumns.length;
    assert.isTrue(isChecked);

    checkbox.click();
    await element.updateComplete;

    assert.equal(element.displayedColumns.length, displayedLength - 1);
  });

  test('show and hide item', async () => {
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

    const checkboxSubject = queryAndAssert<MdCheckbox>(
      element,
      'table tr:nth-child(2) md-checkbox'
    );
    assert.equal(checkboxSubject.name, 'Subject');
    const checkboxOwner = queryAndAssert<MdCheckbox>(
      element,
      'table tr:nth-child(3) md-checkbox'
    );
    assert.equal(checkboxOwner.name, 'Owner');

    assert.equal(element.displayedColumns.length, 5);
    assert.isFalse(checkboxSubject.checked);
    assert.isTrue(checkboxOwner.checked);

    checkboxSubject.click();
    await element.updateComplete;

    assert.equal(element.displayedColumns.length, 6);
    assert.isTrue(checkboxSubject.checked);
    assert.isTrue(checkboxOwner.checked);

    checkboxOwner.click();
    await element.updateComplete;

    assert.equal(element.displayedColumns.length, 5);
    assert.isTrue(checkboxSubject.checked);
    assert.isFalse(checkboxOwner.checked);
  });

  test('getDisplayedColumns', () => {
    const enabledColumns = columns;
    assert.deepEqual(element.getDisplayedColumns(), enabledColumns);
    const input = queryAndAssert<MdCheckbox>(
      element,
      '.checkboxContainer md-checkbox[name=Subject]'
    );
    input.click();
    assert.deepEqual(
      element.getDisplayedColumns(),
      enabledColumns.filter(c => c !== 'Subject')
    );
  });

  test('handleNumberCheckboxClick', async () => {
    const numberInput = queryAndAssert<MdCheckbox>(
      element,
      '.checkboxContainer md-checkbox[name=number]'
    );
    numberInput.click();
    await element.updateComplete;
    assert.isTrue(element.showNumber);

    numberInput.click();
    await element.updateComplete;
    assert.isFalse(element.showNumber);
  });

  test('handleTargetClick', () => {
    assert.include(element.displayedColumns, 'Subject');
    const subjectInput = queryAndAssert<MdCheckbox>(
      element,
      '.checkboxContainer md-checkbox[name=Subject]'
    );
    subjectInput.click();
    assert.notInclude(element.displayedColumns, 'Subject');
  });
});
