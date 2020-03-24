/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import {GrReportingProvider} from './gr-reporting.js';

const GrReportingMock = {
  reportInteraction: () => {},
  appStarted: () => {},
  dashboardDisplayed: () => {},
  changeDisplayed: () => {},
  changeFullyLoaded: () => {},
  fileListDisplayed: () => {},
  diffViewContentDisplayed: () => {},
  diffViewFullyLoaded: () => {},
  diffViewDisplayed: () => {},
  recordDraftInteraction: () => {},
  time: () => {},
  timeEnd: () => {},
  pluginLoaded: () => {},
  pluginsLoaded: () => {},
  timeEndWithAverage: () => {},
  beforeLocationChanged: () => {},
  setRepoName: () => {},
  reportErrorDialog: () => {},
  reportRpcTiming: () => {},
  getTimer: () => {
    return {end: () => {}};
  },
};
sinon.stub(GrReportingMock, 'reportInteraction', (eventName, details) => {
  assert.equal(typeof(eventName), 'string');
  if (details) {
    assert.equal(typeof(details), 'object');
  }
});
sinon.stub(GrReportingMock, 'time', name => {
  assert.equal(typeof(name), 'string');
});
sinon.stub(GrReportingMock, 'timeEnd', name => {
  assert.equal(typeof(name), 'string');
});
sinon.stub(GrReportingMock, 'timeEndWithAverage',
    (name, averageName, denominator) => {
      assert.equal(typeof(name), 'string');
      assert.equal(typeof(averageName), 'string');
      assert.equal(typeof(denominator), 'number');
    });
sinon.stub(GrReportingMock, 'setRepoName', name => {
  assert.equal(typeof(name), 'string');
});
sinon.stub(GrReportingMock, 'pluginLoaded', name => {
  assert.equal(typeof(name), 'string');
});
sinon.stub(GrReportingMock, 'pluginsLoaded', list => {
  assert.isTrue(Array.isArray(list));
});
sinon.stub(GrReportingMock, 'getTimer', name => {
  assert.equal(typeof(name), 'string');
});
sinon.stub(GrReportingMock, 'appStarted');
sinon.stub(GrReportingMock, 'dashboardDisplayed');
sinon.stub(GrReportingMock, 'changeDisplayed');
sinon.stub(GrReportingMock, 'changeFullyLoaded');
sinon.stub(GrReportingMock, 'fileListDisplayed');
sinon.stub(GrReportingMock, 'beforeLocationChanged');
sinon.stub(GrReportingMock, 'diffViewContentDisplayed');
sinon.stub(GrReportingMock, 'diffViewFullyLoaded');
sinon.stub(GrReportingMock, 'diffViewDisplayed');
sinon.stub(GrReportingMock, 'recordDraftInteraction');
sinon.stub(GrReportingMock, 'reportErrorDialog', message => {
  assert.equal(typeof(message), 'string');
});
sinon.stub(GrReportingMock, 'reportRpcTiming', (anonymizedUrl, elapsed) => {
  assert.equal(typeof(anonymizedUrl), 'string');
  assert.equal(typeof(elapsed), 'number');
});

export function setupReportingMock() {
  sinon.stub(GrReportingProvider, 'getReportingInstance',
      () => GrReportingMock);
}
