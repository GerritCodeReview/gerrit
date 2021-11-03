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
  ChecksPatchset,
  updateStateSetLoading,
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

function current() {
  return _testOnly_getState().pluginStateLatest[PLUGIN_NAME];
}

suite('checks-model tests', () => {
  test('updateStateSetProvider', () => {
    updateStateSetProvider(PLUGIN_NAME, ChecksPatchset.LATEST);
    assert.deepEqual(current(), {
      pluginName: PLUGIN_NAME,
      loading: false,
      firstTimeLoad: true,
      runs: [],
      actions: [],
      links: [],
    });
  });

  test('loading and first time load', () => {
    updateStateSetProvider(PLUGIN_NAME, ChecksPatchset.LATEST);
    assert.isFalse(current().loading);
    assert.isTrue(current().firstTimeLoad);
    updateStateSetLoading(PLUGIN_NAME, ChecksPatchset.LATEST);
    assert.isTrue(current().loading);
    assert.isTrue(current().firstTimeLoad);
    updateStateSetResults(PLUGIN_NAME, RUNS, [], [], ChecksPatchset.LATEST);
    assert.isFalse(current().loading);
    assert.isFalse(current().firstTimeLoad);
    updateStateSetLoading(PLUGIN_NAME, ChecksPatchset.LATEST);
    assert.isTrue(current().loading);
    assert.isFalse(current().firstTimeLoad);
    updateStateSetResults(PLUGIN_NAME, RUNS, [], [], ChecksPatchset.LATEST);
    assert.isFalse(current().loading);
    assert.isFalse(current().firstTimeLoad);
  });

  test('updateStateSetResults', () => {
    updateStateSetResults(PLUGIN_NAME, RUNS, [], [], ChecksPatchset.LATEST);
    assert.lengthOf(current().runs, 1);
    assert.lengthOf(current().runs[0].results!, 1);
  });

  test('updateStateUpdateResult', () => {
    updateStateSetResults(PLUGIN_NAME, RUNS, [], [], ChecksPatchset.LATEST);
    assert.equal(
      current().runs[0].results![0].summary,
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
    assert.lengthOf(current().runs, 1);
    assert.lengthOf(current().runs[0].results!, 1);
    assert.equal(current().runs[0].results![0].summary, 'new');
  });
});
