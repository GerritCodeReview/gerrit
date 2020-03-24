/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

import {grReportingMock} from '../../services/gr-reporting/gr-reporting_mock.js';

sinon.stub(grReportingMock, 'reportInteraction', (eventName, details) => {
  assert.equal(typeof(eventName), 'string');
  if (details) {
    assert.equal(typeof(details), 'object');
  }
});
sinon.stub(grReportingMock, 'time', name => {
  assert.equal(typeof(name), 'string');
});
sinon.stub(grReportingMock, 'timeEnd', name => {
  assert.equal(typeof(name), 'string');
});
sinon.stub(grReportingMock, 'timeEndWithAverage',
    (name, averageName, denominator) => {
      assert.equal(typeof(name), 'string');
      assert.equal(typeof(averageName), 'string');
      assert.equal(typeof(denominator), 'number');
    });
sinon.stub(grReportingMock, 'setRepoName', name => {
  assert.equal(typeof(name), 'string');
});
sinon.stub(grReportingMock, 'pluginLoaded', name => {
  assert.equal(typeof(name), 'string');
});
sinon.stub(grReportingMock, 'pluginsLoaded', list => {
  assert.isTrue(Array.isArray(list));
});
sinon.stub(grReportingMock, 'getTimer', name => {
  assert.equal(typeof(name), 'string');
});
sinon.stub(grReportingMock, 'appStarted');
sinon.stub(grReportingMock, 'dashboardDisplayed');
sinon.stub(grReportingMock, 'changeDisplayed');
sinon.stub(grReportingMock, 'changeFullyLoaded');
sinon.stub(grReportingMock, 'fileListDisplayed');
sinon.stub(grReportingMock, 'beforeLocationChanged');
sinon.stub(grReportingMock, 'diffViewContentDisplayed');
sinon.stub(grReportingMock, 'diffViewFullyLoaded');
sinon.stub(grReportingMock, 'diffViewDisplayed');
sinon.stub(grReportingMock, 'recordDraftInteraction');
sinon.stub(grReportingMock, 'reportErrorDialog', message => {
  assert.equal(typeof(message), 'string');
});
sinon.stub(grReportingMock, 'reportRpcTiming', (anonymizedUrl, elapsed) => {
  assert.equal(typeof(anonymizedUrl), 'string');
  assert.equal(typeof(elapsed), 'number');
});

export function setupReportingStub() {
  return grReportingMock;
}
