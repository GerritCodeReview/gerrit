/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {PluginApi} from '../../../api/plugin';
import {EventDetails, ReportingPluginApi} from '../../../api/reporting';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';

/**
 * Defines all methods that will be exported to plugin from reporting service.
 */
export class GrReportingJsApi implements ReportingPluginApi {
  constructor(
    private readonly reporting: ReportingService,
    private readonly plugin: PluginApi
  ) {
    this.reporting.trackApi(this.plugin, 'reporting', 'constructor');
  }

  reportInteraction(eventName: string, details?: EventDetails) {
    this.reporting.trackApi(this.plugin, 'reporting', 'reportInteraction');
    this.reporting.reportPluginInteractionLog(
      `${this.plugin.getPluginName()}-${eventName}`,
      details
    );
  }

  reportLifeCycle(eventName: string, details?: EventDetails) {
    this.reporting.trackApi(this.plugin, 'reporting', 'reportLifeCycle');
    this.reporting.reportPluginLifeCycleLog(
      `${this.plugin.getPluginName()}-${eventName}`,
      details
    );
  }
}
