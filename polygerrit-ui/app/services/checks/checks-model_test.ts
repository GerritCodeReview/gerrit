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
import {
  _testOnly_getState,
  _testOnly_resetState,
  ChecksPatchset,
  updateStateSetProvider,
  updateStateSetResults,
  updateStateUpdateResult,
} from './checks-model';
import {Category, CheckRun, RunStatus} from '../../api/checks';

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

suite('checks-model tests', () => {
  test('updateStateSetProvider', () => {
    _testOnly_resetState();
    updateStateSetProvider(PLUGIN_NAME, ChecksPatchset.LATEST);
    const state = _testOnly_getState().pluginStateLatest[PLUGIN_NAME];
    assert.deepEqual(state, {
      pluginName: PLUGIN_NAME,
      loading: false,
      runs: [],
      actions: [],
      links: [],
    });
  });

  test('updateStateSetResults', () => {
    _testOnly_resetState();
    updateStateSetResults(PLUGIN_NAME, RUNS, [], [], ChecksPatchset.LATEST);
    const state = _testOnly_getState().pluginStateLatest[PLUGIN_NAME];
    assert.lengthOf(state.runs, 1);
    assert.lengthOf(state.runs[0].results!, 1);
  });

  test('updateStateUpdateResult', () => {
    _testOnly_resetState();
    updateStateSetResults(PLUGIN_NAME, RUNS, [], [], ChecksPatchset.LATEST);
    let state = _testOnly_getState().pluginStateLatest[PLUGIN_NAME];
    assert.equal(
      state.runs[0].results![0].summary,
      RUNS[0]!.results![0].summary
    );
    const result = RUNS[0].results![0];
    const updatedResult = {...result, summary: 'new'};
    updateStateUpdateResult(
      PLUGIN_NAME,
      RUNS[0],
      updatedResult,
      ChecksPatchset.LATEST
    );
    state = _testOnly_getState().pluginStateLatest[PLUGIN_NAME];
    assert.lengthOf(state.runs, 1);
    assert.lengthOf(state.runs[0].results!, 1);
    assert.equal(state.runs[0].results![0].summary, 'new');
  });
});
