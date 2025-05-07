/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../test/common-test-setup';
import './checks-model';
import {
  CheckResult,
  ChecksModel,
  ChecksPatchset,
  ChecksProviderState,
  RunResult,
  collectRunResults,
} from './checks-model';
import {
  Action,
  Category,
  CheckRun,
  ChecksApiConfig,
  ChecksProvider,
  ResponseCode,
  RunStatus,
} from '../../api/checks';
import {getAppContext} from '../../services/app-context';
import {
  createCheckResult,
  createParsedChange,
  createRun,
} from '../../test/test-data-generators';
import {waitUntil, waitUntilCalled} from '../../test/test-utils';
import {ParsedChangeInfo} from '../../types/types';
import {
  changeModelToken,
  updateRevisionsWithCommitShas,
} from '../change/change-model';
import {assert} from '@open-wc/testing';
import {testResolver} from '../../test/common-test-setup';
import {changeViewModelToken} from '../views/change';
import {NumericChangeId, PatchSetNumber} from '../../api/rest-api';
import {pluginLoaderToken} from '../../elements/shared/gr-js-api-interface/gr-plugin-loader';
import {deepEqual} from '../../utils/deep-util';

const PLUGIN_NAME = 'test-plugin';

const RUNS: CheckRun[] = [
  {
    checkName: 'MacCheck',
    change: 123,
    patchset: 1,
    attempt: 1,
    status: RunStatus.COMPLETED,
    results: [
      {
        externalId: 'id-314',
        category: Category.WARNING,
        summary: 'Meddle cheddle check and you are weg.',
      },
    ],
  },
];

const CONFIG_POLLING_5S: ChecksApiConfig = {
  fetchPollingIntervalSeconds: 5,
};

const CONFIG_POLLING_NONE: ChecksApiConfig = {
  fetchPollingIntervalSeconds: 0,
};

function createProvider(): ChecksProvider {
  return {
    fetch: () =>
      Promise.resolve({
        responseCode: ResponseCode.OK,
        runs: [],
      }),
  };
}

