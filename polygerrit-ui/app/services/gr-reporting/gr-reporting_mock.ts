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
import {EventDetails, ReportingService, Timer} from './gr-reporting';

export class MockTimer implements Timer {
  end(): this {
    return this;
  }

  reset(): this {
    return this;
  }

  withMaximum(_: number): this {
    return this;
  }
}

const log = function (msg: string) {
  console.info(`ReportingMock.${msg}`);
};

export const grReportingMock: ReportingService = {
  appStarted: () => {},
  beforeLocationChanged: () => {},
  changeDisplayed: () => {},
  changeFullyLoaded: () => {},
  dashboardDisplayed: () => {},
  diffViewContentDisplayed: () => {},
  diffViewDisplayed: () => {},
  diffViewFullyLoaded: () => {},
  fileListDisplayed: () => {},
  getTimer: () => {
    return new MockTimer();
  },
  locationChanged: (page: string) => {
    log(`locationChanged: ${page}`);
  },
  onVisibilityChange: () => {
    log('onVisibilityChange');
  },
  pluginLoaded: () => {},
  pluginsLoaded: () => {},
  recordDraftInteraction: () => {},
  reporter: () => {},
  reportErrorDialog: (message: string) => {
    log(`reportErrorDialog: ${message}`);
  },
  error: () => {
    log('error');
  },
  reportExecution: (id: string, details: EventDetails) => {
    log(`reportExecution '${id}': ${JSON.stringify(details)}`);
  },
  reportExtension: () => {},
  reportInteraction: (eventName: string, details?: EventDetails) => {
    log(`reportInteraction '${eventName}': ${JSON.stringify(details)}`);
  },
  reportLifeCycle: () => {},
  reportRpcTiming: () => {},
  setRepoName: () => {},
  setChangeId: () => {},
  time: () => {},
  timeEnd: () => {},
  timeEndWithAverage: () => {},
};
