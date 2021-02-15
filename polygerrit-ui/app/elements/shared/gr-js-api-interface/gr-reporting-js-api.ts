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

import {appContext} from '../../../services/app-context';
import {PluginApi} from '../../../api/plugin';
import {EventDetails, ReportingPluginApi} from '../../../api/reporting';

/**
 * Defines all methods that will be exported to plugin from reporting service.
 */
export class GrReportingJsApi implements ReportingPluginApi {
  private readonly reporting = appContext.reportingService;

  constructor(private readonly plugin: PluginApi) {}

  reportInteraction(eventName: string, details?: EventDetails) {
    this.reporting.reportInteraction(
      `${this.plugin.getPluginName()}-${eventName}`,
      details
    );
  }

  reportLifeCycle(eventName: string, details?: EventDetails) {
    this.reporting.reportLifeCycle(
      `${this.plugin.getPluginName()}-${eventName}`,
      details
    );
  }
}
