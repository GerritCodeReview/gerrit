/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import './gr-label-score-row';
import {GrLabelScoreRow} from './gr-label-score-row';
import {AccountId} from '../../../api/rest-api';
import {GrButton} from '../../shared/gr-button/gr-button';

const basicFixture = fixtureFromElement('gr-label-score-row');

suite('gr-label-row-score tests', () => {
  let element: GrLabelScoreRow;

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
        all: [
          {
            _account_id: 123 as unknown as AccountId,
            value: 1,
          },
        ],
      },
      Verified: {
        values: {
          '0': 'No score',
          '+1': 'good',
          '+2': 'excellent',
          '-1': 'bad',
          '-2': 'terrible',
        },
        default_value: 0,
        value: 1,
        all: [
          {
            _account_id: 123 as unknown as AccountId,
            value: 1,
          },
        ],
      },
    };

    element.permittedLabels = {
      'Code-Review': ['-2', '-1', ' 0', '+1', '+2'],
      Verified: ['-1', ' 0', '+1'],
    };

    element.orderedLabelValues = [-2, -1, 0, 1, 2];
    //  {'0': 2, '1': 3, '2': 4, '-2': 0, '-1': 1};

    element.label = {
      name: 'Verified',
      value: '+1',
    };

    await element.updateComplete;
    await flush();
  });

  function checkAriaCheckedValid() {
    const items = element.labelSelector!.items;
    assert.ok(items);
    const selectedItem = element.selectedItem;
    for (let i = 0; i < items!.length; i++) {
      const item = items![i];
      if (items![i] === selectedItem) {
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
    assert.ok(element.labelSelector);
    const button = element.shadowRoot!.querySelector(
      'gr-button[data-value="-1"]'
    ) as GrButton;
    button.click();
    await element.updateComplete;

    assert.strictEqual(element.selectedValue, '-1');
    assert.strictEqual(element.selectedItem!.textContent!.trim(), '-1');
    const selectedValueLabel = element.shadowRoot!.querySelector(
      '#selectedValueLabel'
    );
    assert.strictEqual(selectedValueLabel!.textContent!.trim(), 'bad');
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
      element._computeVoteAttribute(value, index, totalItems),
      'positive'
    );
    // negative and first position
    value = -1;
    assert.equal(
      element._computeVoteAttribute(value, index, totalItems),
      'min'
    );
    // negative but not first position
    index = 1;
    assert.equal(
      element._computeVoteAttribute(value, index, totalItems),
      'negative'
    );
    // neutral
    value = 0;
    assert.equal(
      element._computeVoteAttribute(value, index, totalItems),
      'neutral'
    );
    // positive but not last position
    value = 1;
    assert.equal(
      element._computeVoteAttribute(value, index, totalItems),
      'positive'
    );
    // positive and last position
    index = 4;
    assert.equal(
      element._computeVoteAttribute(value, index, totalItems),
      'max'
    );
    // negative and last position
    value = -1;
    assert.equal(
      element._computeVoteAttribute(value, index, totalItems),
      'negative'
    );
  });

  test('correct item is selected', () => {
    // 1 should be the value of the selected item
    assert.strictEqual(element.labelSelector!.selected, '+1');
    assert.strictEqual(element.selectedItem!.textContent!.trim(), '+1');
    const selectedValueLabel = element.shadowRoot!.querySelector(
      '#selectedValueLabel'
    );
    assert.strictEqual(selectedValueLabel!.textContent!.trim(), 'good');
    checkAriaCheckedValid();
  });

  test('_computeLabelValue', () => {
    assert.strictEqual(element._computeLabelValue(), '+1');
  });

  test('computeBlankItemsCount', () => {
    element.orderedLabelValues = [-2, -1, 0, 1, 2];
    element.label = {name: 'Code-Review', value: ' 0'};
    assert.strictEqual(element.computeBlankItemsCount('start'), 0);

    element.label = {name: 'Verified', value: ' 0'};
    assert.strictEqual(element.computeBlankItemsCount('start'), 1);
  });

  test('labelValues returns no keys', () => {
    element.orderedLabelValues = [];
    element.label = {name: 'Code-Review', value: ' 0'};

    assert.deepEqual(element.computeBlankItemsCount('start'), 0);
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
      Verified: {
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
    // For some reason we need the element.labels to flush first before we
    // change the element.label
    await element.updateComplete;

    element.label = {name: 'Verified', value: ' 0'};
    await element.updateComplete;
    // Wait for @selected-item-changed to fire
    await flush();

    const selector = element.labelSelector;
    assert.strictEqual(selector!.selected, ' 0');
    const selectedValueLabel = element.shadowRoot!.querySelector(
      '#selectedValueLabel'
    );
    assert.strictEqual(selectedValueLabel!.textContent!.trim(), 'No score');
    checkAriaCheckedValid();
  });

  test('asymmetrical labels', async () => {
    element.permittedLabels = {
      'Code-Review': ['-2', '-1', ' 0', '+1', '+2'],
      Verified: [' 0', '+1'],
    };
    await element.updateComplete;
    assert.strictEqual(element.labelSelector!.items!.length, 2);
    assert.strictEqual(
      element.shadowRoot!.querySelectorAll('.placeholder').length,
      3
    );

    element.permittedLabels = {
      'Code-Review': [' 0', '+1'],
      Verified: ['-2', '-1', ' 0', '+1', '+2'],
    };
    await element.updateComplete;
    assert.strictEqual(element.labelSelector!.items!.length, 5);
    assert.strictEqual(
      element.shadowRoot!.querySelectorAll('.placeholder').length,
      0
    );
  });

  test('default_value', async () => {
    element.permittedLabels = {
      Verified: ['-1', ' 0', '+1'],
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
    await element.updateComplete;
    assert.strictEqual(element.selectedValue, '-1');
    checkAriaCheckedValid();
  });

  test('default_value is null if not permitted', async () => {
    element.permittedLabels = {
      Verified: ['-1', ' 0', '+1'],
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
    await element.updateComplete;
    assert.isNull(element.selectedValue);
  });

  test('shadowDom test', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <span class="labelNameCell" id="labelName" aria-hidden="true">
        Verified
      </span>
      <div class="buttonsCell">
        <span class="placeholder" data-label="Verified"></span>
        <iron-selector
          aria-labelledby="labelName"
          id="labelSelector"
          role="radiogroup"
          selected="+1"
        >
          <gr-button
            aria-disabled="false"
            aria-label="-1"
            data-name="Verified"
            data-value="-1"
            role="radio"
            tabindex="0"
            title="bad"
            data-vote="min"
            votechip=""
            flatten=""
          >
            <gr-tooltip-content light-tooltip="" has-tooltip="" title="bad">
              -1
            </gr-tooltip-content>
          </gr-button>
          <gr-button
            aria-disabled="false"
            aria-label=" 0"
            data-name="Verified"
            data-value=" 0"
            role="radio"
            tabindex="0"
            data-vote="neutral"
            votechip=""
            flatten=""
          >
            <gr-tooltip-content light-tooltip="" has-tooltip="">
              0
            </gr-tooltip-content>
          </gr-button>
          <gr-button
            aria-checked="true"
            aria-disabled="false"
            aria-label="+1"
            class="iron-selected"
            data-name="Verified"
            data-value="+1"
            role="radio"
            tabindex="0"
            title="good"
            data-vote="max"
            votechip=""
            flatten=""
          >
            <gr-tooltip-content light-tooltip="" has-tooltip="" title="good">
              +1
            </gr-tooltip-content>
          </gr-button>
        </iron-selector>
        <span class="placeholder" data-label="Verified"></span>
      </div>
      <div class="selectedValueCell ">
        <span id="selectedValueLabel">good</span>
      </div>
    `);
  });
});
