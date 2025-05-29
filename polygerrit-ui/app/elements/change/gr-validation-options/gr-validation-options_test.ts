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

suite('gr-trigger-vote tests', () => {
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
        <label class="selectionLabel">
          <input type="checkbox" />
          Option 1
        </label>
        <label class="selectionLabel">
          <input type="checkbox" />
          Option 2
        </label>
      `
    );
  });

  test('selects and unselects options', () => {
    const checkboxes = queryAll<HTMLInputElement>(
      element,
      'input[type="checkbox"]'
    );
    element.validationOptions?.validation_options;

    assert.deepEqual(element.getSelectedOptions(), []);

    checkboxes[0].click();

    assert.deepEqual(element.getSelectedOptions(), [
      {name: 'o1', description: 'option 1'},
    ]);

    checkboxes[1].click();

    assert.deepEqual(element.getSelectedOptions(), [
      {name: 'o1', description: 'option 1'},
      {name: 'o2', description: 'option 2'},
    ]);

    checkboxes[0].click();

    assert.deepEqual(element.getSelectedOptions(), [
      {name: 'o2', description: 'option 2'},
    ]);

    checkboxes[1].click();

    assert.deepEqual(element.getSelectedOptions(), []);
  });
});
