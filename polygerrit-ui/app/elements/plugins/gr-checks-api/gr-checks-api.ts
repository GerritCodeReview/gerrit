/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {PluginApi} from '../../../api/plugin';
import {
  CheckResult,
  CheckRun,
  ChecksApiConfig,
  ChecksPluginApi,
  ChecksProvider,
} from '../../../api/checks';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {PluginsModel} from '../../../models/plugins/plugins-model';

const DEFAULT_CONFIG: ChecksApiConfig = {
  fetchPollingIntervalSeconds: 60,
};

enum State {
  NOT_REGISTERED,
  REGISTERED,
}

/**
 * Plugin API for checks.
 *
 * This object is created/returned to plugins that want to provide check data.
 * Plugins normally just call register() once at startup and then wait for
 * fetch() being called on the provider interface.
 */
export class GrChecksApi implements ChecksPluginApi {
  private state = State.NOT_REGISTERED;

  constructor(
    private readonly reporting: ReportingService,
    private readonly pluginsModel: PluginsModel,
    readonly plugin: PluginApi
  ) {
    this.reporting.trackApi(this.plugin, 'checks', 'constructor');
  }

  announceUpdate() {
    this.reporting.trackApi(this.plugin, 'checks', 'announceUpdate');
    this.pluginsModel.checksAnnounce(this.plugin.getPluginName());
  }

  updateResult(run: CheckRun, result: CheckResult) {
    if (result.externalId === undefined) {
      throw new Error('ChecksApi.updateResult() was called without externalId');
    }
    this.pluginsModel.checksUpdate({
      pluginName: this.plugin.getPluginName(),
      run,
      result,
    });
  }

  register(provider: ChecksProvider, config?: ChecksApiConfig): void {
    this.reporting.trackApi(this.plugin, 'checks', 'register');
    if (this.state === State.REGISTERED)
      throw new Error('Only one provider can be registered per plugin.');
    this.state = State.REGISTERED;
    this.pluginsModel.checksRegister({
      pluginName: this.plugin.getPluginName(),
      provider,
      config: config ?? DEFAULT_CONFIG,
    });
  }
}
