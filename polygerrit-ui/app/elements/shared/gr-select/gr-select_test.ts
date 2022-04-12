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
    await element.updateComplete;
    await element.updateComplete;
  });

  test('bindValue must be set to the first option value', async () => {
    assert.strictEqual(element.bindValue, '1');
    assert.strictEqual(element.nativeSelect?.value, '1');
  });

  test('value of 0 should still trigger value updates', async () => {
    element.bindValue = '0';
    await element.updateComplete;

    assert.strictEqual(element.nativeSelect?.value, '');
  });

  test('bidirectional binding bindValue-to-nativeSelect', async () => {
    const changeStub = sinon.stub();
    element.addEventListener('bind-value-changed', changeStub);

    // The selected element should be the first one by default.
    assert.strictEqual(element.nativeSelect?.value, '1');
    assert.strictEqual(element.bindValue, '1');
    assert.isFalse(changeStub.called);

    // Now change the value.
    element.bindValue = '2';
    await element.updateComplete;

    // It should be updated.
    assert.strictEqual(element.nativeSelect?.value, '2');
    assert.strictEqual(element.bindValue, '2');
    assert.isTrue(changeStub.called);
  });

  test('string binding', async () => {
    const changeStub = sinon.stub();
    element.addEventListener('bind-value-changed', changeStub);

    // The selected element should be the first one by default.
    assert.strictEqual(element.nativeSelect?.value, '1');
    assert.strictEqual(element.bindValue, '1');
    assert.isFalse(changeStub.called);

    // Now change the value.
    element.nativeSelect!.value = '3';
    element.dispatchEvent(
      new CustomEvent('change', {
        composed: true,
        bubbles: true,
      })
    );
    await element.updateComplete;

    // It should be updated.
    assert.strictEqual(element.nativeSelect?.value, '3');
    assert.strictEqual(element.bindValue, '3');
    assert.isTrue(changeStub.called);
  });

  test('boolean binding', async () => {
    const booleanElement = await fixture<GrSelect>(
      html`<gr-select .bindValue=${true}
        ><select>
          <option value="true">t</option>
          <option value="false">f</option>
        </select></gr-select
      >`
    );

    const changeStub = sinon.stub();
    booleanElement.addEventListener('bind-value-changed', changeStub);

    // The native element will be stringified
    assert.strictEqual(booleanElement.nativeSelect?.value, 'true');
    assert.strictEqual(booleanElement.bindValue, true);
    assert.isFalse(changeStub.called);

    // Now change the value.
    booleanElement.nativeSelect!.value = 'false';
    booleanElement.dispatchEvent(
      new CustomEvent('change', {
        composed: true,
        bubbles: true,
      })
    );
    await booleanElement.updateComplete;

    // It should be updated, bindValue will be a boolean to match
    assert.strictEqual(booleanElement.nativeSelect?.value, 'false');
    assert.strictEqual(booleanElement.bindValue, false);
    assert.strictEqual(changeStub.firstCall.args[0].detail.value, false);
  });

  test('number binding', async () => {
    const numberElement = await fixture<GrSelect>(
      html`<gr-select .bindValue=${1}
        ><select>
          <option value="1">1</option>
          <option value="2">2</option>
          <option value="3">3</option>
        </select></gr-select
      >`
    );

    const changeStub = sinon.stub();
    numberElement.addEventListener('bind-value-changed', changeStub);

    // The native element will be stringified
    assert.strictEqual(numberElement.nativeSelect?.value, '1');
    assert.strictEqual(numberElement.bindValue, 1);
    assert.isFalse(changeStub.called);

    // Now change the value.
    numberElement.nativeSelect!.value = '2';
    numberElement.dispatchEvent(
      new CustomEvent('change', {
        composed: true,
        bubbles: true,
      })
    );
    await numberElement.updateComplete;

    // It should be updated, bindValue will be a number to match
    assert.strictEqual(numberElement.nativeSelect?.value, '2');
    assert.strictEqual(numberElement.bindValue, 2);
    assert.strictEqual(changeStub.firstCall.args[0].detail.value, 2);
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

    test('bindValue must not be changed', async () => {
      assert.isUndefined(element.bindValue);
    });
  });
});