suite('checks-model tests', () => {
  let model: ChecksModel;

  let current: ChecksProviderState;

  setup(() => {
    model = new ChecksModel(
      testResolver(changeViewModelToken),
      testResolver(changeModelToken),
      getAppContext().reportingService,
      testResolver(pluginLoaderToken).pluginsModel
    );
    model.checksLatest$.subscribe(c => (current = c[PLUGIN_NAME]));
  });

  teardown(() => {
    model.finalize();
  });

  test('register and fetch', async () => {
    let change: ParsedChangeInfo | undefined = undefined;
    testResolver(changeModelToken).change$.subscribe(c => (change = c));
    const provider = createProvider();
    const fetchSpy = sinon.spy(provider, 'fetch');

    model.register({
      pluginName: 'test-plugin',
      provider,
      config: CONFIG_POLLING_NONE,
    });
    await waitUntil(() => change === undefined);

    const testChange = updateRevisionsWithCommitShas(createParsedChange());
    testResolver(changeModelToken).updateStateChange(testChange);
    await waitUntil(() => deepEqual(change, testChange));
    await waitUntilCalled(fetchSpy, 'fetch');

    assert.equal(
      model.latestPatchNum,
      testChange!.revisions[testChange!.current_revision]
        ._number as PatchSetNumber
    );
    assert.equal(model.changeNum, testChange!._number);
  });

  test('fetch throttle', async () => {
    const clock = sinon.useFakeTimers();
    let change: ParsedChangeInfo | undefined = undefined;
    testResolver(changeModelToken).change$.subscribe(c => (change = c));
    const provider = createProvider();
    const fetchSpy = sinon.spy(provider, 'fetch');

    model.register({
      pluginName: 'test-plugin',
      provider,
      config: CONFIG_POLLING_NONE,
    });
    await waitUntil(() => change === undefined);

    const testChange = updateRevisionsWithCommitShas(createParsedChange());
    testResolver(changeModelToken).updateStateChange(testChange);
    await waitUntil(() => deepEqual(change, testChange));

    model.reload('test-plugin');
    model.reload('test-plugin');
    model.reload('test-plugin');

    // Does not emit at 'leading' of throttle interval,
    // because fetch() is not called when change is undefined.
    assert.equal(fetchSpy.callCount, 0);

    // 600 ms is greater than the 500 ms throttle time.
    clock.tick(600);
    // emits at 'trailing' of throttle interval
    assert.equal(fetchSpy.callCount, 1);

    model.reload('test-plugin');
    model.reload('test-plugin');
    model.reload('test-plugin');
    model.reload('test-plugin');
    // emits at 'leading' of throttle interval
    assert.equal(fetchSpy.callCount, 2);

    // 600 ms is greater than the 500 ms throttle time.
    clock.tick(600);
    // emits at 'trailing' of throttle interval
    assert.equal(fetchSpy.callCount, 3);
  });

  test('triggerAction', async () => {
    model.changeNum = 314 as NumericChangeId;
    model.latestPatchNum = 13 as PatchSetNumber;
    const action: Action = {
      name: 'test action',
      callback: () => undefined,
    };
    const spy = sinon.spy(action, 'callback');
    model.triggerAction(action, undefined, 'none');
    assert.isTrue(spy.calledOnce);
    assert.equal(spy.lastCall.args[0], 314);
    assert.equal(spy.lastCall.args[1], 13);
  });

  test('model.updateStateSetProvider', () => {
    model.updateStateSetProvider(PLUGIN_NAME, ChecksPatchset.LATEST);
    assert.deepEqual(current, {
      pluginName: PLUGIN_NAME,
      loading: false,
      firstTimeLoad: true,
      runs: [],
      actions: [],
      links: [],
    });
  });

  test('loading and first time load', () => {
    model.updateStateSetProvider(PLUGIN_NAME, ChecksPatchset.LATEST);
    assert.isFalse(current.loading);
    assert.isTrue(current.firstTimeLoad);
    model.updateStateSetLoading(PLUGIN_NAME, ChecksPatchset.LATEST);
    assert.isTrue(current.loading);
    assert.isTrue(current.firstTimeLoad);
    model.updateStateSetResults(
      PLUGIN_NAME,
      RUNS,
      [],
      [],
      undefined,
      ChecksPatchset.LATEST
    );
    assert.isFalse(current.loading);
    assert.isFalse(current.firstTimeLoad);
    model.updateStateSetLoading(PLUGIN_NAME, ChecksPatchset.LATEST);
    assert.isTrue(current.loading);
    assert.isFalse(current.firstTimeLoad);
    model.updateStateSetResults(
      PLUGIN_NAME,
      RUNS,
      [],
      [],
      undefined,
      ChecksPatchset.LATEST
    );
    assert.isFalse(current.loading);
    assert.isFalse(current.firstTimeLoad);
  });

  test('model.updateStateSetResults', () => {
    model.updateStateSetProvider(PLUGIN_NAME, ChecksPatchset.LATEST);
    model.updateStateSetResults(
      PLUGIN_NAME,
      RUNS,
      [],
      [],
      undefined,
      ChecksPatchset.LATEST
    );
    assert.lengthOf(current.runs, 1);
    assert.lengthOf(current.runs[0].results!, 1);
  });

  test('model.updateStateSetResults ignore empty name or status', () => {
    model.updateStateSetProvider(PLUGIN_NAME, ChecksPatchset.LATEST);
    model.updateStateSetResults(
      PLUGIN_NAME,
      [
        {
          checkName: 'test-check-name',
          status: RunStatus.COMPLETED,
        },
        // Will be ignored, because the checkName is empty.
        {
          checkName: undefined as unknown as string,
          status: RunStatus.COMPLETED,
        },
        // Will be ignored, because the status is empty.
        {
          checkName: 'test-check-name',
          status: undefined as unknown as RunStatus,
        },
      ],
      [],
      [],
      undefined,
      ChecksPatchset.LATEST
    );
    // 2 out of 3 runs are ignored.
    assert.lengthOf(current.runs, 1);
  });

  test('model.updateStateUpdateResult', () => {
    model.updateStateSetProvider(PLUGIN_NAME, ChecksPatchset.LATEST);
    model.updateStateSetResults(
      PLUGIN_NAME,
      RUNS,
      [],
      [],
      undefined,
      ChecksPatchset.LATEST
    );
    assert.equal(
      current.runs[0].results![0].summary,
      RUNS[0].results![0].summary
    );
    const result = RUNS[0].results![0];
    const updatedResult = {...result, summary: 'new'};
    model.updateStateUpdateResult(
      PLUGIN_NAME,
      RUNS[0],
      updatedResult,
      ChecksPatchset.LATEST
    );
    assert.lengthOf(current.runs, 1);
    assert.lengthOf(current.runs[0].results!, 1);
    assert.equal(current.runs[0].results![0].summary, 'new');
  });

  test('allResults$', async () => {
    let results: CheckResult[] | undefined = undefined;
    model.allResults$.subscribe(allResults => (results = allResults));
    testResolver(changeViewModelToken).updateState({
      checksPatchset: 1 as PatchSetNumber,
    });
    testResolver(changeModelToken).updateStateChange(createParsedChange());

    model.updateStateSetProvider(PLUGIN_NAME, ChecksPatchset.SELECTED);
    model.updateStateSetProvider(PLUGIN_NAME, ChecksPatchset.LATEST);
    assert.equal(results!.length, 0);

    model.updateStateSetResults(
      PLUGIN_NAME,
      RUNS,
      [],
      [],
      undefined,
      ChecksPatchset.LATEST
    );
    assert.equal(results!.length, 1);

    model.updateStateSetResults(
      PLUGIN_NAME,
      RUNS,
      [],
      [],
      undefined,
      ChecksPatchset.SELECTED
    );

    assert.equal(results!.length, 1);
  });

  test('polls for changes', async () => {
    const clock = sinon.useFakeTimers();
    let change: ParsedChangeInfo | undefined = undefined;
    testResolver(changeModelToken).change$.subscribe(c => (change = c));
    const provider = createProvider();
    const fetchSpy = sinon.spy(provider, 'fetch');

    model.register({
      pluginName: 'test-plugin',
      provider,
      config: CONFIG_POLLING_5S,
    });
    await waitUntil(() => change === undefined);
    clock.tick(1);
    const testChange = updateRevisionsWithCommitShas(createParsedChange());
    testResolver(changeModelToken).updateStateChange(testChange);
    await waitUntil(() => deepEqual(change, testChange));
    clock.tick(600); // need to wait for 500ms throttle
    await waitUntilCalled(fetchSpy, 'fetch');
    const pollCount = fetchSpy.callCount;

    // polling should continue while we wait
    clock.tick(CONFIG_POLLING_5S.fetchPollingIntervalSeconds * 1000 * 2);

    assert.isTrue(fetchSpy.callCount > pollCount);
  });

  test('does not poll when config specifies 0 seconds', async () => {
    const clock = sinon.useFakeTimers();
    let change: ParsedChangeInfo | undefined = undefined;
    testResolver(changeModelToken).change$.subscribe(c => (change = c));
    const provider = createProvider();
    const fetchSpy = sinon.spy(provider, 'fetch');

    model.register({
      pluginName: 'test-plugin',
      provider,
      config: CONFIG_POLLING_NONE,
    });
    await waitUntil(() => change === undefined);
    clock.tick(1);
    const testChange = updateRevisionsWithCommitShas(createParsedChange());
    testResolver(changeModelToken).updateStateChange(testChange);
    await waitUntil(() => deepEqual(change, testChange));
    clock.tick(600); // need to wait for 500ms throttle
    await waitUntilCalled(fetchSpy, 'fetch');
    clock.tick(1);
    const pollCount = fetchSpy.callCount;

    // polling should not happen
    clock.tick(60 * 1000);

    assert.equal(fetchSpy.callCount, pollCount);
  });

  test('collectRunResults does not incur quadratic size increase', async () => {
    const results: CheckResult[] = [];
    for (let i = 0; i < 100; i++) {
      results.push({
        ...createCheckResult({
          message: 'some message',
        }),
      });
    }
    const run = createRun({results});
    let collected: RunResult[] = [];
    collected = collectRunResults(collected, {
      runs: [run],
    } as ChecksProviderState);
    const collectedString = JSON.stringify(collected);
    // If the `results` property would not be removed from every check run, then
    // this combined string would be >1MB in size.
    assert.isAtMost(collectedString.length, 50000);
  });
});
