/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-flows';
import {assert, fixture, html} from '@open-wc/testing';
import {GrFlows} from './gr-flows';
import {
  AccountId,
  CommitId,
  FlowInfo,
  FlowStageState,
  Timestamp,
} from '../../../api/rest-api';
import {queryAndAssert} from '../../../test/test-utils';
import {NumericChangeId} from '../../../types/common';
import sinon from 'sinon';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {FlowsModel, flowsModelToken} from '../../../models/flows/flows-model';
import {
  ChangeModel,
  changeModelToken,
} from '../../../models/change/change-model';
import {UserModel, userModelToken} from '../../../models/user/user-model';
import {testResolver} from '../../../test/common-test-setup';
import {
  createAccountDetailWithId,
  createFlow,
  createParsedChange,
  createRevision,
} from '../../../test/test-data-generators';

function setChangeWithUploader(
  changeModel: ChangeModel,
  uploaderId: AccountId
) {
  changeModel.updateState({
    change: {
      ...createParsedChange(),
      _number: 123 as NumericChangeId,
      revisions: {
        rev1: {
          ...createRevision(1),
          uploader: createAccountDetailWithId(uploaderId),
        },
      },
      current_revision: 'rev1' as CommitId,
    },
  });
}

suite('gr-flows tests', () => {
  let element: GrFlows;
  let clock: sinon.SinonFakeTimers;
  let flowsModel: FlowsModel;
  let changeModel: ChangeModel;
  let userModel: UserModel;

  setup(async () => {
    clock = sinon.useFakeTimers({shouldClearNativeTimers: true});

    changeModel = testResolver(changeModelToken);
    userModel = testResolver(userModelToken);
    flowsModel = testResolver(flowsModelToken);
    // The model is created by the DI system. The test setup replaces the real
    // model with a mock. To prevent real API calls, we stub the reload method.
    sinon.stub(flowsModel, 'reload');

    element = await fixture<GrFlows>(html`<gr-flows></gr-flows>`);
    await element.updateComplete;
    setChangeWithUploader(changeModel, 123 as AccountId);
    userModel.setState({
      account: createAccountDetailWithId(123 as AccountId),
      accountLoaded: true,
    });
    await element.updateComplete;
  });

  teardown(() => {
    clock.restore();
  });

  test('renders create flow component and no flows', async () => {
    flowsModel.setState({
      flows: [],
      loading: false,
      isEnabled: true,
      providers: [],
    });
    await element.updateComplete;
  });

  test('renders flows', async () => {
    const flows: FlowInfo[] = [
      createFlow({
        last_evaluated: '2025-01-01T11:00:00.000Z' as Timestamp,
      }),
      createFlow({
        uuid: 'flow2',
        owner: {name: 'owner2', _account_id: 2 as AccountId},
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
      }),
    ];
    flowsModel.setState({
      flows,
      loading: false,
      isEnabled: true,
      providers: [],
    });
    await element.updateComplete;

    // prettier formats the spacing for "last evaluated" incorrectly
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="container">
          <div class="header-actions">
            <gr-create-flow> </gr-create-flow>
          </div>
          <div class="flows-header">
            <div class="heading-with-button">
              <h2 class="main-heading">Scheduled Flows</h2>
              <gr-button
                aria-disabled="false"
                aria-label="Refresh flows"
                class="refresh"
                link=""
                role="button"
                tabindex="0"
                title="Refresh flows"
              >
                <gr-icon icon="refresh"> </gr-icon>
              </gr-button>
            </div>
          </div>
          <div>
            <div class="flow">
              <div class="flow-header">
                <div class="flow-title">Flow</div>
                <div class="flow-actions">
                  <gr-copy-clipboard
                    buttontitle="Copy flow string to clipboard"
                    hideinput=""
                  >
                  </gr-copy-clipboard>
                  <gr-button
                    aria-disabled="false"
                    link=""
                    role="button"
                    tabindex="0"
                    title="Delete flow"
                  >
                    <gr-icon icon="delete"> </gr-icon>
                  </gr-button>
                </div>
              </div>
              <div class="flow-info">
                <div class="owner-container">
                  Owner:
                  <gr-avatar hidden=""> </gr-avatar>
                  <gr-account-label deselected=""> </gr-account-label>
                </div>
                <div>
                  Last Evaluation:
                  <gr-date-formatter withtooltip=""> </gr-date-formatter>
                </div>
              </div>
              <div class="stages">
                <gr-flow-rule></gr-flow-rule>
              </div>
            </div>
            <div class="flow">
              <div class="flow-header">
                <div class="flow-title">Submit</div>
                <div class="flow-actions">
                  <gr-copy-clipboard
                    buttontitle="Copy flow string to clipboard"
                    hideinput=""
                  >
                  </gr-copy-clipboard>
                  <gr-button
                    aria-disabled="false"
                    link=""
                    role="button"
                    tabindex="0"
                    title="Delete flow"
                  >
                    <gr-icon icon="delete"> </gr-icon>
                  </gr-button>
                </div>
              </div>
              <div class="flow-info">
                <div class="owner-container">
                  Owner:
                  <gr-avatar hidden=""> </gr-avatar>
                  <gr-account-label deselected=""> </gr-account-label>
                </div>
              </div>
              <div class="stages">
                <gr-flow-rule></gr-flow-rule>
              </div>
            </div>
          </div>
        </div>
        <dialog id="deleteFlowModal">
          <gr-dialog confirm-label="Delete" role="dialog">
            <div class="header" slot="header">Delete Flow</div>
            <div class="main" slot="main">
              Are you sure you want to delete this flow?
            </div>
          </gr-dialog>
        </dialog>
      `
    );
  });

  test('deletes a flow after confirmation', async () => {
    const flows: FlowInfo[] = [createFlow()];
    const deleteFlowStub = sinon.stub(flowsModel, 'deleteFlow');
    flowsModel.setState({
      flows,
      loading: false,
      isEnabled: true,
      providers: [],
    });
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
    const flows: FlowInfo[] = [createFlow()];
    const deleteFlowStub = sinon.stub(flowsModel, 'deleteFlow');
    flowsModel.setState({
      flows,
      loading: false,
      isEnabled: true,
      providers: [],
    });
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
    const flow = createFlow();
    flowsModel.setState({
      flows: [flow],
      loading: false,
      isEnabled: true,
      providers: [],
    });
    await element.updateComplete;

    const reloadStub = flowsModel.reload as sinon.SinonStub;
    reloadStub.resetHistory();

    const refreshButton = queryAndAssert<GrButton>(
      element,
      '.flows-header gr-button'
    );
    refreshButton.click();
    await element.updateComplete;

    assert.isTrue(reloadStub.calledOnce);
  });

  suite('create flow visibility', () => {
    setup(async () => {
      flowsModel.setState({
        flows: [],
        loading: false,
        isEnabled: true,
        providers: [],
      });
      await element.updateComplete;
    });

    test('shows gr-create-flow when current user is uploader', async () => {
      const uploaderId = 123 as AccountId;
      const currentUserId = 123 as AccountId;
      setChangeWithUploader(changeModel, uploaderId);
      userModel.setState({
        account: createAccountDetailWithId(currentUserId),
        accountLoaded: true,
      });
      await element.updateComplete;

      const createFlow = element.shadowRoot!.querySelector('gr-create-flow');
      assert.isNotNull(createFlow);
    });

    test('hides gr-create-flow when current user is not uploader', async () => {
      const uploaderId = 456 as AccountId;
      const currentUserId = 123 as AccountId;
      setChangeWithUploader(changeModel, uploaderId);
      userModel.setState({
        account: createAccountDetailWithId(currentUserId),
        accountLoaded: true,
      });
      await element.updateComplete;

      const createFlow = element.shadowRoot!.querySelector('gr-create-flow');
      assert.isNull(createFlow);
    });
  });
});
