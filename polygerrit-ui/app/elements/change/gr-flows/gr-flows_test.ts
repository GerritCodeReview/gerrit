/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-flows';
import {assert, fixture, html} from '@open-wc/testing';
import {GrFlows} from './gr-flows';
import {FlowInfo, Timestamp} from '../../../api/rest-api';
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
    await element.updateComplete;
  });

  test('renders initially', async () => {
    stubRestApi('listFlows').returns(Promise.resolve([]));
    await element['loadFlows']();
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div>
          <ul></ul>
        </div>
        <div>
          <input placeholder="Condition" />
          <span> -> </span>
          <input placeholder="Action" />
          <gr-button aria-label="Add Stage">+</gr-button>
        </div>
        <gr-button aria-label="Create Flow"> Create Flow </gr-button>
        <p>No flows found for this change.</p>
      `
    );
  });

  test('renders flows', async () => {
    const flows: FlowInfo[] = [
      {
        uuid: 'flow1',
        owner: {name: 'owner1'},
        created: '2025-01-01' as Timestamp,
        stages: [],
      },
      {
        uuid: 'flow2',
        owner: {name: 'owner2'},
        created: '2025-01-02' as Timestamp,
        stages: [],
      },
    ];
    stubRestApi('listFlows').returns(Promise.resolve(flows));
    await element['loadFlows']();
    await element.updateComplete;

    const flowElements = element.shadowRoot?.querySelectorAll('.flow');
    assert.lengthOf(flowElements!, 2);
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div>
          <ul></ul>
        </div>
        <div>
          <input placeholder="Condition" />
          <span> -> </span>
          <input placeholder="Action" />
          <gr-button aria-label="Add Stage">+ </gr-button>
        </div>
        <gr-button aria-label="Create Flow"> Create Flow </gr-button>
        <div>
          <div class="flow">
            <div class="flow-id">Flow flow1</div>
            <div>Owner: owner1</div>
            <div>Created: 2025-01-01</div>
          </div>
          <div class="flow">
            <div class="flow-id">Flow flow2</div>
            <div>Owner: owner2</div>
            <div>Created: 2025-01-02</div>
          </div>
        </div>
      `
    );
  });

  test('adds and removes stages', async () => {
    await element['loadFlows']();
    await element.updateComplete;

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
    await element['loadFlows']();
    await element.updateComplete;

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
    await element['loadFlows']();
    await element.updateComplete;

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
    await element['loadFlows']();
    await element.updateComplete;

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
