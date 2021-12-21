/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import '../../test/common-test-setup-karma';
import './checks-model';
import {ChecksModel, ChecksPatchset, ChecksProviderState} from './checks-model';
import {
  Category,
  CheckRun,
  ChecksApiConfig,
  ChecksProvider,
  ResponseCode,
  RunStatus,
} from '../../api/checks';
import {getAppContext} from '../app-context';
import {createParsedChange} from '../../test/test-data-generators';
import {waitUntil, waitUntilCalled} from '../../test/test-utils';
import {ParsedChangeInfo} from '../../types/types';

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

const CONFIG: ChecksApiConfig = {
  fetchPollingIntervalSeconds: 1000,
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
      getAppContext().routerModel,
      getAppContext().changeModel,
      getAppContext().reportingService
    );
    model.checksLatest$.subscribe(c => (current = c[PLUGIN_NAME]));
  });

  teardown(() => {
    model.finalize();
  });

  test('register and fetch', async () => {
    let change: ParsedChangeInfo | undefined = undefined;
    model.changeModel.change$.subscribe(c => (change = c));
    const provider = createProvider();
    const fetchSpy = sinon.spy(provider, 'fetch');

    model.register('test-plugin', provider, CONFIG);
    await waitUntil(() => change === undefined);

    const testChange = createParsedChange();
    model.changeModel.updateStateChange(testChange);
    await waitUntil(() => change === testChange);
    await waitUntilCalled(fetchSpy, 'fetch');
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

  test('model.updateStateUpdateResult', () => {
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
      RUNS[0]!.results![0].summary
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
});
