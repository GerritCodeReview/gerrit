/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {ReportingService, Timer} from './gr-reporting';
import {EventDetails} from '../../api/reporting';
import {PluginApi} from '../../api/plugin';
import {Execution, Interaction} from '../../constants/reporting';
import {Finalizable} from '../registry';

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

export const grReportingMock: ReportingService & Finalizable = {
  appStarted: () => {},
  beforeLocationChanged: () => {},
  changeDisplayed: () => {},
  changeFullyLoaded: () => {},
  dashboardDisplayed: () => {},
  diffViewContentDisplayed: () => {},
  diffViewDisplayed: () => {},
  fileListDisplayed: () => {},
  finalize: () => {},
  getTimer: () => new MockTimer(),
  locationChanged: (page: string) => {
    log(`locationChanged: ${page}`);
  },
  onVisibilityChange: () => {
    log('onVisibilityChange');
  },
  pluginLoaded: () => {},
  pluginsLoaded: () => {},
  pluginsFailed: () => {},
  reporter: () => {},
  reportErrorDialog: (message: string) => {
    log(`reportErrorDialog: ${message}`);
  },
  error: () => {
    log('error');
  },
  reportExecution: (_id: Execution, _details?: EventDetails) => {},
  trackApi: (_pluginApi: PluginApi, _object: string, _method: string) => {},
  reportExtension: () => {},
  reportInteraction: (
    _eventName: string | Interaction,
    _details?: EventDetails
  ) => {},
  reportLifeCycle: () => {},
  reportPluginLifeCycleLog: () => {},
  reportPluginInteractionLog: () => {},
  reportRpcTiming: () => {},
  setRepoName: () => {},
  setChangeId: () => {},
  time: () => {},
  timeEnd: () => {},
};
