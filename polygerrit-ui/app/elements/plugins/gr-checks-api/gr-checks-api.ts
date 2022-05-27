/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {PluginApi} from '../../../api/plugin';
import {
  ChecksApiConfig,
  ChecksProvider,
  ChecksPluginApi,
  CheckResult,
  CheckRun,
} from '../../../api/checks';
import {getAppContext} from '../../../services/app-context';

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

  private readonly reporting = getAppContext().reportingService;

  private readonly pluginsModel = getAppContext().pluginsModel;

  constructor(readonly plugin: PluginApi) {
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
