/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {testResolver} from '../../test/common-test-setup';
import {
  buildFlowCondition,
  FlowsModel,
  getChangePrefix,
  getSubmitCondition,
  SUBMIT_ACTION_NAME,
} from './flows-model';
import {ChangeModel, changeModelToken} from '../change/change-model';
import {UserModel, userModelToken} from '../user/user-model';
import {PluginsModel} from '../plugins/plugins-model';
import {stubRestApi, waitUntil} from '../../test/test-utils';
import {NumericChangeId} from '../../types/common';
import {assert} from '@open-wc/testing';
import {FlowsAutosubmitProvider} from '../../api/flows';
import {
  createAccountDetailWithId,
  createFlow,
  createParsedChange,
  createRevision,
} from '../../test/test-data-generators';
import {
  AccountId,
  CommitId,
  FlowInfo,
  FlowStageState,
} from '../../api/rest-api';
import {ChangeStatus} from '../../constants/constants';

suite('flows-model tests', () => {
  let flowsModel: FlowsModel;
  let changeModel: ChangeModel;
  let userModel: UserModel;
  let pluginsModel: PluginsModel;

  setup(() => {
    changeModel = testResolver(changeModelToken);
    userModel = testResolver(userModelToken);
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
          condition: getSubmitCondition(),
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
          condition: getChangePrefix() + ' is custom condition',
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
              condition: getSubmitCondition(),
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

  suite('getChangePrefix', () => {
    let originalPath: string;

    setup(() => {
      originalPath = window.location.pathname;
    });

    teardown(() => {
      window.history.replaceState(null, '', originalPath);
    });

    test('strips patchset, diff range, and file path subpaths', () => {
      const origin = window.location.origin;

      window.history.replaceState(null, '', '/c/my-repo/+/123');
      assert.equal(getChangePrefix(), `${origin}/c/my-repo/+/123`);

      window.history.replaceState(null, '', '/c/my-repo/+/123/4');
      assert.equal(getChangePrefix(), `${origin}/c/my-repo/+/123`);

      window.history.replaceState(null, '', '/c/my-repo/+/123/1..4');
      assert.equal(getChangePrefix(), `${origin}/c/my-repo/+/123`);

      window.history.replaceState(
        null,
        '',
        '/c/my-repo/+/123/1..4/src/file.ts'
      );
      assert.equal(getChangePrefix(), `${origin}/c/my-repo/+/123`);
    });
  });

  test('hasAutosubmitFlowAlready detects flows when navigating between patchsets/diff ranges', async () => {
    const originalPath = window.location.pathname;
    try {
      window.history.replaceState(null, '', '/c/my-repo/+/123/4..5/src/foo.ts');
      stubRestApi('getIfFlowsIsEnabled').resolves({enabled: true});
      const expectedPrefix = `${window.location.origin}/c/my-repo/+/123`;
      stubRestApi('listFlows').resolves([
        createFlow({
          uuid: 'flow1',
          stages: [
            {
              expression: {
                condition: `${expectedPrefix} is is:submittable`,
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
    } finally {
      window.history.replaceState(null, '', originalPath);
    }
  });

  suite('canCreateFlow$', () => {
    test('returns true only when enabled, open, and user is both owner and latest uploader', async () => {
      stubRestApi('getIfFlowsIsEnabled').resolves({enabled: true});
      stubRestApi('listFlows').resolves([]);

      let canCreate = false;
      flowsModel.canCreateFlow$.subscribe(v => (canCreate = v));

      changeModel.updateStateChange({
        ...createParsedChange(),
        _number: 123 as NumericChangeId,
        status: ChangeStatus.NEW,
        owner: createAccountDetailWithId(100 as AccountId),
        current_revision: 'rev1' as CommitId,
        revisions: {
          rev1: {
            ...createRevision(1),
            uploader: createAccountDetailWithId(100 as AccountId),
          },
        },
      });
      await waitUntil(() => flowsModel.getState().isEnabled);
      userModel.setAccount(createAccountDetailWithId(100 as AccountId));
      assert.isTrue(canCreate);
      assert.isTrue(flowsModel.canCreateFlow());

      // Latest patchset uploaded by another user -> false
      changeModel.updateStateChange({
        ...createParsedChange(),
        _number: 123 as NumericChangeId,
        status: ChangeStatus.NEW,
        owner: createAccountDetailWithId(100 as AccountId),
        current_revision: 'rev1' as CommitId,
        revisions: {
          rev1: {
            ...createRevision(1),
            uploader: createAccountDetailWithId(200 as AccountId),
          },
        },
      });
      assert.isFalse(canCreate);
      assert.isFalse(flowsModel.canCreateFlow());

      // Merged change -> false
      changeModel.updateStateChange({
        ...createParsedChange(),
        _number: 123 as NumericChangeId,
        status: ChangeStatus.MERGED,
        owner: createAccountDetailWithId(100 as AccountId),
      });
      assert.isFalse(canCreate);
    });
  });

  test('upsertFlow deletes existingFlowId before creating new flow and reloads once', async () => {
    stubRestApi('getIfFlowsIsEnabled').resolves({enabled: true});
    stubRestApi('listFlows').resolves([]);
    const deleteFlowStub = stubRestApi('deleteFlow').resolves(new Response());
    const createFlowStub = stubRestApi('createFlow').resolves(
      createFlow({uuid: 'flow-2', stages: []})
    );

    changeModel.updateStateChange({
      ...createParsedChange(),
      _number: 123 as NumericChangeId,
    });
    await waitUntil(() => flowsModel.getState().isEnabled);

    const reloadSpy = sinon.spy(flowsModel, 'reload');
    const flowInput = {stage_expressions: [{condition: 'is:submittable'}]};
    await flowsModel.upsertFlow('flow-1', flowInput);

    assert.isTrue(
      deleteFlowStub.calledOnceWith(123 as NumericChangeId, 'flow-1')
    );
    assert.isTrue(
      createFlowStub.calledOnceWith(123 as NumericChangeId, flowInput)
    );
    assert.isTrue(deleteFlowStub.calledBefore(createFlowStub));
    assert.isTrue(reloadSpy.calledOnce);
  });

  test('buildCondition constructs condition with change prefix', () => {
    const change = {
      ...createParsedChange(),
      _number: 456 as NumericChangeId,
    };
    const originalPath = window.location.pathname;
    try {
      window.history.replaceState(null, '', '/dashboard/self');
      assert.equal(
        flowsModel.buildCondition('label:Verified=+1', change),
        `${window.location.origin}/c/${change.project}/+/456 is label:Verified=+1`
      );
      assert.equal(
        buildFlowCondition(
          'label:Verified=+1',
          change,
          'https://example.com',
          '/dashboard/self'
        ),
        `https://example.com/c/${change.project}/+/456 is label:Verified=+1`
      );
    } finally {
      window.history.replaceState(null, '', originalPath);
    }
  });
});
