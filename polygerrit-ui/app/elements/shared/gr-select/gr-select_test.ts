/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import './gr-select';
import {fixture, html} from '@open-wc/testing-helpers';
import {GrSelect} from './gr-select';

suite('gr-select tests', () => {
  let element: GrSelect;

  setup(async () => {
    element = await fixture<GrSelect>(html`
      <gr-select>
        <select>
          <option value="1">One</option>
          <option value="2">Two</option>
          <option value="3">Three</option>
        </select>
      </gr-select>
    `);
  });

  test('bindValue must be set to the first option value', () => {
    assert.equal(element.bindValue, '1');
    assert.equal(element.nativeSelect.value, '1');
  });

  test('value of 0 should still trigger value updates', () => {
    element.bindValue = '0';
    assert.equal(element.nativeSelect.value, '');
  });

  test('bidirectional binding property-to-attribute', () => {
    const changeStub = sinon.stub();
    element.addEventListener('bind-value-changed', changeStub);

    // The selected element should be the first one by default.
    assert.equal(element.nativeSelect.value, '1');
    assert.equal(element.bindValue, '1');
    assert.isFalse(changeStub.called);

    // Now change the value.
    element.bindValue = '2';

    // It should be updated.
    assert.equal(element.nativeSelect.value, '2');
    assert.equal(element.bindValue, '2');
    assert.isTrue(changeStub.called);
  });

  test('bidirectional binding attribute-to-property', () => {
    const changeStub = sinon.stub();
    element.addEventListener('bind-value-changed', changeStub);

    // The selected element should be the first one by default.
    assert.equal(element.nativeSelect.value, '1');
    assert.equal(element.bindValue, '1');
    assert.isFalse(changeStub.called);

    // Now change the value.
    element.nativeSelect.value = '3';
    element.dispatchEvent(
      new CustomEvent('change', {
        composed: true,
        bubbles: true,
      })
    );

    // It should be updated.
    assert.equal(element.nativeSelect.value, '3');
    assert.equal(element.bindValue, '3');
    assert.isTrue(changeStub.called);
  });

  suite('gr-select no options tests', () => {
    let element: GrSelect;

    setup(async () => {
      element = await fixture<GrSelect>(html`
        <gr-select>
          <select></select>
        </gr-select>
      `);
    });

    test('bindValue must not be changed', () => {
      assert.isUndefined(element.bindValue);
    });
  });
});
