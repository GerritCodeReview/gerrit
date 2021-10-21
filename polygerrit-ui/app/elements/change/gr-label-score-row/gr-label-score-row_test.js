/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import './gr-label-score-row.js';

const basicFixture = fixtureFromElement('gr-label-score-row');

suite('gr-label-row-score tests', () => {
  let element;

  setup(async () => {
    element = basicFixture.instantiate();
    element.labels = {
      'Code-Review': {
        values: {
          '0': 'No score',
          '+1': 'good',
          '+2': 'excellent',
          '-1': 'bad',
          '-2': 'terrible',
        },
        default_value: 0,
        value: 1,
        all: [{
          _account_id: 123,
          value: 1,
        }],
      },
      'Verified': {
        values: {
          '0': 'No score',
          '+1': 'good',
          '+2': 'excellent',
          '-1': 'bad',
          '-2': 'terrible',
        },
        default_value: 0,
        value: 1,
        all: [{
          _account_id: 123,
          value: 1,
        }],
      },
    };

    element.permittedLabels = {
      'Code-Review': [
        '-2',
        '-1',
        ' 0',
        '+1',
        '+2',
      ],
      'Verified': [
        '-1',
        ' 0',
        '+1',
      ],
    };

    element.labelValues = {'0': 2, '1': 3, '2': 4, '-2': 0, '-1': 1};

    element.label = {
      name: 'Verified',
      value: '+1',
    };

    await flush();
  });

  function checkAriaCheckedValid() {
    const items = element.$.labelSelector.items;
    const selectedItem = element.selectedItem;
    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      if (items[i] === selectedItem) {
        assert.isTrue(item.hasAttribute('aria-checked'), `item ${i}`);
        assert.equal(item.getAttribute('aria-checked'), 'true', `item ${i}`);
      } else {
        assert.isFalse(item.hasAttribute('aria-checked'), `item ${i}`);
      }
    }
  }

  test('label picker', async () => {
    const labelsChangedHandler = sinon.stub();
    element.addEventListener('labels-changed', labelsChangedHandler);
    assert.ok(element.$.labelSelector);
    MockInteractions.tap(
        element.shadowRoot.querySelector('gr-button[data-value="-1"]'));
    await flush();
    assert.strictEqual(element.selectedValue, '-1');
    assert.strictEqual(element.selectedItem.textContent.trim(), '-1');
    assert.strictEqual(element.$.selectedValueLabel.textContent.trim(), 'bad');
    const detail = labelsChangedHandler.args[0][0].detail;
    assert.equal(detail.name, 'Verified');
    assert.equal(detail.value, '-1');
    checkAriaCheckedValid();
  });

  test('_computeVoteAttribute', () => {
    let value = 1;
    let index = 0;
    const totalItems = 5;
    // positive and first position
    assert.equal(
        element._computeVoteAttribute(value, index, totalItems), 'positive');
    // negative and first position
    value = -1;
    assert.equal(
        element._computeVoteAttribute(value, index, totalItems), 'min');
    // negative but not first position
    index = 1;
    assert.equal(
        element._computeVoteAttribute(value, index, totalItems), 'negative');
    // neutral
    value = 0;
    assert.equal(
        element._computeVoteAttribute(value, index, totalItems), 'neutral');
    // positive but not last position
    value = 1;
    assert.equal(
        element._computeVoteAttribute(value, index, totalItems), 'positive');
    // positive and last position
    index = 4;
    assert.equal(
        element._computeVoteAttribute(value, index, totalItems), 'max');
    // negative and last position
    value = -1;
    assert.equal(
        element._computeVoteAttribute(value, index, totalItems), 'negative');
  });

  test('correct item is selected', () => {
    // 1 should be the value of the selected item
    assert.strictEqual(element.$.labelSelector.selected, '+1');
    assert.strictEqual(
        element.$.labelSelector.selectedItem.textContent.trim(), '+1');
    assert.strictEqual(element.$.selectedValueLabel.textContent.trim(), 'good');
    checkAriaCheckedValid();
  });

  test('_computeLabelValue', () => {
    assert.strictEqual(
        element._computeLabelValue(
            element.labels, element.permittedLabels, element.label),
        '+1');
  });

  test('_computeBlankItems', () => {
    element.labelValues = {
      '-2': 0,
      '-1': 1,
      '0': 2,
      '1': 3,
      '2': 4,
    };

    assert.strictEqual(
        element._computeBlankItems(element.permittedLabels, 'Code-Review')
            .length,
        0);

    assert.strictEqual(
        element._computeBlankItems(element.permittedLabels, 'Verified').length,
        1);
  });

  test('labelValues returns no keys', () => {
    element.labelValues = {};

    assert.deepEqual(
        element._computeBlankItems(element.permittedLabels, 'Code-Review'), []);
  });

  test('changes in label score are reflected in the DOM', async () => {
    element.labels = {
      'Code-Review': {
        values: {
          '0': 'No score',
          '+1': 'good',
          '+2': 'excellent',
          '-1': 'bad',
          '-2': 'terrible',
        },
        default_value: 0,
      },
      'Verified': {
        values: {
          ' 0': 'No score',
          '+1': 'good',
          '+2': 'excellent',
          '-1': 'bad',
          '-2': 'terrible',
        },
        default_value: 0,
      },
    };
    await flush();
    const selector = element.$.labelSelector;
    element.set('label', {name: 'Verified', value: ' 0'});
    await flush();
    assert.strictEqual(selector.selected, ' 0');
    assert.strictEqual(
        element.$.selectedValueLabel.textContent.trim(), 'No score');
    checkAriaCheckedValid();
  });

  test('without permitted labels', async () => {
    element.permittedLabels = {
      Verified: [
        '-1',
        ' 0',
        '+1',
      ],
    };
    await flush();
    assert.isOk(element.$.labelSelector);
    assert.isFalse(element.$.labelSelector.hidden);

    element.permittedLabels = {};
    await flush();
    assert.isOk(element.$.labelSelector);
    assert.isTrue(element.$.labelSelector.hidden);

    element.permittedLabels = {Verified: []};
    await flush();
    assert.isOk(element.$.labelSelector);
    assert.isTrue(element.$.labelSelector.hidden);
  });

  test('asymmetrical labels', async () => {
    element.permittedLabels = {
      'Code-Review': [
        '-2',
        '-1',
        ' 0',
        '+1',
        '+2',
      ],
      'Verified': [
        ' 0',
        '+1',
      ],
    };
    await flush();
    assert.strictEqual(element.$.labelSelector.items.length, 2);
    assert.strictEqual(element.root.querySelectorAll('.placeholder').length, 3);

    element.permittedLabels = {
      'Code-Review': [
        ' 0',
        '+1',
      ],
      'Verified': [
        '-2',
        '-1',
        ' 0',
        '+1',
        '+2',
      ],
    };
    await flush();
    assert.strictEqual(element.$.labelSelector.items.length, 5);
    assert.strictEqual(element.root.querySelectorAll('.placeholder').length, 0);
  });

  test('default_value', () => {
    element.permittedLabels = {
      Verified: [
        '-1',
        ' 0',
        '+1',
      ],
    };
    element.labels = {
      Verified: {
        values: {
          '0': 'No score',
          '+1': 'good',
          '+2': 'excellent',
          '-1': 'bad',
          '-2': 'terrible',
        },
        default_value: -1,
      },
    };
    element.label = {
      name: 'Verified',
      value: null,
    };
    flush();
    assert.strictEqual(element.selectedValue, '-1');
    checkAriaCheckedValid();
  });

  test('default_value is null if not permitted', () => {
    element.permittedLabels = {
      Verified: [
        '-1',
        ' 0',
        '+1',
      ],
    };
    element.labels = {
      'Code-Review': {
        values: {
          '0': 'No score',
          '+1': 'good',
          '+2': 'excellent',
          '-1': 'bad',
          '-2': 'terrible',
        },
        default_value: -1,
      },
    };
    element.label = {
      name: 'Code-Review',
      value: null,
    };
    flush();
    assert.isNull(element.selectedValue);
  });
});
