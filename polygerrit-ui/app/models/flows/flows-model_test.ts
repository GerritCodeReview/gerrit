/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {testResolver} from '../../test/common-test-setup';
import {FlowsModel, SUBMIT_ACTION_NAME, SUBMIT_CONDITION} from './flows-model';
import {ChangeModel, changeModelToken} from '../change/change-model';
import {PluginsModel} from '../plugins/plugins-model';
import {stubRestApi, waitUntil} from '../../test/test-utils';
import {NumericChangeId} from '../../types/common';
import {assert} from '@open-wc/testing';
import {FlowsAutosubmitProvider} from '../../api/flows';
import {createFlow, createParsedChange} from '../../test/test-data-generators';
import {FlowInfo, FlowStageState} from '../../api/rest-api';

suite('flows-model tests', () => {
  let flowsModel: FlowsModel;
  let changeModel: ChangeModel;
  let pluginsModel: PluginsModel;

  setup(() => {
    changeModel = testResolver(changeModelToken);
    pluginsModel = new PluginsModel();

    flowsModel = new FlowsModel(changeModel, pluginsModel);
  });

  test('flows$ loads flows when enabled', async () => {
    stubRestApi('getIfFlowsIsEnabled').resolves({enabled: true});
    const listFlowsStub = stubRestApi('listFlows').resolves([
      createFlow({uuid: 'flow1', stages: []}),
    ]);

    let flows: FlowInfo[] = [];
    flowsModel.flows$.subscribe(f => (flows = f));

    changeModel.updateStateChange({
      ...createParsedChange(),
      _number: 123 as NumericChangeId,
    });
    await waitUntil(() => flows.length > 0);

    assert.equal(flows.length, 1);
    assert.equal(flows[0].uuid, 'flow1');
    assert.isTrue(listFlowsStub.calledWith(123 as NumericChangeId));
  });

  test('deleteFlow calls API and reloads', async () => {
    stubRestApi('getIfFlowsIsEnabled').resolves({enabled: true});
    stubRestApi('listFlows').resolves([]);
    const deleteFlowStub = stubRestApi('deleteFlow').resolves(new Response());
    const reloadSpy = sinon.spy(flowsModel, 'reload');

    changeModel.updateStateChange({
      ...createParsedChange(),
      _number: 123 as NumericChangeId,
    });
    await waitUntil(() => flowsModel.getState().isEnabled);

    await flowsModel.deleteFlow('flow1');

    assert.isTrue(deleteFlowStub.calledWith(123 as NumericChangeId, 'flow1'));
    assert.isTrue(reloadSpy.called);
  });

  test('createFlow calls API and reloads', async () => {
    stubRestApi('getIfFlowsIsEnabled').resolves({enabled: true});
    stubRestApi('listFlows').resolves([]);
    const createFlowStub = stubRestApi('createFlow').resolves(
      createFlow({uuid: 'new-flow', stages: []})
    );
    const reloadSpy = sinon.spy(flowsModel, 'reload');

    changeModel.updateStateChange({
      ...createParsedChange(),
      _number: 123 as NumericChangeId,
    });
    await waitUntil(() => flowsModel.getState().isEnabled);

    const flowInput = {stage_expressions: []};
    await flowsModel.createFlow(flowInput);

    assert.isTrue(createFlowStub.calledWith(123 as NumericChangeId, flowInput));
    assert.isTrue(reloadSpy.called);
  });

  test('createAutosubmitFlow uses default values', async () => {
    stubRestApi('getIfFlowsIsEnabled').resolves({enabled: true});
    stubRestApi('listFlows').resolves([]);
    const createFlowStub = stubRestApi('createFlow').resolves(
      createFlow({uuid: 'auto-flow', stages: []})
    );

    changeModel.updateStateChange({
      ...createParsedChange(),
      _number: 123 as NumericChangeId,
    });
    await waitUntil(() => flowsModel.getState().isEnabled);

    await flowsModel.createAutosubmitFlow();

    assert.isTrue(createFlowStub.called);
    const args = createFlowStub.firstCall.args;
    assert.equal(args[0], 123 as NumericChangeId);
    assert.deepEqual(args[1], {
      stage_expressions: [
        {
          condition: SUBMIT_CONDITION,
          action: {name: SUBMIT_ACTION_NAME},
        },
      ],
    });
  });

  test('createAutosubmitFlow uses provider values', async () => {
    stubRestApi('getIfFlowsIsEnabled').resolves({enabled: true});
    stubRestApi('listFlows').resolves([]);
    const createFlowStub = stubRestApi('createFlow').resolves(
      createFlow({uuid: 'auto-flow', stages: []})
    );

    const provider: FlowsAutosubmitProvider = {
      isAutosubmitEnabled: () => true,
      getSubmitCondition: () => 'custom condition',
      getSubmitAction: () => {
        return {name: 'custom action'};
      },
    };
    pluginsModel.registerFlowsAutosubmitProvider({
      pluginName: 'test',
      provider,
    });

    changeModel.updateStateChange({
      ...createParsedChange(),
      _number: 123 as NumericChangeId,
    });
    await waitUntil(() => flowsModel.getState().isEnabled);

    await flowsModel.createAutosubmitFlow();

    assert.isTrue(createFlowStub.called);
    const args = createFlowStub.firstCall.args;
    assert.deepEqual(args[1], {
      stage_expressions: [
        {
          condition: 'custom condition',
          action: {name: 'custom action'},
        },
      ],
    });
  });

  test('hasAutosubmitFlowAlready checks flows', async () => {
    stubRestApi('getIfFlowsIsEnabled').resolves({enabled: true});
    const listFlowsStub = stubRestApi('listFlows').resolves([
      createFlow({
        uuid: 'flow1',
        stages: [
          {
            expression: {
              condition: SUBMIT_CONDITION,
              action: {name: SUBMIT_ACTION_NAME},
            },
            state: FlowStageState.DONE,
          },
        ],
      }),
    ]);

    changeModel.updateStateChange({
      ...createParsedChange(),
      _number: 123 as NumericChangeId,
    });
    await waitUntil(() => flowsModel.getState().flows.length > 0);

    assert.isTrue(flowsModel.hasAutosubmitFlowAlready());

    listFlowsStub.resolves([
      createFlow({
        uuid: 'flow2',
        stages: [
          {
            expression: {
              condition: 'other condition',
              action: {name: 'other action'},
            },
            state: FlowStageState.DONE,
          },
        ],
      }),
    ]);
    flowsModel.reload();
    await waitUntil(() => !flowsModel.hasAutosubmitFlowAlready());
    assert.isFalse(flowsModel.hasAutosubmitFlowAlready());
  });
});
