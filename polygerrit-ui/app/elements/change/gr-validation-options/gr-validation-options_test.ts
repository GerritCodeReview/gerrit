/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {assert, fixture} from '@open-wc/testing';
import {html} from 'lit';
import './gr-validation-options';
import {GrValidationOptions} from './gr-validation-options';
import {ValidationOptionsInfo} from '../../../api/rest-api';
import {queryAll} from '../../../test/test-utils';
import {MdCheckbox} from '@material/web/checkbox/checkbox';

suite('gr-validation-options tests', () => {
  let element: GrValidationOptions;
  setup(async () => {
    const validationOptions: ValidationOptionsInfo = {
      validation_options: [
        {name: 'o1', description: 'option 1'},
        {name: 'o2', description: 'option 2'},
      ],
    };
    element = await fixture<GrValidationOptions>(
      html`<gr-validation-options
        .validationOptions=${validationOptions}
      ></gr-validation-options>`
    );
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="checkbox-container">
          <md-checkbox class="selectionLabel" id="o1"> </md-checkbox>
          <label for="o1"> Option 1 </label>
        </div>
        <div class="checkbox-container">
          <md-checkbox class="selectionLabel" id="o2"> </md-checkbox>
          <label for="o2"> Option 2 </label>
        </div>
      `
    );
  });

  test('selects and unselects options', async () => {
    const checkboxes = queryAll<MdCheckbox>(element, 'md-checkbox');

    assert.deepEqual(element.getSelectedOptions(), []);

    checkboxes[0].click();
    await element.updateComplete;

    assert.deepEqual(element.getSelectedOptions(), [
      {name: 'o1', description: 'option 1'},
    ]);

    checkboxes[1].click();
    await element.updateComplete;

    assert.deepEqual(element.getSelectedOptions(), [
      {name: 'o1', description: 'option 1'},
      {name: 'o2', description: 'option 2'},
    ]);

    checkboxes[0].click();
    await element.updateComplete;

    assert.deepEqual(element.getSelectedOptions(), [
      {name: 'o2', description: 'option 2'},
    ]);

    checkboxes[1].click();
    await element.updateComplete;

    assert.deepEqual(element.getSelectedOptions(), []);
  });
});
