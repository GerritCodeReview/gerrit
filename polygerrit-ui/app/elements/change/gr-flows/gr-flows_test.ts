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
import {queryAndAssert} from '../../../test/test-utils';
import {NumericChangeId} from '../../../types/common';
import sinon from 'sinon';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {FlowsModel, flowsModelToken} from '../../../models/flows/flows-model';
import {testResolver} from '../../../test/common-test-setup';

suite('gr-flows tests', () => {
  let element: GrFlows;
  let clock: sinon.SinonFakeTimers;
  let flowsModel: FlowsModel;

  setup(async () => {
    clock = sinon.useFakeTimers();

    flowsModel = testResolver(flowsModelToken);
    // The model is created by the DI system. The test setup replaces the real
    // model with a mock. To prevent real API calls, we stub the reload method.
    sinon.stub(flowsModel, 'reload');

    element = await fixture<GrFlows>(html`<gr-flows></gr-flows>`);
    element['changeNum'] = 123 as NumericChangeId;
    await element.updateComplete;
  });

  teardown(() => {
    clock.restore();
  });

  test('renders create flow component and no flows', async () => {
    flowsModel.setState({flows: [], loading: false});
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
        <dialog id="deleteFlowModal">
          <gr-dialog confirm-label="Delete">
            <div class="header" slot="header">Delete Flow</div>
            <div class="main" slot="main">
              Are you sure you want to delete this flow?
            </div>
          </gr-dialog>
        </dialog>
      `,
      {ignoreAttributes: ['role']}
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
    flowsModel.setState({flows, loading: false});
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
            <div class="heading-with-button">
              <h2 class="main-heading">Existing Flows</h2>
              <gr-button
                aria-label="Refresh flows"
                link=""
                title="Refresh flows"
              >
                <gr-icon icon="refresh"></gr-icon>
              </gr-button>
            </div>
            <md-filled-select label="Filter by status">
              <md-select-option value="all">
                <div slot="headline">All</div>
              </md-select-option>
              <md-select-option value="DONE">
                <div slot="headline">DONE</div>
              </md-select-option>
              <md-select-option value="FAILED">
                <div slot="headline">FAILED</div>
              </md-select-option>
              <md-select-option value="PENDING">
                <div slot="headline">PENDING</div>
              </md-select-option>
              <md-select-option value="TERMINATED">
                <div slot="headline">TERMINATED</div>
              </md-select-option>
            </md-filled-select>
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
              <table>
                <thead>
                  <tr>
                    <th>Status</th>
                    <th>Condition</th>
                    <th>Action</th>
                    <th>Parameters</th>
                    <th>Message</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>
                      <gr-icon
                        aria-label="done"
                        filled
                        icon="check_circle"
                      ></gr-icon>
                    </td>
                    <td>label:Code-Review=+1</td>
                    <td></td>
                    <td></td>
                    <td></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="flow">
              <div class="flow-header">
                <gr-button link title="Delete flow">
                  <gr-icon icon="delete" filled></gr-icon>
                </button>
              </div>
              <div class="flow-id hidden">Flow flow2</div>
              <div>
                Created:
                <gr-date-formatter withtooltip></gr-date-formatter>
              </div>
              <table>
                <thead>
                  <tr>
                    <th>Status</th>
                    <th>Condition</th>
                    <th>Action</th>
                    <th>Parameters</th>
                    <th>Message</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>
                      <gr-icon aria-label="pending" icon="timelapse"></gr-icon>
                    </td>
                    <td>label:Verified=+1</td>
                    <td>submit</td>
                     <td></td>
                    <td></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
        <dialog id="deleteFlowModal">
          <gr-dialog confirm-label="Delete">
            <div class="header" slot="header">Delete Flow</div>
            <div class="main" slot="main">
              Are you sure you want to delete this flow?
            </div>
          </gr-dialog>
        </dialog>
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
          'md-menu-item',
        ],
      }
    );
  });

  test('deletes a flow after confirmation', async () => {
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
    const deleteFlowStub = sinon.stub(flowsModel, 'deleteFlow');
    flowsModel.setState({flows, loading: false});
    await element.updateComplete;

    const deleteButton = queryAndAssert<GrButton>(element, '.flow gr-button');
    deleteButton.click();
    await element.updateComplete;

    const dialog = queryAndAssert<HTMLDialogElement>(
      element,
      '#deleteFlowModal'
    );
    assert.isTrue(dialog.open);

    const grDialog = queryAndAssert<GrDialog>(dialog, 'gr-dialog');
    const confirmButton = queryAndAssert<GrButton>(grDialog, '#confirm');
    confirmButton.click();
    await element.updateComplete;

    assert.isTrue(deleteFlowStub.calledOnceWith('flow1'));
  });

  test('cancel deleting a flow', async () => {
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
    const deleteFlowStub = sinon.stub(flowsModel, 'deleteFlow');
    flowsModel.setState({flows, loading: false});
    await element.updateComplete;

    const deleteButton = queryAndAssert<GrButton>(element, '.flow gr-button');
    deleteButton.click();
    await element.updateComplete;

    const dialog = queryAndAssert<HTMLDialogElement>(
      element,
      '#deleteFlowModal'
    );
    assert.isTrue(dialog.open);

    const grDialog = queryAndAssert<GrDialog>(dialog, 'gr-dialog');
    const cancelButton = queryAndAssert<GrButton>(grDialog, '#cancel');
    cancelButton.click();
    await element.updateComplete;

    assert.isTrue(deleteFlowStub.notCalled);
    assert.isFalse(dialog.open);
  });

  test('refreshes flows on button click', async () => {
    const flow = {
      uuid: 'flow1',
      owner: {name: 'owner1'},
      created: '2025-01-01T10:00:00.000Z' as Timestamp,
      stages: [],
    } as FlowInfo;
    flowsModel.setState({flows: [flow], loading: false});
    await element.updateComplete;

    const reloadStub = flowsModel.reload as sinon.SinonStub;
    reloadStub.resetHistory();

    const refreshButton = queryAndAssert<GrButton>(
      element,
      '.heading-with-button gr-button'
    );
    refreshButton.click();
    await element.updateComplete;

    assert.isTrue(reloadStub.calledOnce);
  });

  suite('filter', () => {
    const flows: FlowInfo[] = [
      {
        uuid: 'flow-done',
        owner: {name: 'owner1'},
        created: '2025-01-01T10:00:00.000Z' as Timestamp,
        stages: [
          {expression: {condition: 'cond-done'}, state: FlowStageState.DONE},
        ],
      },
      {
        uuid: 'flow-pending',
        owner: {name: 'owner2'},
        created: '2025-01-02T10:00:00.000Z' as Timestamp,
        stages: [
          {
            expression: {condition: 'cond-pending'},
            state: FlowStageState.PENDING,
          },
        ],
      },
      {
        uuid: 'flow-failed',
        owner: {name: 'owner3'},
        created: '2025-01-03T10:00:00.000Z' as Timestamp,
        stages: [
          {
            expression: {condition: 'cond-failed'},
            state: FlowStageState.FAILED,
          },
        ],
      },
      {
        uuid: 'flow-terminated',
        owner: {name: 'owner4'},
        created: '2025-01-04T10:00:00.000Z' as Timestamp,
        stages: [
          {
            expression: {condition: 'cond-terminated'},
            state: FlowStageState.TERMINATED,
          },
        ],
      },
    ];

    setup(async () => {
      flowsModel.setState({flows, loading: false});
      await element.updateComplete;
    });

    test('shows all flows by default', () => {
      const flowElements = element.shadowRoot!.querySelectorAll('.flow');
      assert.equal(flowElements.length, 4);
    });

    test('filters by DONE', async () => {
      element['statusFilter'] = FlowStageState.DONE;
      await element.updateComplete;

      const flowElements = element.shadowRoot!.querySelectorAll('.flow');
      assert.equal(flowElements.length, 1);
      assert.include(flowElements[0].textContent, 'cond-done');
    });

    test('filters by PENDING', async () => {
      element['statusFilter'] = FlowStageState.PENDING;
      await element.updateComplete;

      const flowElements = element.shadowRoot!.querySelectorAll('.flow');
      assert.equal(flowElements.length, 1);
      assert.include(flowElements[0].textContent, 'cond-pending');
    });

    test('filters by FAILED', async () => {
      element['statusFilter'] = FlowStageState.FAILED;
      await element.updateComplete;

      const flowElements = element.shadowRoot!.querySelectorAll('.flow');
      assert.equal(flowElements.length, 1);
      assert.include(flowElements[0].textContent, 'cond-failed');
    });

    test('filters by TERMINATED', async () => {
      element['statusFilter'] = FlowStageState.TERMINATED;
      await element.updateComplete;

      const flowElements = element.shadowRoot!.querySelectorAll('.flow');
      assert.equal(flowElements.length, 1);
      assert.include(flowElements[0].textContent, 'cond-terminated');
    });

    test('shows all when filter is changed to all', async () => {
      element['statusFilter'] = FlowStageState.DONE;
      await element.updateComplete;
      let flowElements = element.shadowRoot!.querySelectorAll('.flow');
      assert.equal(flowElements.length, 1);

      element['statusFilter'] = 'all';
      await element.updateComplete;

      flowElements = element.shadowRoot!.querySelectorAll('.flow');
      assert.equal(flowElements.length, 4);
    });
  });
});
