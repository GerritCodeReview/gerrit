/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {PluginApi} from '../../../api/plugin';
import {PluginsModel} from '../../../models/plugins/plugins-model';
import {
  ChangeUpdatesPluginApi,
  ChangeUpdatesProvider,
} from '../../../api/change-updates';

enum State {
  NOT_REGISTERED,
  REGISTERED,
}

/**
 * Plugin API for change updates.
 *
 * This object is returned to plugins that want to provide change updates data.
 * Plugins normally just call register() once at startup.
 */
export class GrChangeUpdatesApi implements ChangeUpdatesPluginApi {
  private state = State.NOT_REGISTERED;

  constructor(
    private readonly pluginsModel: PluginsModel,
    readonly plugin: PluginApi
  ) {}

  register(provider: ChangeUpdatesProvider): void {
    if (this.state === State.REGISTERED) {
      throw new Error('Only one provider can be registered per plugin.');
    }
    this.state = State.REGISTERED;
    this.pluginsModel.changeUpdatesRegister({
      pluginName: this.plugin.getPluginName(),
      provider,
    });
  }
}
