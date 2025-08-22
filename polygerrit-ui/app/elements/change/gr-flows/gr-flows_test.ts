/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-flows';
import {assert, fixture, html} from '@open-wc/testing';
import {GrFlows} from './gr-flows';
import {FlowInfo, FlowStageState, Timestamp} from '../../../api/rest-api';
import {queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {NumericChangeId} from '../../../types/common';
import {GrCreateFlow} from './gr-create-flow';
import sinon from 'sinon';

suite('gr-flows tests', () => {
  let element: GrFlows;
  let clock: sinon.SinonFakeTimers;

  setup(async () => {
    clock = sinon.useFakeTimers();
    element = await fixture<GrFlows>(html`<gr-flows></gr-flows>`);
    element['changeNum'] = 123 as NumericChangeId;
    await element.updateComplete;
  });

  teardown(() => {
    clock.restore();
  });

  test('renders create flow component and no flows', async () => {
    stubRestApi('listFlows').returns(Promise.resolve([]));
    await element['loadFlows']();
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-create-flow></gr-create-flow>
        <h2 class="main-heading">Existing Flows</h2>
        <p>No flows found for this change.</p>
      `
    );
  });

  test('renders flows', async () => {
    const flows: FlowInfo[] = [
      {
        uuid: 'flow1',
        owner: {name: 'owner1'},
        created: '2025-01-01T10:00:00.000Z' as Timestamp,
        last_evaluated: '2025-01-01T11:00:00.000Z' as Timestamp,
        stages: [
          {
            expression: {condition: 'label:Code-Review=+1'},
            state: FlowStageState.DONE,
          },
        ],
      },
      {
        uuid: 'flow2',
        owner: {name: 'owner2'},
        created: '2025-01-02T10:00:00.000Z' as Timestamp,
        stages: [
          {
            expression: {
              condition: 'label:Verified=+1',
              action: {name: 'submit'},
            },
            state: FlowStageState.PENDING,
          },
        ],
      },
    ];
    stubRestApi('listFlows').returns(Promise.resolve(flows));
    await element['loadFlows']();
    await element.updateComplete;

    const flowElements = element.shadowRoot!.querySelectorAll('.flow');
    assert.lengthOf(flowElements, 2);

    const flow1 = flowElements[0];
    assert.include(flow1.textContent, 'Owner: owner1');
    assert.include(
      flow1.textContent,
      `Created: ${new Date('2025-01-01T10:00:00.000Z').toLocaleString()}`
    );
    assert.include(flow1.textContent, 'Last Evaluated:');
    assert.include(
      flow1.textContent,
      `${new Date('2025-01-01T11:00:00.000Z').toLocaleString()}`
    );
    const stage1 = queryAndAssert(flow1, '.stages-list li');
    assert.equal(
      stage1.textContent!.trim().replace(/\s+/g, ' '),
      '1. label:Code-Review=+1 : DONE'
    );

    const flow2 = flowElements[1];
    assert.include(flow2.textContent, 'Owner: owner2');
    assert.include(
      flow2.textContent,
      `Created: ${new Date('2025-01-02T10:00:00.000Z').toLocaleString()}`
    );
    const stage2 = queryAndAssert(flow2, '.stages-list li');
    assert.equal(
      stage2.textContent!.trim().replace(/\s+/g, ' '),
      '1. label:Verified=+1 -> submit : PENDING'
    );
  });

  test('reloads flows on flow-created event', async () => {
    const listFlowsStub = stubRestApi('listFlows').returns(Promise.resolve([]));
    await element['loadFlows']();
    await element.updateComplete;

    assert.isTrue(listFlowsStub.calledOnce);

    const createFlow = queryAndAssert<GrCreateFlow>(element, 'gr-create-flow');
    createFlow.dispatchEvent(
      new CustomEvent('flow-created', {bubbles: true, composed: true})
    );

    await element.updateComplete;

    assert.isTrue(listFlowsStub.calledTwice);
  });
});
