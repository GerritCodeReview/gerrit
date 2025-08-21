/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-create-flow';
import {assert, fixture, html} from '@open-wc/testing';
import {GrCreateFlow} from './gr-create-flow';
import {
  mockPromise,
  queryAll,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {NumericChangeId} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';

suite('gr-create-flow tests', () => {
  let element: GrCreateFlow;

  setup(async () => {
    element = await fixture<GrCreateFlow>(
      html`<gr-create-flow></gr-create-flow>`
    );
    element.changeNum = 123 as NumericChangeId;
    await element.updateComplete;
  });

  test('renders initially', () => {
    assert.isDefined(queryAndAssert(element, 'input[placeholder="Condition"]'));
    assert.isDefined(queryAndAssert(element, 'input[placeholder="Action"]'));
    assert.isDefined(
      queryAndAssert(element, 'gr-button[aria-label="Add Stage"]')
    );
    assert.isDefined(
      queryAndAssert(element, 'gr-button[aria-label="Create Flow"]')
    );
  });

  test('adds and removes stages', async () => {
    const inputs = queryAll<HTMLInputElement>(element, 'input');
    const conditionInput = inputs[0];
    const actionInput = inputs[1];
    const addButton = queryAndAssert<GrButton>(
      element,
      'gr-button[aria-label="Add Stage"]'
    );

    conditionInput.value = 'cond 1';
    conditionInput.dispatchEvent(new Event('input'));
    actionInput.value = 'act 1';
    actionInput.dispatchEvent(new Event('input'));
    await element.updateComplete;
    addButton.click();
    await element.updateComplete;

    assert.deepEqual(element['stages'], [
      {condition: 'cond 1', action: 'act 1'},
    ]);
    assert.equal(element['currentCondition'], '');
    assert.equal(element['currentAction'], '');

    conditionInput.value = 'cond 2';
    conditionInput.dispatchEvent(new Event('input'));
    actionInput.value = 'act 2';
    actionInput.dispatchEvent(new Event('input'));
    await element.updateComplete;
    addButton.click();
    await element.updateComplete;

    assert.deepEqual(element['stages'], [
      {condition: 'cond 1', action: 'act 1'},
      {condition: 'cond 2', action: 'act 2'},
    ]);

    let removeButtons = queryAll<GrButton>(element, 'li gr-button');
    assert.lengthOf(removeButtons, 2);

    removeButtons[0].click();
    await element.updateComplete;

    assert.deepEqual(element['stages'], [
      {condition: 'cond 2', action: 'act 2'},
    ]);
    removeButtons = queryAll<GrButton>(element, 'li gr-button');
    assert.lengthOf(removeButtons, 1);
  });

  test('creates a flow with one stage', async () => {
    const createFlowStub = stubRestApi('createFlow').returns(mockPromise());

    const inputs = queryAll<HTMLInputElement>(element, 'input');
    const conditionInput = inputs[0];
    const actionInput = inputs[1];
    conditionInput.value = 'single condition';
    conditionInput.dispatchEvent(new Event('input'));
    actionInput.value = 'single action';
    actionInput.dispatchEvent(new Event('input'));
    await element.updateComplete;

    const createButton = queryAndAssert<GrButton>(
      element,
      'gr-button[aria-label="Create Flow"]'
    );
    createButton.click();
    await element.updateComplete;

    assert.isTrue(createFlowStub.calledOnce);
    const flowInput = createFlowStub.lastCall.args[1];
    assert.deepEqual(flowInput.stage_expressions, [
      {condition: 'single condition', action: {name: 'single action'}},
    ]);
  });

  test('creates a flow with multiple stages', async () => {
    const createFlowStub = stubRestApi('createFlow').returns(mockPromise());

    const inputs = queryAll<HTMLInputElement>(element, 'input');
    const conditionInput = inputs[0];
    const actionInput = inputs[1];
    const addButton = queryAndAssert<GrButton>(
      element,
      'gr-button[aria-label="Add Stage"]'
    );

    conditionInput.value = 'cond 1';
    conditionInput.dispatchEvent(new Event('input'));
    actionInput.value = 'act 1';
    actionInput.dispatchEvent(new Event('input'));
    await element.updateComplete;
    addButton.click();
    await element.updateComplete;

    conditionInput.value = 'cond 2';
    conditionInput.dispatchEvent(new Event('input'));
    actionInput.value = 'act 2';
    actionInput.dispatchEvent(new Event('input'));
    await element.updateComplete;
    addButton.click();
    await element.updateComplete;

    const createButton = queryAndAssert<GrButton>(
      element,
      'gr-button[aria-label="Create Flow"]'
    );
    createButton.click();
    await element.updateComplete;

    assert.isTrue(createFlowStub.calledOnce);
    const flowInput = createFlowStub.lastCall.args[1];
    assert.deepEqual(flowInput.stage_expressions, [
      {condition: 'cond 1', action: {name: 'act 1'}},
      {condition: 'cond 2', action: {name: 'act 2'}},
    ]);
  });

  test('create flow with added stages and current input', async () => {
    const createFlowStub = stubRestApi('createFlow').returns(mockPromise());

    const inputs = queryAll<HTMLInputElement>(element, 'input');
    const conditionInput = inputs[0];
    const actionInput = inputs[1];
    const addButton = queryAndAssert<GrButton>(
      element,
      'gr-button[aria-label="Add Stage"]'
    );

    conditionInput.value = 'cond 1';
    conditionInput.dispatchEvent(new Event('input'));
    actionInput.value = 'act 1';
    actionInput.dispatchEvent(new Event('input'));
    await element.updateComplete;
    addButton.click();
    await element.updateComplete;

    conditionInput.value = 'cond 2';
    conditionInput.dispatchEvent(new Event('input'));
    actionInput.value = 'act 2';
    actionInput.dispatchEvent(new Event('input'));
    await element.updateComplete;

    const createButton = queryAndAssert<GrButton>(
      element,
      'gr-button[aria-label="Create Flow"]'
    );
    createButton.click();
    await element.updateComplete;

    assert.isTrue(createFlowStub.calledOnce);
    const flowInput = createFlowStub.lastCall.args[1];
    assert.deepEqual(flowInput.stage_expressions, [
      {condition: 'cond 1', action: {name: 'act 1'}},
      {condition: 'cond 2', action: {name: 'act 2'}},
    ]);
  });
});
