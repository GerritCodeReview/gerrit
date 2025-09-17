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
        <md-checkbox class="selectionLabel">Option 1</md-checkbox>
        <md-checkbox class="selectionLabel">Option 2</md-checkbox>
      `
    );
  });

  test('selects and unselects options', () => {
    const checkboxes = queryAll<MdCheckbox>(element, 'md-checkbox');
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
