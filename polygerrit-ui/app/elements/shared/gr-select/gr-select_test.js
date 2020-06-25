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

const basicFixture = fixtureFromTemplate(html`
<gr-select>
      <select>
        <option value="1">One</option>
        <option value="2">Two</option>
        <option value="3">Three</option>
      </select>
    </gr-select>
`);

const noOptionsFixture = fixtureFromTemplate(html`
<gr-select>
      <select>
      </select>
    </gr-select>
`);

import '../../../test/common-test-setup-karma.js';
import './gr-select.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

suite('gr-select tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('bindValue must be set to the first option value', () => {
    assert.equal(element.bindValue, '1');
  });

  test('value of 0 should still trigger value updates', () => {
    element.bindValue = 0;
    assert.equal(element.nativeSelect.value, 0);
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
          composed: true, bubbles: true,
        }));

    // It should be updated.
    assert.equal(element.nativeSelect.value, '3');
    assert.equal(element.bindValue, '3');
    assert.isTrue(changeStub.called);
  });

  suite('gr-select no options tests', () => {
    let element;

    setup(() => {
      element = noOptionsFixture.instantiate();
    });

    test('bindValue must not be changed', () => {
      assert.isUndefined(element.bindValue);
    });
  });
});

