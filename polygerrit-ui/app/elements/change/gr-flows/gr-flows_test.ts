/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-flows';
import {assert, fixture, html} from '@open-wc/testing';
import {GrFlows} from './gr-flows';
import {FlowInfo, FlowInput} from '../../../api/rest-api';
import {
  mockPromise,
  queryAll,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {NumericChangeId} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';

suite('gr-flows tests', () => {
  let element: GrFlows;

  setup(async () => {
    element = await fixture<GrFlows>(html`<gr-flows></gr-flows>`);
    element['changeNum'] = 123 as NumericChangeId;
    stubRestApi('listFlows').returns(Promise.resolve([]));
    await element.updateComplete;
  });

  test('renders initially', async () => {
    await element['loadFlows']();
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div>
          <ul></ul>
        </div>
        <div>
          <input />
          <gr-button aria-label="Add Stage">+</gr-button>
        </div>
        <gr-button aria-label="Create Flow"> Create Flow </gr-button>
        <p>No flows found for this change.</p>
      `
    );
  });

  test('adds and removes stages', async () => {
    await element['loadFlows']();
    await element.updateComplete;

    const input = queryAndAssert<HTMLInputElement>(element, 'input');
    const addButton = queryAndAssert<GrButton>(
      element,
      'gr-button[aria-label="Add Stage"]'
    );

    input.value = 'stage 1';
    input.dispatchEvent(new Event('input'));
    await element.updateComplete;
    addButton.click();
    await element.updateComplete;

    assert.deepEqual(element['stageExpressions'], ['stage 1']);
    assert.equal(element['currentStageExpression'], '');

    input.value = 'stage 2';
    input.dispatchEvent(new Event('input'));
    await element.updateComplete;
    addButton.click();
    await element.updateComplete;

    assert.deepEqual(element['stageExpressions'], ['stage 1', 'stage 2']);

    let removeButtons = queryAll<GrButton>(element, 'li gr-button');
    assert.lengthOf(removeButtons, 2);

    removeButtons[0].click();
    await element.updateComplete;

    assert.deepEqual(element['stageExpressions'], ['stage 2']);
    removeButtons = queryAll<GrButton>(element, 'li gr-button');
    assert.lengthOf(removeButtons, 1);
  });

  test('creates a flow with one stage', async () => {
    const createFlowStub = stubRestApi('createFlow').returns(mockPromise());
    await element['loadFlows']();
    await element.updateComplete;

    const input = queryAndAssert<HTMLInputElement>(element, 'input');
    input.value = 'single stage';
    input.dispatchEvent(new Event('input'));
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
      {condition: 'single stage'},
    ]);
  });

  test('creates a flow with multiple stages', async () => {
    const createFlowStub = stubRestApi('createFlow').returns(mockPromise());
    await element['loadFlows']();
    await element.updateComplete;

    const input = queryAndAssert<HTMLInputElement>(element, 'input');
    const addButton = queryAndAssert<GrButton>(
      element,
      'gr-button[aria-label="Add Stage"]'
    );

    input.value = 'stage 1';
    input.dispatchEvent(new Event('input'));
    await element.updateComplete;
    addButton.click();
    await element.updateComplete;

    input.value = 'stage 2';
    input.dispatchEvent(new Event('input'));
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
      {condition: 'stage 1'},
      {condition: 'stage 2'},
    ]);
  });

  test('create flow with added stages and current input', async () => {
    const createFlowStub = stubRestApi('createFlow').returns(mockPromise());
    await element['loadFlows']();
    await element.updateComplete;

    const input = queryAndAssert<HTMLInputElement>(element, 'input');
    const addButton = queryAndAssert<GrButton>(
      element,
      'gr-button[aria-label="Add Stage"]'
    );

    input.value = 'stage 1';
    input.dispatchEvent(new Event('input'));
    await element.updateComplete;
    addButton.click();
    await element.updateComplete;

    input.value = 'stage 2';
    input.dispatchEvent(new Event('input'));
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
      {condition: 'stage 1'},
      {condition: 'stage 2'},
    ]);
  });
});
