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
import {GrButton} from '../../shared/gr-button/gr-button';

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
        <div class="container">
          <h2 class="main-heading">Create new flow</h2>
          <gr-create-flow></gr-create-flow>
          <hr />
          <p>No flows found for this change.</p>
        </div>
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

    // prettier formats the spacing for "last evaluated" incorrectly
    assert.shadowDom.equal(
      element,
      /* prettier-ignore */ /* HTML */ `
        <div class="container">
          <h2 class="main-heading">Create new flow</h2>
          <gr-create-flow></gr-create-flow>
          <hr />
          <div>
            <h2 class="main-heading">Existing Flows</h2>
            <div class="flow">
              <div class="flow-header">
                <gr-button link title="Delete flow">
                  <gr-icon icon="delete" filled></gr-icon>
                </gr-button>
              </div>
              <div class="flow-id hidden">Flow flow1</div>
              <div>
                Created:
                <gr-date-formatter withtooltip></gr-date-formatter>
              </div>
              <div>
                Last Evaluated:
                <gr-date-formatter withtooltip></gr-date-formatter>
              </div>
              <div class="stages-list">
                <h4>Stages</h4>
                <ul>
                  <li>
                    <gr-icon
                      class="done"
                      icon="check_circle"
                      filled
                      aria-label="done"
                      role="img"
                    ></gr-icon>
                    <span>1. </span>
                    <span>label:Code-Review=+1</span>
                  </li>
                </ul>
              </div>
            </div>
            <div class="flow">
              <div class="flow-header">
                <gr-button link title="Delete flow">
                  <gr-icon icon="delete" filled></gr-icon>
                </gr-button>
              </div>
              <div class="flow-id hidden">Flow flow2</div>
              <div>
                Created:
                <gr-date-formatter withtooltip></gr-date-formatter>
              </div>
              <div class="stages-list">
                <h4>Stages</h4>
                <ul>
                  <li>
                    <gr-icon
                      class="pending"
                      icon="timelapse"
                      aria-label="pending"
                      role="img"
                    ></gr-icon>
                    <span>1. </span>
                    <span>label:Verified=+1</span>
                    <span> -> submit</span>
                  </li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      `,
      {
        ignoreAttributes: [
          'style',
          'class',
          'account',
          'changenum',
          'datestr',
          'aria-disabled',
          'role',
          'tabindex',
        ],
      }
    );
  });

  test('deletes a flow', async () => {
    const flows: FlowInfo[] = [
      {
        uuid: 'flow1',
        owner: {name: 'owner1'},
        created: '2025-01-01T10:00:00.000Z' as Timestamp,
        stages: [
          {
            expression: {condition: 'label:Code-Review=+1'},
            state: FlowStageState.DONE,
          },
        ],
      },
    ];
    stubRestApi('listFlows').returns(Promise.resolve(flows));
    const deleteFlowStub = stubRestApi('deleteFlow').returns(
      Promise.resolve(new Response())
    );
    await element['loadFlows']();
    await element.updateComplete;

    const deleteButton = queryAndAssert<GrButton>(element, '.flow gr-button');
    deleteButton.click();

    await element.updateComplete;

    assert.isTrue(deleteFlowStub.calledOnceWith(123, 'flow1'));
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
